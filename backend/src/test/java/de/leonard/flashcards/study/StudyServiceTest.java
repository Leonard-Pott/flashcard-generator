package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
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

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);

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
        Clock fixedClock = Clock.fixed(
                TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new StudyService(cardRepository, cardService, deckService, new Sm2(), fixedClock);

        deck = new Deck("ITIL", null, Language.DE);
        lenient().when(deckService.findById(deck.getId())).thenReturn(deck);
    }

    @Test
    void returnsTheDueCardAndTheRemainingCount() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardRepository.findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(
                deck.getId(), TODAY)).willReturn(Optional.of(card));
        given(cardRepository.countByDeckIdAndDueDateLessThanEqual(deck.getId(), TODAY)).willReturn(3L);

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isEqualTo(card);
        assertThat(next.dueCount()).isEqualTo(3);
        assertThat(next.nextDueDate()).isNull();
    }

    @Test
    void reportsWhenTheNextCardIsDueIfNothingIsDueToday() {
        Card future = new Card(deck, "Frage", "Antwort");
        future.recordReview(2.5, 6, 2, TODAY.plusDays(4));
        given(cardRepository.findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(
                deck.getId(), TODAY)).willReturn(Optional.empty());
        given(cardRepository.countByDeckIdAndDueDateLessThanEqual(deck.getId(), TODAY)).willReturn(0L);
        given(cardRepository.findFirstByDeckIdAndDueDateGreaterThanOrderByDueDateAsc(deck.getId(), TODAY))
                .willReturn(Optional.of(future));

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isNull();
        assertThat(next.dueCount()).isZero();
        assertThat(next.nextDueDate()).isEqualTo(TODAY.plusDays(4));
    }

    @Test
    void emptyDeckHasNeitherCardNorNextDate() {
        given(cardRepository.findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(
                deck.getId(), TODAY)).willReturn(Optional.empty());
        given(cardRepository.countByDeckIdAndDueDateLessThanEqual(deck.getId(), TODAY)).willReturn(0L);
        given(cardRepository.findFirstByDeckIdAndDueDateGreaterThanOrderByDueDateAsc(deck.getId(), TODAY))
                .willReturn(Optional.empty());

        StudyService.NextCard next = service.nextDue(deck.getId());

        assertThat(next.card()).isNull();
        assertThat(next.nextDueDate()).isNull();
    }

    @Test
    void failsForUnknownDeck() {
        UUID unknown = UUID.randomUUID();
        given(deckService.findById(unknown)).willThrow(new DeckNotFoundException(unknown));

        assertThatThrownBy(() -> service.nextDue(unknown)).isInstanceOf(DeckNotFoundException.class);
    }

    @Test
    void reviewWritesTheSm2ResultToTheCard() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 4);

        assertThat(updated.getRepetitions()).isEqualTo(1);
        assertThat(updated.getIntervalDays()).isEqualTo(1);
        assertThat(updated.getDueDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(updated.getEaseFactor()).isEqualTo(2.5);
    }

    @Test
    void reviewWithFailedAnswerBringsTheCardBackTomorrow() {
        Card card = new Card(deck, "Frage", "Antwort");
        card.recordReview(2.5, 30, 5, TODAY);
        given(cardService.findById(card.getId())).willReturn(card);

        Card updated = service.review(card.getId(), 1);

        assertThat(updated.getRepetitions()).isZero();
        assertThat(updated.getIntervalDays()).isEqualTo(1);
        assertThat(updated.getDueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void reviewRejectsQualityOutsideTheRange() {
        Card card = new Card(deck, "Frage", "Antwort");
        given(cardService.findById(card.getId())).willReturn(card);

        assertThatThrownBy(() -> service.review(card.getId(), 9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
