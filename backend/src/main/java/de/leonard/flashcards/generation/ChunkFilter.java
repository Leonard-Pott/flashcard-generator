package de.leonard.flashcards.generation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Sortiert Abschnitte aus, die erkennbar keinen Lernstoff enthalten - Deckblaetter,
 * Inhaltsverzeichnisse, Seitenzahlenwueste, Quellenlisten.
 *
 * <p>Der Sinn ist nicht Qualitaet, sondern Sparsamkeit: Jeder Abschnitt kostet eine
 * Anfrage vom knappen Tageskontingent. Ein Abschnitt ohne Substanz liefert ohnehin nur
 * ein leeres Array zurueck - dann kann man ihn auch gleich hierbehalten.
 *
 * <p>Im Zweifel wird durchgelassen. Ein faelschlich verworfener Abschnitt kostet
 * Lernstoff, ein faelschlich geschickter nur eine Anfrage.
 */
@Component
public class ChunkFilter {

    /** Unter diesem Anteil an Buchstaben ist es eine Zahlen- oder Symbolwueste. */
    private static final double MINIMUM_LETTER_RATIO = 0.5;

    /**
     * Weniger Woerter ergeben keine sinnvolle Karte. Bewusst niedrig: Eine Folie mit
     * fuenf gehaltvollen Stichpunkten kommt auf keine 40 Woerter und ist trotzdem Stoff.
     */
    private static final int MINIMUM_WORDS = 25;

    /**
     * Wiederholt sich derselbe Wortschatz staendig (Kopf- und Fusszeilen, die die
     * Extraktion je Seite mitgenommen hat), steckt kaum Inhalt dahinter. Als absolute
     * Untergrenze statt als Anteil: Bei langen Abschnitten faellt der Anteil
     * unterschiedlicher Woerter natuerlicherweise, ohne dass der Inhalt duenner wird.
     */
    private static final int MINIMUM_DISTINCT_WORDS = 20;

    public Verdict judge(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Verdict.rejected("leer");
        }

        long letters = chunk.chars().filter(Character::isLetter).count();
        double letterRatio = (double) letters / chunk.length();
        if (letterRatio < MINIMUM_LETTER_RATIO) {
            return Verdict.rejected("ueberwiegend Zahlen oder Symbole (%.0f%% Buchstaben)"
                    .formatted(letterRatio * 100));
        }

        String[] words = chunk.toLowerCase().split("[^\\p{L}\\p{N}]+");
        long wordCount = Arrays.stream(words).filter(word -> !word.isBlank()).count();
        if (wordCount < MINIMUM_WORDS) {
            return Verdict.rejected("nur %d Woerter".formatted(wordCount));
        }

        Set<String> distinct = Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
        if (distinct.size() < MINIMUM_DISTINCT_WORDS) {
            return Verdict.rejected("kaum unterschiedliche Woerter (%d)".formatted(distinct.size()));
        }

        return Verdict.accepted();
    }

    public record Verdict(boolean usable, String reason) {

        static Verdict accepted() {
            return new Verdict(true, null);
        }

        static Verdict rejected(String reason) {
            return new Verdict(false, reason);
        }
    }
}
