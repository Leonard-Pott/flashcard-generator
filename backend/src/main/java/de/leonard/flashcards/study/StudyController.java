package de.leonard.flashcards.study;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.leonard.flashcards.deck.CardDtos.CardResponse;
import de.leonard.flashcards.study.StudyDtos.NextCardResponse;
import de.leonard.flashcards.study.StudyDtos.ReviewRequest;

import jakarta.validation.Valid;

@RestController
public class StudyController {

    private final StudyService service;

    public StudyController(StudyService service) {
        this.service = service;
    }

    @GetMapping("/api/decks/{deckId}/study/next")
    public NextCardResponse next(@PathVariable UUID deckId) {
        StudyService.NextCard next = service.nextDue(deckId);
        return NextCardResponse.of(
                next.card() == null ? null : CardResponse.from(next.card()),
                next.dueCount(),
                next.nextDueAt());
    }

    @PostMapping("/api/cards/{cardId}/review")
    public CardResponse review(@PathVariable UUID cardId, @Valid @RequestBody ReviewRequest request) {
        return CardResponse.from(service.review(cardId, request.quality()));
    }
}
