package de.leonard.flashcards.deck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
            Language language,
            UUID folderId) {
    }

    public record MergeDecksRequest(
            @NotNull(message = "Es muss ein Quellstapel angegeben werden.")
            UUID sourceDeckId) {
    }

    /**
     * Alle Felder folgen derselben Regel: fehlt oder {@code null} = unveraendert,
     * leerer String = leeren, sonst setzen.
     *
     * <p>{@code examDate} und {@code folderId} sind deshalb Strings und keine
     * {@code Optional}. Jackson liefert fuer ein fehlendes {@code Optional}-Feld ein
     * {@code Optional.empty()} - "nicht mitgeschickt" und "ausdruecklich null" waeren
     * damit ununterscheidbar, und jedes Teil-PATCH haette den Termin geloescht.
     */
    public record UpdateDeckRequest(
            @Size(max = 200, message = "Der Name darf hoechstens 200 Zeichen lang sein.")
            String name,
            String description,
            Language language,
            String examDate,
            String folderId) {
    }

    /**
     * {@code dueToday} sind die heute noch offenen Karten (Ueberfaelliges eingeschlossen),
     * {@code nextDueAt} der naechste Termin nach jetzt - null, wenn nichts mehr aussteht.
     */
    public record DeckResponse(
            UUID id,
            String name,
            String description,
            Language language,
            LocalDate examDate,
            UUID folderId,
            long cardCount,
            long dueToday,
            Instant nextDueAt,
            Instant createdAt,
            Instant updatedAt) {

        /** Frisch angelegter Stapel - ohne Karten ist auch nichts faellig. */
        public static DeckResponse ofNew(Deck deck) {
            return new DeckResponse(
                    deck.getId(),
                    deck.getName(),
                    deck.getDescription(),
                    deck.getLanguage(),
                    deck.getExamDate(),
                    deck.getFolderId(),
                    0,
                    0,
                    null,
                    deck.getCreatedAt(),
                    deck.getUpdatedAt());
        }

        public static DeckResponse from(DeckRepository.DeckWithCardCount projection) {
            Deck deck = projection.getDeck();
            return new DeckResponse(
                    deck.getId(),
                    deck.getName(),
                    deck.getDescription(),
                    deck.getLanguage(),
                    deck.getExamDate(),
                    deck.getFolderId(),
                    projection.getCardCount(),
                    projection.getDueToday(),
                    projection.getNextDueAt(),
                    deck.getCreatedAt(),
                    deck.getUpdatedAt());
        }
    }
}
