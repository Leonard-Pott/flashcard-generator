package de.leonard.flashcards.generation;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckNotFoundException;
import de.leonard.flashcards.deck.DeckRepository;

/**
 * Alle Datenbankzugriffe des Generierungslaufs, jeder in einer eigenen kurzen
 * Transaktion. Der Lauf selbst dauert Minuten - eine Transaktion darueber zu spannen
 * wuerde eine Verbindung blockieren und den Fortschritt erst am Ende sichtbar machen.
 */
@Service
public class GenerationJobStore {

    /** So viel Originaltext wird je Karte zur Nachvollziehbarkeit gespeichert. */
    private static final int EXCERPT_LENGTH = 500;

    private final GenerationJobRepository jobRepository;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;

    public GenerationJobStore(GenerationJobRepository jobRepository, DeckRepository deckRepository,
                              CardRepository cardRepository) {
        this.jobRepository = jobRepository;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public GenerationJob load(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new GenerationJobNotFoundException(jobId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID jobId, int totalChunks, String llmModel) {
        load(jobId).start(totalChunks, llmModel);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProcessedChunk(UUID jobId, int generatedCards) {
        load(jobId).recordProcessedChunk(generatedCards);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSkippedChunk(UUID jobId) {
        load(jobId).recordSkippedChunk();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, String note) {
        load(jobId).complete(note);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, String errorMessage) {
        load(jobId).fail(errorMessage);
    }

    /** Vorderseiten der Karten, die schon im Zielstapel liegen - Grundlage der Deduplizierung. */
    @Transactional(readOnly = true)
    public Set<String> existingFronts(UUID deckId) {
        return cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deckId).stream()
                .map(Card::getFront)
                .collect(Collectors.toSet());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveCards(UUID deckId, List<GeneratedCard> cards, UUID sourceDocumentId, String chunkText) {
        Deck deck = deckRepository.findById(deckId).orElseThrow(() -> new DeckNotFoundException(deckId));
        String excerpt = excerptOf(chunkText);

        for (GeneratedCard generated : cards) {
            Card card = new Card(deck, generated.front().strip(), generated.back().strip());
            card.setTags(generated.tags().stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::strip)
                    .collect(Collectors.toCollection(TreeSet::new)));
            card.setSource(sourceDocumentId, excerpt);
            cardRepository.save(card);
        }
        return cards.size();
    }

    private String excerptOf(String chunkText) {
        if (chunkText == null) {
            return null;
        }
        String stripped = chunkText.strip();
        return stripped.length() <= EXCERPT_LENGTH ? stripped : stripped.substring(0, EXCERPT_LENGTH) + " …";
    }
}
