package de.leonard.flashcards.study;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

/**
 * SM-2 nach Abschnitt 10 des Projektplans. Bewusst frei von Datenbank und Entitaeten:
 * eine reine Rechnung, die sich vollstaendig durchtesten laesst.
 */
@Component
public class Sm2 {

    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 5;

    /** Ab dieser Bewertung gilt die Antwort als gewusst. */
    private static final int PASSING_QUALITY = 3;

    /** Unter diesen Wert faellt der Faktor nicht, sonst kaeme die Karte in Dauerschleife. */
    private static final double MINIMUM_EASE_FACTOR = 1.3;

    public ReviewOutcome apply(double easeFactor, int intervalDays, int repetitions,
                               int quality, LocalDate today) {
        if (quality < MIN_QUALITY || quality > MAX_QUALITY) {
            throw new IllegalArgumentException("Die Bewertung muss zwischen 0 und 5 liegen: " + quality);
        }

        int nextRepetitions;
        int nextInterval;

        if (quality < PASSING_QUALITY) {
            // Nicht gewusst: die Karte faengt von vorn an und kommt morgen wieder.
            nextRepetitions = 0;
            nextInterval = 1;
        } else {
            nextRepetitions = repetitions + 1;
            nextInterval = switch (nextRepetitions) {
                case 1 -> 1;
                case 2 -> 6;
                default -> (int) Math.round(intervalDays * easeFactor);
            };
        }

        return new ReviewOutcome(
                nextEaseFactor(easeFactor, quality),
                nextInterval,
                nextRepetitions,
                today.plusDays(nextInterval));
    }

    /**
     * Der Faktor wird bei jeder Bewertung angepasst, auch bei einer schlechten - eine
     * wiederholt verfehlte Karte wird dadurch dauerhaft haeufiger abgefragt.
     */
    private double nextEaseFactor(double easeFactor, int quality) {
        int miss = MAX_QUALITY - quality;
        double updated = easeFactor + (0.1 - miss * (0.08 + miss * 0.02));
        return Math.max(MINIMUM_EASE_FACTOR, round(updated));
    }

    /**
     * Auf vier Nachkommastellen runden. Ohne das entstuenden aus der Fliesskommarechnung
     * Werte wie 1.7000000000000002, die sich ueber viele Wiederholungen fortschreiben.
     */
    private double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    public record ReviewOutcome(double easeFactor, int intervalDays, int repetitions, LocalDate dueDate) {
    }
}
