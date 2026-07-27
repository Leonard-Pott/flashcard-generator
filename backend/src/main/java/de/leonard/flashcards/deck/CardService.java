package de.leonard.flashcards.deck;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.leonard.flashcards.deck.CardDtos.CreateCardRequest;
import de.leonard.flashcards.deck.CardDtos.UpdateCardRequest;

@Service
public class CardService {

    private static final int MAX_TAG_LENGTH = 64;

    private final CardRepository cardRepository;
    private final DeckService deckService;

    public CardService(CardRepository cardRepository, DeckService deckService) {
        this.cardRepository = cardRepository;
        this.deckService = deckService;
    }

    @Transactional(readOnly = true)
    public List<Card> findByDeck(UUID deckId) {
        // Wirft 404, wenn der Stapel nicht existiert - eine leere Liste waere hier irrefuehrend.
        deckService.findById(deckId);
        return cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deckId);
    }

    @Transactional
    public Card create(UUID deckId, CreateCardRequest request) {
        Deck deck = deckService.findById(deckId);

        Card card = new Card(deck, request.front().trim(), request.back().trim());
        card.setTags(normalizeTags(request.tags()));
        if (request.sourceDocumentId() != null || request.sourceExcerpt() != null) {
            card.setSource(request.sourceDocumentId(), request.sourceExcerpt());
        }
        return cardRepository.save(card);
    }

    @Transactional
    public Card update(UUID cardId, UpdateCardRequest request) {
        Card card = findById(cardId);

        if (request.front() != null) {
            card.setFront(requireText(request.front(), "Die Vorderseite darf nicht leer sein."));
        }
        if (request.back() != null) {
            card.setBack(requireText(request.back(), "Die Rueckseite darf nicht leer sein."));
        }
        if (request.tags() != null) {
            card.setTags(normalizeTags(request.tags()));
        }
        return card;
    }

    @Transactional(readOnly = true)
    public Card findById(UUID cardId) {
        return cardRepository.findById(cardId).orElseThrow(() -> new CardNotFoundException(cardId));
    }

    @Transactional
    public void delete(UUID cardId) {
        if (!cardRepository.existsById(cardId)) {
            throw new CardNotFoundException(cardId);
        }
        cardRepository.deleteById(cardId);
    }

    private String requireText(String value, String message) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidDeckException(message);
        }
        return trimmed;
    }

    /**
     * Leere Tags verwerfen, Rest trimmen und auf die Spaltenlaenge kuerzen. Sortiert,
     * damit die Antwort auf ein POST dieselbe Reihenfolge hat wie ein spaeteres GET -
     * die Karte laedt ihre Tags alphabetisch (siehe {@code @OrderBy} in {@link Card}).
     */
    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> normalized = new TreeSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed.length() > MAX_TAG_LENGTH ? trimmed.substring(0, MAX_TAG_LENGTH) : trimmed);
        }
        return normalized;
    }
}
