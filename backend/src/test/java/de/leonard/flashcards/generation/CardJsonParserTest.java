package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CardJsonParserTest {

    private final CardJsonParser parser = new CardJsonParser(new ObjectMapper());

    @Test
    void readsPlainJsonArray() {
        String json = """
                [{"front": "Was ist ein Incident?", "back": "Eine Unterbrechung.", "tags": ["ITIL"]}]""";

        List<GeneratedCard> cards = parser.parse(json);

        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().front()).isEqualTo("Was ist ein Incident?");
        assertThat(cards.getFirst().tags()).containsExactly("ITIL");
    }

    @Test
    void stripsMarkdownFences() {
        String answer = """
                ```json
                [{"front": "A", "back": "B"}]
                ```""";

        assertThat(parser.parse(answer)).hasSize(1);
    }

    @Test
    void stripsFencesWithoutLanguageHint() {
        assertThat(parser.parse("```\n[{\"front\": \"A\", \"back\": \"B\"}]\n```")).hasSize(1);
    }

    @Test
    void ignoresChatterAroundTheArray() {
        String answer = """
                Gerne! Hier sind die Karten:
                [{"front": "A", "back": "B"}]
                Ich hoffe, das hilft.""";

        assertThat(parser.parse(answer)).hasSize(1);
    }

    @Test
    void toleratesUnknownFields() {
        String json = """
                [{"front": "A", "back": "B", "difficulty": "leicht", "id": 7}]""";

        assertThat(parser.parse(json)).hasSize(1);
    }

    @Test
    void acceptsEmptyArrayForContentWithoutSubstance() {
        // Gliederungs- und Titelfolien sollen genau das liefern.
        assertThat(parser.parse("[]")).isEmpty();
    }

    @Test
    void missingTagsBecomeAnEmptyList() {
        List<GeneratedCard> cards = parser.parse("[{\"front\": \"A\", \"back\": \"B\"}]");

        assertThat(cards.getFirst().tags()).isEmpty();
    }

    @Test
    void dropsCardsWithoutFrontOrBack() {
        String json = """
                [{"front": "A", "back": "B"},
                 {"front": "", "back": "B"},
                 {"front": "C"},
                 {"back": "D"}]""";

        assertThat(parser.parse(json)).hasSize(1);
    }

    @Test
    void failsOnTruncatedJson() {
        assertThatThrownBy(() -> parser.parse("[{\"front\": \"A\", \"back\": \"unvollstae"))
                .isInstanceOf(CardParseException.class);
    }

    @Test
    void failsWhenThereIsNoArrayAtAll() {
        assertThatThrownBy(() -> parser.parse("Ich kann dazu keine Karten erstellen."))
                .isInstanceOf(CardParseException.class)
                .hasMessageContaining("kein JSON-Array");
    }

    @Test
    void failsOnEmptyAnswer() {
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(CardParseException.class);
    }
}
