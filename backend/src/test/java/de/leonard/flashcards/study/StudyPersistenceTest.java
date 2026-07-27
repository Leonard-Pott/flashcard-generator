package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
 * Prueft die Faelligkeitsabfragen gegen ein echtes Postgres - Datumsvergleiche sind
 * genau die Stelle, an der ein Mock zu wenig aussagt. Benoetigt ein laufendes Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StudyPersistenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);

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
        cardWithDueDate("Uebermorgen faellig", TODAY.plusDays(2));
        cardWithDueDate("Gestern faellig", TODAY.minusDays(1));
        cardWithDueDate("Heute faellig", TODAY);

        Card next = cardRepository
                .findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(deck.getId(), TODAY)
                .orElseThrow();

        assertThat(next.getFront()).isEqualTo("Gestern faellig");
    }

    @Test
    void countsOnlyCardsDueTodayOrEarlier() {
        cardWithDueDate("Gestern", TODAY.minusDays(1));
        cardWithDueDate("Heute", TODAY);
        cardWithDueDate("Morgen", TODAY.plusDays(1));

        assertThat(cardRepository.countByDeckIdAndDueDateLessThanEqual(deck.getId(), TODAY)).isEqualTo(2);
    }

    @Test
    void findsTheNextFutureDueDate() {
        cardWithDueDate("In zehn Tagen", TODAY.plusDays(10));
        cardWithDueDate("In drei Tagen", TODAY.plusDays(3));

        Card next = cardRepository
                .findFirstByDeckIdAndDueDateGreaterThanOrderByDueDateAsc(deck.getId(), TODAY)
                .orElseThrow();

        assertThat(next.getDueDate()).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    void newCardsAreDueImmediately() {
        cardRepository.saveAndFlush(new Card(deck, "Neue Karte", "Antwort"));

        assertThat(cardRepository.countByDeckIdAndDueDateLessThanEqual(deck.getId(), LocalDate.now()))
                .isEqualTo(1);
    }

    @Test
    void doesNotLookIntoOtherDecks() {
        Deck other = deckRepository.save(new Deck("Anderer Stapel", null, Language.DE));
        Card foreign = new Card(other, "Fremde Karte", "Antwort");
        foreign.recordReview(2.5, 0, 0, TODAY.minusDays(1));
        cardRepository.saveAndFlush(foreign);

        assertThat(cardRepository
                .findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(deck.getId(), TODAY))
                .isEmpty();
    }

    @Test
    void storesTheReviewResult() {
        Card card = cardRepository.saveAndFlush(new Card(deck, "Frage", "Antwort"));
        card.recordReview(2.36, 15, 3, TODAY.plusDays(15));
        cardRepository.saveAndFlush(card);

        Card reloaded = cardRepository.findById(card.getId()).orElseThrow();

        assertThat(reloaded.getEaseFactor()).isEqualTo(2.36);
        assertThat(reloaded.getIntervalDays()).isEqualTo(15);
        assertThat(reloaded.getRepetitions()).isEqualTo(3);
        assertThat(reloaded.getDueDate()).isEqualTo(TODAY.plusDays(15));
    }

    private void cardWithDueDate(String front, LocalDate dueDate) {
        Card card = new Card(deck, front, "Antwort");
        card.recordReview(Card.INITIAL_EASE_FACTOR, 0, 0, dueDate);
        cardRepository.saveAndFlush(card);
    }
}
