package de.leonard.flashcards.deck;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    private final FolderRepository folderRepository;
    private final Clock clock;

    public DeckService(DeckRepository deckRepository, CardRepository cardRepository,
                       FolderRepository folderRepository, Clock clock) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.folderRepository = folderRepository;
        this.clock = clock;
    }

    @Transactional
    public Deck create(CreateDeckRequest request) {
        Deck deck = new Deck(
                request.name().trim(),
                emptyToNull(request.description()),
                request.language() != null ? request.language() : DEFAULT_LANGUAGE);
        if (request.folderId() != null) {
            deck.moveTo(requireFolder(request.folderId()));
        }
        return deckRepository.save(deck);
    }

    @Transactional(readOnly = true)
    public List<DeckWithCardCount> findAll() {
        return deckRepository.findAllWithCardCount(clock.instant(), endOfDay());
    }

    @Transactional(readOnly = true)
    public DeckWithCardCount findWithCardCount(UUID id) {
        return deckRepository.findWithCardCount(id, clock.instant(), endOfDay())
                .orElseThrow(() -> new DeckNotFoundException(id));
    }

    /** Beginn des Folgetages: alles davor ist heute noch zu lernen. */
    private Instant endOfDay() {
        return LocalDate.now(clock).plusDays(1).atStartOfDay(clock.getZone()).toInstant();
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
        if (request.examDate() != null) {
            deck.examOn(parseExamDate(request.examDate()));
        }
        if (request.folderId() != null) {
            deck.moveTo(requireFolder(parseFolderId(request.folderId())));
        }
        return deck;
    }

    /**
     * Haengt alle Karten des Quellstapels an den Zielstapel um und loescht den leeren
     * Rest. Die Lernstaende der Karten bleiben unangetastet.
     */
    @Transactional
    public Deck merge(UUID targetId, UUID sourceId) {
        if (targetId.equals(sourceId)) {
            throw new InvalidDeckException("Ein Stapel kann nicht mit sich selbst zusammengefuehrt werden.");
        }
        Deck target = findById(targetId);
        // Wirft 404, wenn es den Quellstapel nicht gibt.
        findById(sourceId);

        cardRepository.moveAllToDeck(sourceId, target);
        deckRepository.deleteById(sourceId);
        return target;
    }

    /** Leerer String entfernt den Termin. */
    private LocalDate parseExamDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidDeckException("Kein gueltiges Datum: " + value);
        }
    }

    /** Leerer String nimmt den Stapel aus seinem Ordner heraus. */
    private UUID parseFolderId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidDeckException("Keine gueltige Ordner-Id: " + value);
        }
    }

    /** Null bleibt null - ein Stapel muss in keinem Ordner liegen. */
    private UUID requireFolder(UUID folderId) {
        if (folderId != null && !folderRepository.existsById(folderId)) {
            throw new FolderNotFoundException(folderId);
        }
        return folderId;
    }

    /** Loescht den Stapel samt Karten (ON DELETE CASCADE in V3). */
    @Transactional
    public void delete(UUID id) {
        if (!deckRepository.existsById(id)) {
            throw new DeckNotFoundException(id);
        }
        deckRepository.deleteById(id);
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
