package de.leonard.flashcards.llm;

/**
 * Der Aufruf des Sprachmodells ist endgueltig gescheitert. Ab M5 wird der betroffene
 * Chunk uebersprungen und im Job vermerkt, statt den ganzen Lauf abzubrechen.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
