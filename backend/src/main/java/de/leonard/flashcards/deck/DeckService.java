package de.leonard.flashcards.deck;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckDtos.UpdateDeckRequest;
import de.leonard.flashcards.deck.DeckRepository.DeckWithCardCount;

@Service
public class DeckService {

    /** Ohne Angabe wird deutsch angenommen - das ist der Normalfall dieser Anwendung. */
    private static final Language DEFAULT_LANGUAGE = Language.DE;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;

    public DeckService(DeckRepository deckRepository, CardRepository cardRepository) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional
    public Deck create(CreateDeckRequest request) {
        Deck deck = new Deck(
                request.name().trim(),
                emptyToNull(request.description()),
                request.language() != null ? request.language() : DEFAULT_LANGUAGE);
        return deckRepository.save(deck);
    }

    @Transactional(readOnly = true)
    public List<DeckWithCardCount> findAll() {
        return deckRepository.findAllWithCardCount();
    }

    @Transactional(readOnly = true)
    public DeckWithCardCount findWithCardCount(UUID id) {
        return deckRepository.findWithCardCount(id).orElseThrow(() -> new DeckNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Deck findById(UUID id) {
        return deckRepository.findById(id).orElseThrow(() -> new DeckNotFoundException(id));
    }

    @Transactional
    public Deck update(UUID id, UpdateDeckRequest request) {
        Deck deck = findById(id);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new InvalidDeckException("Der Name darf nicht leer sein.");
            }
            deck.rename(name);
        }
        if (request.description() != null) {
            deck.describe(emptyToNull(request.description()));
        }
        if (request.language() != null) {
            deck.useLanguage(request.language());
        }
        return deck;
    }

    /** Loescht den Stapel samt Karten (ON DELETE CASCADE in V3). */
    @Transactional
    public void delete(UUID id) {
        if (!deckRepository.existsById(id)) {
            throw new DeckNotFoundException(id);
        }
        deckRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long countCards(UUID deckId) {
        return cardRepository.countByDeckId(deckId);
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
