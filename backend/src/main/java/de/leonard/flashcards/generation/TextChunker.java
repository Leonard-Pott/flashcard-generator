package de.leonard.flashcards.generation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Teilt den extrahierten Text in Abschnitte, die einzeln an das Modell gehen.
 * Getrennt wird bevorzugt an Absatzgrenzen, damit kein Satz mitten entzweigeht.
 */
@Component
public class TextChunker {

    static final int TARGET_SIZE = 6_000;
    static final int OVERLAP = 300;
    static final int MINIMUM_SIZE = 200;

    /**
     * Ein Absatzumbruch wird nur akzeptiert, wenn er im letzten Viertel des Abschnitts
     * liegt. Sonst entstuenden aus einem Text mit vielen kurzen Absaetzen lauter Winzlinge.
     */
    private static final double EARLIEST_BREAK = 0.75;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.strip();
        if (normalized.length() <= TARGET_SIZE) {
            return normalized.length() >= MINIMUM_SIZE ? List.of(normalized) : List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + TARGET_SIZE, normalized.length());
            if (end < normalized.length()) {
                end = breakAtParagraph(normalized, start, end);
            }

            String chunk = normalized.substring(start, end).strip();
            // Der letzte Abschnitt ist oft nur noch der Overlap-Rest oder eine Seitenzahl.
            if (chunk.length() >= MINIMUM_SIZE) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }
            start = nextStart(normalized, start, end);
        }
        return List.copyOf(chunks);
    }

    /**
     * Setzt um den Overlap zurueck, ruckt dann aber bis zur naechsten Absatz- oder
     * Satzgrenze vor. Sonst begaenne jeder Abschnitt ausser dem ersten mitten im Satz,
     * und das Modell muesste mit einem Satzfragment als Einstieg arbeiten.
     */
    private int nextStart(String text, int previousStart, int end) {
        // Immer mindestens ein Zeichen vorankommen, sonst laeuft die Schleife endlos.
        int windowStart = Math.max(previousStart + 1, end - OVERLAP);

        int paragraph = text.indexOf("\n\n", windowStart);
        if (paragraph >= 0 && paragraph + 2 < end) {
            return paragraph + 2;
        }
        int sentence = text.indexOf(". ", windowStart);
        if (sentence >= 0 && sentence + 2 < end) {
            return sentence + 2;
        }
        return windowStart;
    }

    private int breakAtParagraph(String text, int start, int end) {
        int earliest = start + (int) (TARGET_SIZE * EARLIEST_BREAK);
        int paragraph = text.lastIndexOf("\n\n", end);

        if (paragraph > earliest) {
            return paragraph;
        }
        // Kein brauchbarer Absatz: wenigstens am Satzende trennen.
        int sentence = text.lastIndexOf(". ", end);
        return sentence > earliest ? sentence + 1 : end;
    }
}
