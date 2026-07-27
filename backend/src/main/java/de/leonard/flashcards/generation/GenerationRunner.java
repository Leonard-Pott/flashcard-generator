package de.leonard.flashcards.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import de.leonard.flashcards.config.AsyncConfig;
import de.leonard.flashcards.deck.Language;
import de.leonard.flashcards.document.Document;
import de.leonard.flashcards.document.DocumentRepository;
import de.leonard.flashcards.llm.LlmClient;
import de.leonard.flashcards.llm.LlmException;

/**
 * Arbeitet einen Generierungslauf ab: chunken, je Abschnitt das Modell fragen,
 * Antwort parsen, Duplikate aussortieren, Karten speichern, Fortschritt schreiben.
 *
 * <p>Ein einzelner Abschnitt darf scheitern - dann wird er uebersprungen und im Job
 * vermerkt. Abgebrochen wird nur, wenn gar nichts zu holen ist.
 */
@Component
public class GenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(GenerationRunner.class);

    private final GenerationJobStore store;
    private final DocumentRepository documentRepository;
    private final TextChunker chunker;
    private final CardPromptFactory promptFactory;
    private final CardJsonParser parser;
    private final CardDeduplicator deduplicator;
    private final LlmClient llmClient;

    public GenerationRunner(GenerationJobStore store, DocumentRepository documentRepository,
                            TextChunker chunker, CardPromptFactory promptFactory, CardJsonParser parser,
                            CardDeduplicator deduplicator, LlmClient llmClient) {
        this.store = store;
        this.documentRepository = documentRepository;
        this.chunker = chunker;
        this.promptFactory = promptFactory;
        this.parser = parser;
        this.deduplicator = deduplicator;
        this.llmClient = llmClient;
    }

    @Async(AsyncConfig.GENERATION_EXECUTOR)
    public void run(UUID jobId) {
        try {
            process(jobId);
        } catch (RuntimeException e) {
            log.error("Generierungslauf {} abgebrochen", jobId, e);
            store.fail(jobId, e.getMessage());
        }
    }

    private void process(UUID jobId) {
        JobSettings settings = readSettings(jobId);
        List<Chunk> chunks = collectChunks(settings.documentIds());

        if (chunks.isEmpty()) {
            store.fail(jobId, "In den gewaehlten Dokumenten war zu wenig Text fuer auch nur einen Abschnitt.");
            return;
        }

        store.markRunning(jobId, chunks.size(), llmClient.modelName());

        CardDeduplicator.Session dedup = deduplicator.newSession(store.existingFronts(settings.deckId()));
        String systemPrompt = promptFactory.systemPrompt(
                settings.language(), settings.style(), settings.cardsPerChunk());
        List<String> problems = new ArrayList<>();

        for (Chunk chunk : chunks) {
            try {
                List<GeneratedCard> cards = generateFor(chunk, systemPrompt);
                List<GeneratedCard> fresh = cards.stream().filter(dedup::isNew).toList();

                int saved = fresh.isEmpty()
                        ? 0
                        : store.saveCards(settings.deckId(), fresh, chunk.documentId(), chunk.text());
                store.recordProcessedChunk(jobId, saved);

                log.debug("Job {}: Abschnitt {} lieferte {} Karten ({} nach Deduplizierung)",
                        jobId, chunk.index(), cards.size(), saved);
            } catch (LlmException | CardParseException e) {
                // Genau der Fall, fuer den der Plan das Ueberspringen vorsieht.
                log.warn("Job {}: Abschnitt {} uebersprungen: {}", jobId, chunk.index(), e.getMessage());
                store.recordSkippedChunk(jobId);
                problems.add("Abschnitt %d: %s".formatted(chunk.index() + 1, e.getMessage()));
            }
        }

        finish(jobId, chunks.size(), problems);
    }

    private void finish(UUID jobId, int totalChunks, List<String> problems) {
        if (problems.size() == totalChunks) {
            store.fail(jobId, "Kein einziger Abschnitt konnte verarbeitet werden. "
                    + firstProblems(problems));
            return;
        }
        String note = problems.isEmpty()
                ? null
                : "%d von %d Abschnitten uebersprungen. %s".formatted(
                        problems.size(), totalChunks, firstProblems(problems));
        store.complete(jobId, note);
    }

    /** Nur die ersten Meldungen aufnehmen; bei vielen Fehlern sind sie ohnehin gleichlautend. */
    private String firstProblems(List<String> problems) {
        List<String> shown = problems.size() <= 3 ? problems : problems.subList(0, 3);
        String text = String.join(" | ", shown);
        return problems.size() > shown.size() ? text + " | … (" + problems.size() + " insgesamt)" : text;
    }

    private List<GeneratedCard> generateFor(Chunk chunk, String systemPrompt) {
        String answer = llmClient.complete(systemPrompt, promptFactory.userPrompt(chunk.text()));
        return parser.parse(answer);
    }

    private JobSettings readSettings(UUID jobId) {
        // store.load oeffnet seine eigene Transaktion; die Dokument-IDs haengen eager daran.
        GenerationJob job = store.load(jobId);
        return new JobSettings(
                job.getDocumentIds(),
                job.getTargetDeckId(),
                job.getLanguage(),
                job.getStyle(),
                job.getCardsPerChunk());
    }

    private List<Chunk> collectChunks(List<UUID> documentIds) {
        List<Chunk> chunks = new ArrayList<>();
        for (UUID documentId : documentIds) {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document == null || document.getExtractedText() == null) {
                log.warn("Dokument {} ist verschwunden oder ohne Text, wird uebergangen", documentId);
                continue;
            }
            for (String text : chunker.chunk(document.getExtractedText())) {
                chunks.add(new Chunk(documentId, text, chunks.size()));
            }
        }
        return chunks;
    }

    record JobSettings(List<UUID> documentIds, UUID deckId, Language language,
                       CardStyle style, int cardsPerChunk) {
    }

    record Chunk(UUID documentId, String text, int index) {
    }
}
