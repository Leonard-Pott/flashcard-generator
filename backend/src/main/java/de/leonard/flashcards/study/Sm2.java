package de.leonard.flashcards.study;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/**
 * SM-2 nach Abschnitt 10 des Projektplans, erweitert um Lernschritte nach Anki-Vorbild.
 * Bewusst frei von Datenbank und Entitaeten: eine reine Rechnung, die sich vollstaendig
 * durchtesten laesst.
 *
 * <p>SM-2 allein kennt nur ganze Tage. Eine neue Karte kaeme damit selbst nach "Nochmal"
 * fruehestens morgen wieder - fuer den Lernenden fuehlt sich das falsch an. Deshalb laeuft
 * eine Karte erst durch kurze Lernschritte innerhalb derselben Sitzung, bevor SM-2 den
 * Tagesrhythmus uebernimmt. Wer eine bereits gelernte Karte vergisst, faellt nicht auf
 * Null zurueck, sondern lernt sie am selben Tag nach und behaelt einen Rest des Intervalls.
 */
@Component
public class Sm2 {

    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 5;

    /** Ab dieser Bewertung gilt die Antwort als gewusst. */
    private static final int PASSING_QUALITY = 3;

    /** Unter diesen Wert faellt der Faktor nicht, sonst kaeme die Karte in Dauerschleife. */
    private static final double MINIMUM_EASE_FACTOR = 1.3;

    /** {@code learningStep} einer Karte, die die Lernschritte hinter sich hat. */
    public static final int GRADUATED = -1;

    /**
     * Schritte einer neuen Karte, bevor sie in den Tagesrhythmus wechselt. Bewusst nur
     * einer: eine Karte nach 60 Sekunden erneut zu fragen prueft das Arbeitsgedaechtnis,
     * nicht das Langzeitgedaechtnis - der Abstand muss gross genug sein, um zu wirken.
     */
    private static final Duration[] LEARNING_STEPS = {Duration.ofMinutes(10)};

    /** Schritte einer vergessenen Karte. Kurz, weil der Stoff schon einmal sass. */
    private static final Duration[] RELEARNING_STEPS = {Duration.ofMinutes(10)};

    /** Erstes Tagesintervall nach bestandenen Lernschritten. */
    private static final int GRADUATING_INTERVAL = 1;

    /** Wer die neue Karte sofort als einfach bewertet, ueberspringt die Lernschritte. */
    private static final int EASY_INTERVAL = 4;

    /** Anteil des alten Intervalls, der einen Patzer ueberlebt - also 70 Prozent Kuerzung. */
    private static final double LAPSE_RETENTION = 0.3;

    /**
     * @param examDate optionaler Pruefungstermin des Stapels; deckelt die Tagesintervalle.
     */
    public ReviewOutcome apply(State state, int quality, Instant now, ZoneId zone, LocalDate examDate) {
        if (quality < MIN_QUALITY || quality > MAX_QUALITY) {
            throw new IllegalArgumentException("Die Bewertung muss zwischen 0 und 5 liegen: " + quality);
        }

        return state.learningStep() == GRADUATED
                ? inDailyRhythm(state, quality, now, zone, examDate)
                : inLearningSteps(state, quality, now, zone, examDate);
    }

    /**
     * Karte steckt in den Lernschritten. Der Ease-Faktor bleibt hier unberuehrt: Wer eine
     * neue Karte dreimal nicht weiss, hat sie noch nicht gelernt - das ist keine Aussage
     * ueber ihre dauerhafte Schwierigkeit.
     */
    private ReviewOutcome inLearningSteps(State state, int quality, Instant now, ZoneId zone,
                                          LocalDate examDate) {
        // Nach einem Patzer zaehlt die Karte weiter Wiederholungen; sie lernt nur nach.
        Duration[] steps = state.repetitions() == 0 ? LEARNING_STEPS : RELEARNING_STEPS;

        int step;
        if (quality < PASSING_QUALITY) {
            step = 0;                            // Nochmal: zurueck auf den ersten Schritt.
        } else if (quality == PASSING_QUALITY) {
            step = state.learningStep();         // Schwer: denselben Schritt wiederholen.
        } else if (quality == MAX_QUALITY) {
            step = steps.length;                 // Einfach: Lernschritte ueberspringen.
        } else {
            step = state.learningStep() + 1;     // Gut: einen Schritt weiter.
        }

        if (step >= steps.length) {
            return graduate(state, quality, now, zone, examDate);
        }
        return new ReviewOutcome(
                state.easeFactor(), state.intervalDays(), state.repetitions(), step, now.plus(steps[step]));
    }

