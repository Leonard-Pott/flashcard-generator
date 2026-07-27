package de.leonard.flashcards.deck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Anfrage- und Antwortformate fuer Karten.
 *
 * <p>Wie bei den Stapeln bedeutet {@code null} in den PATCH-Formaten "unveraendert".
 */
public final class CardDtos {

    private CardDtos() {
    }

    public record CreateCardRequest(
            @NotBlank(message = "Die Vorderseite darf nicht leer sein.")
            String front,
            @NotBlank(message = "Die Rueckseite darf nicht leer sein.")
            String back,
            Set<String> tags,
            UUID sourceDocumentId,
            String sourceExcerpt) {
    }

    public record UpdateCardRequest(String front, String back, Set<String> tags) {
    }

    public record CardResponse(
            UUID id,
            UUID deckId,
            String front,
            String back,
            Set<String> tags,
            UUID sourceDocumentId,
            String sourceExcerpt,
            double easeFactor,
            int intervalDays,
            int repetitions,
            LocalDate dueDate,
            Instant createdAt,
            Instant updatedAt) {

        public static CardResponse from(Card card) {
            return new CardResponse(
                    card.getId(),
                    card.getDeck().getId(),
                    card.getFront(),
                    card.getBack(),
                    new LinkedHashSet<>(card.getTags()),
                    card.getSourceDocumentId(),
                    card.getSourceExcerpt(),
                    card.getEaseFactor(),
                    card.getIntervalDays(),
                    card.getRepetitions(),
                    card.getDueDate(),
                    card.getCreatedAt(),
                    card.getUpdatedAt());
        }
    }
}
