package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckRepository;
import de.leonard.flashcards.deck.Language;

/**
 * Prueft die Faelligkeitsabfragen gegen ein echtes Postgres - Zeitvergleiche sind genau
 * die Stelle, an der ein Mock zu wenig aussagt. Benoetigt ein laufendes Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StudyPersistenceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = LocalDate.of(2026, 7, 27).atTime(21, 30).atZone(ZONE).toInstant();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    private Deck deck;

    @BeforeEach
    void setUp() {
        cardRepository.deleteAll();
        deckRepository.deleteAll();
        deck = deckRepository.save(new Deck("ITIL", null, Language.DE));
    }

    @Test
    void findsTheOldestDueCardFirst() {
        cardDueAt("Uebermorgen faellig", NOW.plus(Duration.ofDays(2)));
        cardDueAt("Gestern faellig", NOW.minus(Duration.ofDays(1)));
        cardDueAt("Jetzt faellig", NOW);

        Card next = cardRepository
                .findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(deck.getId(), NOW)
                .orElseThrow();

        assertThat(next.getFront()).isEqualTo("Gestern faellig");
    }

    @Test
    void countsOnlyCardsDueNowOrEarlier() {
        cardDueAt("Gestern", NOW.minus(Duration.ofDays(1)));
        cardDueAt("Jetzt", NOW);
        cardDueAt("Morgen", NOW.plus(Duration.ofDays(1)));

        assertThat(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), NOW)).isEqualTo(2);
    }

    /** Der Kern des Ganzen: ein Lernschritt liegt Minuten entfernt, nicht Tage. */
    @Test
    void separatesCardsThatAreMinutesApart() {
        cardDueAt("In einer Minute", NOW.plus(Duration.ofMinutes(1)));
        cardDueAt("In zehn Minuten", NOW.plus(Duration.ofMinutes(10)));

        assertThat(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), NOW)).isZero();

        Instant later = NOW.plus(Duration.ofMinutes(5));
        assertThat(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), later)).isEqualTo(1);
        assertThat(cardRepository
                .findFirstByDeckIdAndDueAtGreaterThanOrderByDueAtAsc(deck.getId(), later)
                .orElseThrow()
                .getFront()).isEqualTo("In zehn Minuten");
    }

    @Test
    void findsTheNextFutureDueDate() {
        cardDueAt("In zehn Tagen", NOW.plus(Duration.ofDays(10)));
        cardDueAt("In drei Tagen", NOW.plus(Duration.ofDays(3)));

        Card next = cardRepository
                .findFirstByDeckIdAndDueAtGreaterThanOrderByDueAtAsc(deck.getId(), NOW)
                .orElseThrow();

        assertThat(next.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    void newCardsAreDueImmediately() {
        cardRepository.saveAndFlush(new Card(deck, "Neue Karte", "Antwort"));

        assertThat(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), Instant.now()))
                .isEqualTo(1);
    }

    @Test
    void doesNotLookIntoOtherDecks() {
        Deck other = deckRepository.save(new Deck("Anderer Stapel", null, Language.DE));
        Card foreign = new Card(other, "Fremde Karte", "Antwort");
        foreign.recordReview(2.5, 0, 0, 0, NOW.minus(Duration.ofDays(1)));
        cardRepository.saveAndFlush(foreign);

        assertThat(cardRepository
                .findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(deck.getId(), NOW))
                .isEmpty();
    }

    @Test
    void storesTheReviewResult() {
        Card card = cardRepository.saveAndFlush(new Card(deck, "Frage", "Antwort"));
        Instant due = NOW.plus(Duration.ofDays(15));
        card.recordReview(2.36, 15, 3, Sm2.GRADUATED, due);
        cardRepository.saveAndFlush(card);

        Card reloaded = cardRepository.findById(card.getId()).orElseThrow();

        assertThat(reloaded.getEaseFactor()).isEqualTo(2.36);
        assertThat(reloaded.getIntervalDays()).isEqualTo(15);
        assertThat(reloaded.getRepetitions()).isEqualTo(3);
        assertThat(reloaded.getLearningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(reloaded.getDueAt()).isEqualTo(due);
    }

    private void cardDueAt(String front, Instant dueAt) {
        Card card = new Card(deck, front, "Antwort");
        card.recordReview(Card.INITIAL_EASE_FACTOR, 0, 0, 0, dueAt);
        cardRepository.saveAndFlush(card);
    }
}
