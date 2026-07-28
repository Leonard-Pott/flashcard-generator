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

    /** So viele verschiedene Ursachen kommen in die Job-Notiz, der Rest wird gezaehlt. */
    private static final int MAX_SHOWN_CAUSES = 2;

    private final GenerationJobStore store;
    private final DocumentRepository documentRepository;
    private final TextChunker chunker;
    private final ChunkFilter chunkFilter;
    private final CardPromptFactory promptFactory;
    private final CardJsonParser parser;
    private final CardDeduplicator deduplicator;
    private final LlmClient llmClient;

    public GenerationRunner(GenerationJobStore store, DocumentRepository documentRepository,
                            TextChunker chunker, ChunkFilter chunkFilter, CardPromptFactory promptFactory,
                            CardJsonParser parser, CardDeduplicator deduplicator, LlmClient llmClient) {
        this.store = store;
        this.documentRepository = documentRepository;
        this.chunker = chunker;
        this.chunkFilter = chunkFilter;
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
                problems.add(e.getMessage());
            }
        }

        finish(jobId, chunks.size(), problems);
    }

    private void finish(UUID jobId, int totalChunks, List<String> problems) {
        if (problems.size() == totalChunks) {
            store.fail(jobId, "Kein einziger Abschnitt konnte verarbeitet werden. "
                    + summarize(problems));
            return;
        }
        String note = problems.isEmpty()
                ? null
                : "%d von %d Abschnitten uebersprungen. %s".formatted(
                        problems.size(), totalChunks, summarize(problems));
        store.complete(jobId, note);
    }

    /**
     * Gleichlautende Ursachen zu einer Meldung zusammenfassen. Bricht ein Lauf am
     * Kontingent ab, scheitern alle folgenden Abschnitte am selben Grund - den elfmal
     * untereinander zu zeigen hilft niemandem.
     */
    private String summarize(List<String> problems) {
        List<String> distinct = problems.stream().distinct().toList();

        if (distinct.size() == 1) {
            return distinct.getFirst();
        }
        List<String> shown = distinct.size() <= MAX_SHOWN_CAUSES
                ? distinct
                : distinct.subList(0, MAX_SHOWN_CAUSES);
        String text = String.join(" | ", shown);
        return distinct.size() > shown.size()
                ? text + " | … und %d weitere Ursachen".formatted(distinct.size() - shown.size())
                : text;
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

    /**
     * Abschnitte einsammeln und die aussortieren, die keinen Lernstoff enthalten. Sie
     * gehen gar nicht erst in die Zaehlung ein - sonst wuerde der Fortschritt Anfragen
     * versprechen, die nie gestellt werden.
     */
    private List<Chunk> collectChunks(List<UUID> documentIds) {
        List<Chunk> chunks = new ArrayList<>();
        int discarded = 0;

        for (UUID documentId : documentIds) {
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document == null || document.getExtractedText() == null) {
                log.warn("Dokument {} ist verschwunden oder ohne Text, wird uebergangen", documentId);
                continue;
            }
            for (String text : chunker.chunk(document.getExtractedText())) {
                ChunkFilter.Verdict verdict = chunkFilter.judge(text);
                if (verdict.usable()) {
                    chunks.add(new Chunk(documentId, text, chunks.size()));
                } else {
                    discarded++;
                    log.debug("Abschnitt aus '{}' verworfen: {}", document.getFilename(), verdict.reason());
                }
            }
        }

        if (discarded > 0) {
            log.info("{} Abschnitte ohne Lernstoff aussortiert, das spart ebenso viele Anfragen", discarded);
        }
        return chunks;
    }

    record JobSettings(List<UUID> documentIds, UUID deckId, Language language,
                       CardStyle style, int cardsPerChunk) {
    }

    record Chunk(UUID documentId, String text, int index) {
    }
}
