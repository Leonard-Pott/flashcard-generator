package de.leonard.flashcards.study;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.CardService;
import de.leonard.flashcards.deck.DeckService;

@Service
public class StudyService {

    private final CardRepository cardRepository;
    private final CardService cardService;
    private final DeckService deckService;
    private final Sm2 sm2;
    private final Clock clock;

    public StudyService(CardRepository cardRepository, CardService cardService, DeckService deckService,
                        Sm2 sm2, Clock clock) {
        this.cardRepository = cardRepository;
        this.cardService = cardService;
        this.deckService = deckService;
        this.sm2 = sm2;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NextCard nextDue(UUID deckId) {
        // Wirft 404, wenn es den Stapel nicht gibt.
        deckService.findById(deckId);
        LocalDate today = LocalDate.now(clock);

        Optional<Card> due = cardRepository
                .findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(deckId, today);
        long dueCount = cardRepository.countByDeckIdAndDueDateLessThanEqual(deckId, today);

        LocalDate nextDueDate = due.isPresent()
                ? null
                : cardRepository.findFirstByDeckIdAndDueDateGreaterThanOrderByDueDateAsc(deckId, today)
                        .map(Card::getDueDate)
                        .orElse(null);

        return new NextCard(due.orElse(null), dueCount, nextDueDate);
    }

    @Transactional
    public Card review(UUID cardId, int quality) {
        Card card = cardService.findById(cardId);

        Sm2.ReviewOutcome outcome = sm2.apply(
                card.getEaseFactor(),
                card.getIntervalDays(),
                card.getRepetitions(),
                quality,
                LocalDate.now(clock));

        card.recordReview(
                outcome.easeFactor(), outcome.intervalDays(), outcome.repetitions(), outcome.dueDate());
        return card;
    }

    public record NextCard(Card card, long dueCount, LocalDate nextDueDate) {
    }
}
