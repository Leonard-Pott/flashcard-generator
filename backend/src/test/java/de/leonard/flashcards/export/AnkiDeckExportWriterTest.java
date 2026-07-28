package de.leonard.flashcards.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.Language;

class AnkiDeckExportWriterTest {

    private static final Deck DECK = new Deck("ITIL", null, Language.DE);

    private final AnkiDeckExportWriter writer = new AnkiDeckExportWriter();

    @Test
    void writesThreeTabSeparatedColumns() {
        String tsv = text(writer.write(DECK, List.of(new Card(DECK, "Frage", "Antwort"))));

        assertThat(tsv).isEqualTo("Frage\tAntwort\t\n");
    }

    @Test
    void writesNoHeaderRow() {
        // Anki wuerde eine Kopfzeile als erste Karte importieren.
        String tsv = text(writer.write(DECK, List.of(new Card(DECK, "Frage", "Antwort"))));

        assertThat(tsv).doesNotContain("Vorderseite");
    }

    @Test
    void writesNoBom() {
        byte[] output = writer.write(DECK, List.of(new Card(DECK, "Frage", "Antwort")));

        assertThat(output[0]).isNotEqualTo((byte) 0xEF);
    }

    @Test
    void turnsLineBreaksIntoHtml() {
        // Ein roher Umbruch wuerde die Zeile und damit den Import zerreissen.
        String tsv = text(writer.write(DECK, List.of(new Card(DECK, "Frage", "Erstens\nZweitens"))));

        assertThat(tsv).isEqualTo("Frage\tErstens<br>Zweitens\t\n");
        assertThat(tsv.lines()).hasSize(1);
    }

    @Test
    void replacesTabsInsideFields() {
        String tsv = text(writer.write(DECK, List.of(new Card(DECK, "Frage\tmit Tab", "Antwort"))));

        assertThat(tsv).isEqualTo("Frage mit Tab\tAntwort\t\n");
    }

    @Test
    void separatesTagsWithSpacesAndKeepsThemInOnePiece() {
        Card card = new Card(DECK, "Frage", "Antwort");
        card.setTags(Set.of("ITIL", "Service Design"));

        // Anki trennt Tags am Leerzeichen; "Service Design" waere sonst zwei Tags.
        assertThat(text(writer.write(DECK, List.of(card)))).isEqualTo("Frage\tAntwort\tITIL Service-Design\n");
    }

    @Test
    void writesOneLinePerCard() {
        String tsv = text(writer.write(DECK, List.of(
                new Card(DECK, "Erste", "A"),
                new Card(DECK, "Zweite", "B"))));

        assertThat(tsv.lines()).hasSize(2);
    }

    @Test
    void producesNothingForAnEmptyDeck() {
        assertThat(writer.write(DECK, List.of())).isEmpty();
    }

    @Test
    void writesUmlautsAsUtf8() {
        String tsv = text(writer.write(DECK, List.of(new Card(DECK, "Verfügbarkeit", "Fähigkeit"))));

        assertThat(tsv).contains("Verfügbarkeit").contains("Fähigkeit");
    }

    private String text(byte[] output) {
        return new String(output, StandardCharsets.UTF_8);
    }
}