    /** Uebergang aus den Lernschritten in den Tagesrhythmus. */
    private ReviewOutcome graduate(State state, int quality, Instant now, ZoneId zone, LocalDate examDate) {
        int repetitions = state.repetitions() + 1;
        int intervalDays = state.repetitions() == 0
                // Neue Karte: der erste Termin haengt nur davon ab, wie leicht sie fiel.
                ? (quality == MAX_QUALITY ? EASY_INTERVAL : GRADUATING_INTERVAL)
                // Nachgelernte Karte: das gekuerzte Intervall steht seit dem Patzer fest.
                : Math.max(1, state.intervalDays());
        intervalDays = capBeforeExam(intervalDays, now, zone, examDate);

        return new ReviewOutcome(
                state.easeFactor(), intervalDays, repetitions, GRADUATED, startOfDay(now, zone, intervalDays));
    }

    /** Karte laeuft im Tagesrhythmus; hier rechnet das urspruengliche SM-2. */
    private ReviewOutcome inDailyRhythm(State state, int quality, Instant now, ZoneId zone,
                                        LocalDate examDate) {
        double easeFactor = nextEaseFactor(state.easeFactor(), quality);

        if (quality < PASSING_QUALITY) {
            // Patzer: Intervall stark kuerzen und die Karte noch heute nachlernen lassen.
            int shortened = Math.max(1, (int) Math.round(state.intervalDays() * LAPSE_RETENTION));
            return new ReviewOutcome(
                    easeFactor, shortened, state.repetitions(), 0, now.plus(RELEARNING_STEPS[0]));
        }

        int repetitions = state.repetitions() + 1;
        int intervalDays = repetitions == 2
                ? 6
                : Math.max(1, (int) Math.round(state.intervalDays() * state.easeFactor()));
        intervalDays = capBeforeExam(intervalDays, now, zone, examDate);

        return new ReviewOutcome(
                easeFactor, intervalDays, repetitions, GRADUATED, startOfDay(now, zone, intervalDays));
    }

    /**
     * Vor einem festen Termin ist SM-2 die falsche Zielgroesse: es spart Wiederholungen,
     * die man vor einer Pruefung gerade nicht sparen will - eine Karte mit 15 Tagen
     * Intervall saehe man bis zum Termin kein einziges Mal wieder.
     *
     * <p>Der Deckel auf die halbe Restzeit ergibt von selbst einen Taper: die Abstaende
     * wachsen erst und ziehen sich zum Termin hin zusammen. Karten, die man nicht kann,
     * bleiben trotzdem haeufiger dran - der Patzer-Pfad laeuft ueber Minuten und wird
     * vom Deckel nicht beruehrt.
     */
    private int capBeforeExam(int intervalDays, Instant now, ZoneId zone, LocalDate examDate) {
        if (examDate == null) {
            return intervalDays;
        }

        long remaining = ChronoUnit.DAYS.between(LocalDate.ofInstant(now, zone), examDate);
        if (remaining <= 0) {
            // Termin vorbei: der Stapel laeuft wieder nach dem normalen SM-2.
            return intervalDays;
        }
        return (int) Math.min(intervalDays, Math.max(1, remaining / 2));
    }

    /**
     * Tagesintervalle liegen auf dem Tagesbeginn, nicht auf der Uhrzeit der Bewertung.
     * Sonst waere eine abends gelernte Karte am naechsten Morgen noch nicht faellig.
     */
    private Instant startOfDay(Instant now, ZoneId zone, int days) {
        return LocalDate.ofInstant(now, zone).plusDays(days).atStartOfDay(zone).toInstant();
    }

    /**
     * Der Faktor wird bei jeder Bewertung im Tagesrhythmus angepasst, auch bei einer
     * schlechten - eine wiederholt verfehlte Karte wird dadurch dauerhaft haeufiger abgefragt.
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

    /** Lernstand einer Karte, so wie ihn die Rechnung braucht. */
    public record State(double easeFactor, int intervalDays, int repetitions, int learningStep) {
    }

    public record ReviewOutcome(double easeFactor, int intervalDays, int repetitions, int learningStep,
                                Instant dueAt) {
    }
}
