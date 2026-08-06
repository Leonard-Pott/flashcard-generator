package de.leonard.flashcards.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.Language;

class CsvDeckExportWriterTest {

    private static final Deck DECK = new Deck("ITIL", null, Language.DE);

    private final CsvDeckExportWriter writer = new CsvDeckExportWriter();

    @Test
    void startsWithTheUtf8Bom() {
        byte[] output = writer.write(DECK, List.of(card("Frage", "Antwort")));

        assertThat(output[0]).isEqualTo((byte) 0xEF);
        assertThat(output[1]).isEqualTo((byte) 0xBB);
        assertThat(output[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void writesHeaderAndSemicolonSeparatedRows() {
        String csv = text(writer.write(DECK, List.of(card("Was ist ein Incident?", "Eine Unterbrechung."))));

        assertThat(csv).startsWith("Vorderseite;Rueckseite;Tags;Faellig\r\n");
        assertThat(csv).contains("Was ist ein Incident?;Eine Unterbrechung.;;");
    }

    @Test
    void quotesFieldsContainingTheSeparator() {
        String csv = text(writer.write(DECK, List.of(card("Erst dies; dann das", "Antwort"))));

        assertThat(csv).contains("\"Erst dies; dann das\";Antwort");
    }

    @Test
    void doublesInnerQuotes() {
        String csv = text(writer.write(DECK, List.of(card("Was heisst \"Incident\"?", "Antwort"))));

        assertThat(csv).contains("\"Was heisst \"\"Incident\"\"?\";Antwort");
    }

    @Test
    void keepsLineBreaksInsideQuotedFields() {
        String csv = text(writer.write(DECK, List.of(card("Frage", "Erstens\nZweitens"))));

        assertThat(csv).contains("\"Erstens\nZweitens\"");
    }

    @Test
    void writesUmlautsAsUtf8() {
        byte[] output = writer.write(DECK, List.of(card("Verfügbarkeit", "Fähigkeit eines Service")));

        assertThat(text(output)).contains("Verfügbarkeit").contains("Fähigkeit");
    }

    @Test
    void joinsTagsWithCommas() {
        Card card = card("Frage", "Antwort");
        card.setTags(Set.of("ITIL", "Service Design"));

        // Ohne Anfuehrungszeichen: das Trennzeichen ist das Semikolon, ein Komma stoert nicht.
        assertThat(text(writer.write(DECK, List.of(card)))).contains(";ITIL, Service Design;");
    }

    @Test
    void writesHeaderOnlyForAnEmptyDeck() {
        String csv = text(writer.write(DECK, List.of()));

        assertThat(csv).isEqualTo("Vorderseite;Rueckseite;Tags;Faellig\r\n");
    }

    @Test
    void usesWindowsLineBreaks() {
        String csv = text(writer.write(DECK, List.of(card("A", "B"))));

        assertThat(csv.lines()).hasSize(2);
        assertThat(csv).endsWith("\r\n");
    }

    private Card card(String front, String back) {
        Card card = new Card(DECK, front, back);
        card.recordReview(2.5, 0, 0, 0,
                LocalDate.of(2026, 7, 28).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return card;
    }

    /** Ohne BOM lesen, damit die Vergleiche nicht darueber stolpern. */
    private String text(byte[] output) {
        return new String(output, 3, output.length - 3, StandardCharsets.UTF_8);
    }
}
