package de.leonard.flashcards.export;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.Deck;

/**
 * CSV fuer Excel: Semikolon als Trennzeichen und ein BOM am Anfang. Ohne BOM zeigt
 * Excel Umlaute als Kauderwelsch, weil es die Datei sonst als Windows-1252 liest.
 */
@Component
public class CsvDeckExportWriter implements DeckExportWriter {

    private static final char SEPARATOR = ';';
    private static final String LINE_BREAK = "\r\n";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public String contentType() {
        return "text/csv;charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "csv";
    }

    @Override
    public byte[] write(Deck deck, List<Card> cards) {
        StringBuilder out = new StringBuilder();
        out.append(row("Vorderseite", "Rueckseite", "Tags", "Faellig"));

        for (Card card : cards) {
            out.append(row(
                    card.getFront(),
                    card.getBack(),
                    String.join(", ", card.getTags()),
                    // Im Export interessiert nur der Tag, nicht die Uhrzeit des Lernschritts.
                    LocalDate.ofInstant(card.getDueAt(), ZoneId.systemDefault()).toString()));
        }

        byte[] body = out.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        return withBom;
    }

    private String row(String... fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(SEPARATOR);
            }
            row.append(escape(fields[i]));
        }
        return row.append(LINE_BREAK).toString();
    }

    /**
     * Felder mit Trennzeichen, Anfuehrungszeichen oder Zeilenumbruch werden nach
     * RFC 4180 in Anfuehrungszeichen gesetzt, innere Anfuehrungszeichen verdoppelt.
     * Zeilenumbrueche bleiben erhalten - Excel kommt damit innerhalb von Feldern zurecht.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;

        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
