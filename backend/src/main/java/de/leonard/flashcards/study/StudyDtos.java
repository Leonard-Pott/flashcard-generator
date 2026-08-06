package de.leonard.flashcards.study;

import java.time.Instant;

import de.leonard.flashcards.deck.CardDtos.CardResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class StudyDtos {

    private StudyDtos() {
    }

    public record ReviewRequest(
            @NotNull(message = "Es muss eine Bewertung angegeben werden.")
            @Min(value = 0, message = "Die Bewertung muss zwischen 0 und 5 liegen.")
            @Max(value = 5, message = "Die Bewertung muss zwischen 0 und 5 liegen.")
            Integer quality) {
    }

    /**
     * Naechste faellige Karte samt Kontext. {@code card} ist null, wenn gerade nichts
     * ansteht - dann sagt {@code nextDueAt}, wann es weitergeht. Das kann wenige Minuten
     * spaeter sein, wenn noch Karten in den Lernschritten haengen.
     */
    public record NextCardResponse(CardResponse card, long dueCount, Instant nextDueAt) {

        public static NextCardResponse of(CardResponse card, long dueCount, Instant nextDueAt) {
            return new NextCardResponse(card, dueCount, nextDueAt);
        }
    }
}
