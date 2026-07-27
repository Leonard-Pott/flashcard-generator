package de.leonard.flashcards.deck;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Anfrage- und Antwortformate fuer Stapel.
 *
 * <p>Bei den PATCH-Formaten bedeutet {@code null} "unveraendert lassen". Ein Feld
 * wird geleert, indem ein leerer String geschickt wird.
 */
public final class DeckDtos {

    private DeckDtos() {
    }

    public record CreateDeckRequest(
            @NotBlank(message = "Der Name darf nicht leer sein.")
            @Size(max = 200, message = "Der Name darf hoechstens 200 Zeichen lang sein.")
            String name,
            String description,
            Language language) {
    }

    public record UpdateDeckRequest(
            @Size(max = 200, message = "Der Name darf hoechstens 200 Zeichen lang sein.")
            String name,
            String description,
            Language language) {
    }

    public record DeckResponse(
            UUID id,
            String name,
            String description,
            Language language,
            long cardCount,
            Instant createdAt,
            Instant updatedAt) {

        public static DeckResponse from(Deck deck, long cardCount) {
            return new DeckResponse(
                    deck.getId(),
                    deck.getName(),
                    deck.getDescription(),
                    deck.getLanguage(),
                    cardCount,
                    deck.getCreatedAt(),
                    deck.getUpdatedAt());
        }

        public static DeckResponse from(DeckRepository.DeckWithCardCount projection) {
            return from(projection.getDeck(), projection.getCardCount());
        }
    }
}
