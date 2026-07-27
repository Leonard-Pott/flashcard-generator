package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void shortTextStaysOneChunk() {
        String text = "Ein kurzer Absatz. ".repeat(20);

        assertThat(chunker.chunk(text)).hasSize(1);
    }

    @Test
    void dropsTextBelowTheMinimumSize() {
        // Deckblaetter und Seitenzahlen sollen gar nicht erst ans Modell gehen.
        assertThat(chunker.chunk("Titelblatt")).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void splitsLongTextIntoSeveralChunks() {
        String text = "Satz ueber Service Management. ".repeat(1_000); // rund 31.000 Zeichen

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(4);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(TextChunker.TARGET_SIZE));
    }

    @Test
    void prefersParagraphBoundaries() {
        String paragraph = "Absatz mit Inhalt. ".repeat(60); // rund 1.140 Zeichen
        String text = (paragraph + "\n\n").repeat(12);

        List<String> chunks = chunker.chunk(text);

        // Kein Abschnitt beginnt oder endet mitten im Satz.
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).startsWith("Absatz");
            assertThat(chunk.strip()).endsWith(".");
        });
    }

    @Test
    void consecutiveChunksOverlap() {
        String text = numberedSentences(2_000);

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        String endOfFirst = chunks.getFirst();
        String startOfSecond = chunks.get(1);
        // Der Anfang des zweiten Abschnitts kommt im ersten noch vor.
        String probe = startOfSecond.substring(0, 40);
        assertThat(endOfFirst).contains(probe);
    }

    @Test
    void coversTheWholeTextWithoutGaps() {
        String text = numberedSentences(1_500);

        List<String> chunks = chunker.chunk(text);

        // Jeder Satz muss in mindestens einem Abschnitt auftauchen.
        for (int i = 0; i < 1_500; i += 100) {
            String sentence = "Satz Nummer " + i + ".";
            assertThat(chunks).as("Satz %d fehlt", i).anySatisfy(chunk ->
                    assertThat(chunk).contains(sentence));
        }
    }

    @Test
    void terminatesOnTextWithoutAnyBoundaries() {
        // Ein einziges "Wort" ohne Punkt und ohne Absatz - der Zerleger darf nicht haengen.
        String text = "a".repeat(20_000);

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).isNotEmpty();
        assertThat(String.join("", chunks)).contains("aaa");
    }

    private String numberedSentences(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("Satz Nummer ").append(i).append(". ");
        }
        return text.toString();
    }
}
