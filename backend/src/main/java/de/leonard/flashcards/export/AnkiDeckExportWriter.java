package de.leonard.flashcards.export;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.Deck;

/**
 * Anki-kompatibles TSV: drei Spalten (Vorderseite, Rueckseite, Tags), keine Kopfzeile
 * und kein BOM - Anki wuerde beides als Inhalt der ersten Karte lesen.
 */
@Component
public class AnkiDeckExportWriter implements DeckExportWriter {

    private static final char SEPARATOR = '\t';
    private static final String LINE_BREAK = "\n";

    @Override
    public ExportFormat format() {
        return ExportFormat.ANKI;
    }

    @Override
    public String contentType() {
        return "text/tab-separated-values;charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "txt";
    }

    @Override
    public byte[] write(Deck deck, List<Card> cards) {
        StringBuilder out = new StringBuilder();

        for (Card card : cards) {
            out.append(field(card.getFront()))
                    .append(SEPARATOR)
                    .append(field(card.getBack()))
                    .append(SEPARATOR)
                    // Anki trennt Tags durch Leerzeichen; Leerzeichen im Tag selbst
                    // wuerden ihn zerreissen und werden deshalb ersetzt.
                    .append(card.getTags().stream()
                            .map(tag -> tag.replace(' ', '-'))
                            .reduce((a, b) -> a + " " + b)
                            .orElse(""))
                    .append(LINE_BREAK);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * TSV kennt keine Maskierung: Ein Tabulator oder Zeilenumbruch im Feld wuerde die
     * Spalten verschieben. Umbrueche werden zu HTML-Zeilenumbruechen, die Anki im
     * Feld korrekt darstellt, Tabulatoren zu Leerzeichen.
     */
    private String field(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>")
                .replace("\t", " ");
    }
}
