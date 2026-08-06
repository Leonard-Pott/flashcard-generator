package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.CardService;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckNotFoundException;
import de.leonard.flashcards.deck.DeckService;
import de.leonard.flashcards.deck.Language;

@ExtendWith(MockitoExtension.class)
class StudyServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);
    private static final Instant NOW = TODAY.atTime(21, 30).atZone(ZONE).toInstant();

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardService cardService;

    @Mock
    private DeckService deckService;

    private StudyService service;
    private Deck deck;

    @BeforeEach
    void setUp() {
        service = new StudyService(
                cardRepository, cardService, deckService, new Sm2(), Clock.fixed(NOW, ZONE));

        deck = new Deck("ITIL", null, Language.DE);
        lenient().when(deckService.findById(deck.getId())).thenReturn(deck);
    }

    private static Instant startOfDayIn(int days) {
        return TODAY.plusDays(days).atStartOfDay(ZONE).toInstant();
    }

    @Test
    void returnsTheDueCardAndTheRemainingCount() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardRepository.findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(
                deck.getId(), NOW)).willReturn(Optional.of(card));
        given(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), NOW)).willReturn(3L);

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isEqualTo(card);
        assertThat(next.dueCount()).isEqualTo(3);
        assertThat(next.nextDueAt()).isNull();
    }

    @Test
    void reportsWhenTheNextCardIsDueIfNothingIsDueRightNow() {
        Card future = new Card(deck, "Frage", "Antwort");
        future.recordReview(2.5, 6, 2, Sm2.GRADUATED, startOfDayIn(4));
        given(cardRepository.findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(
                deck.getId(), NOW)).willReturn(Optional.empty());
        given(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), NOW)).willReturn(0L);
        given(cardRepository.findFirstByDeckIdAndDueAtGreaterThanOrderByDueAtAsc(deck.getId(), NOW))
                .willReturn(Optional.of(future));

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isNull();
        assertThat(next.dueCount()).isZero();
        assertThat(next.nextDueAt()).isEqualTo(startOfDayIn(4));
    }

    @Test
    void emptyDeckHasNeitherCardNorNextDate() {
        given(cardRepository.findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(
                deck.getId(), NOW)).willReturn(Optional.empty());
        given(cardRepository.countByDeckIdAndDueAtLessThanEqual(deck.getId(), NOW)).willReturn(0L);
        given(cardRepository.findFirstByDeckIdAndDueAtGreaterThanOrderByDueAtAsc(deck.getId(), NOW))
                .willReturn(Optional.empty());

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isNull();
        assertThat(next.nextDueAt()).isNull();
    }

    @Test
    void failsForUnknownDeck() {
        UUID unknown = UUID.randomUUID();
        given(deckService.findById(unknown)).willThrow(new DeckNotFoundException(unknown));

        assertThatThrownBy(() -> service.nextDue(unknown)).isInstanceOf(DeckNotFoundException.class);
    }

    @Test
    void reviewOfANewCardKeepsItInTheLearningStepsWhenItWasMissed() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 0);

        assertThat(updated.getLearningStep()).isZero();
        assertThat(updated.getRepetitions()).isZero();
        assertThat(updated.getDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void graduatedCardGetsADayInterval() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 4);

        assertThat(updated.getRepetitions()).isEqualTo(1);
        assertThat(updated.getIntervalDays()).isEqualTo(1);
        assertThat(updated.getLearningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(updated.getDueAt()).isEqualTo(startOfDayIn(1));
        assertThat(updated.getEaseFactor()).isEqualTo(2.5);
    }

    @Test
    void theDecksExamDateCapsTheInterval() {
        deck.examOn(TODAY.plusDays(10));
        Card card = new Card(deck, "Frage", "Antwort");
        card.recordReview(2.5, 6, 2, Sm2.GRADUATED, NOW);
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 4);

        // Ohne Termin waeren es 15 Tage - die Karte kaeme erst nach der Pruefung wieder.
        assertThat(updated.getIntervalDays()).isEqualTo(5);
        assertThat(updated.getDueAt()).isEqualTo(startOfDayIn(5));
    }

    @Test
    void reviewWithFailedAnswerBringsTheCardBackInTenMinutes() {
        Card card = new Card(deck, "Frage", "Antwort");
        card.recordReview(2.5, 30, 5, Sm2.GRADUATED, NOW);
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 1);

        assertThat(updated.getRepetitions()).isEqualTo(5);
        // 30 Tage * 0.3, nicht zurueck auf Tag eins.
        assertThat(updated.getIntervalDays()).isEqualTo(9);
        assertThat(updated.getLearningStep()).isZero();
        assertThat(updated.getDueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void reviewRejectsQualityOutsideTheRange() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardService.findById(card.getId())).willReturn(card);

        assertThatThrownBy(() -> service.review(card.getId(), 9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
