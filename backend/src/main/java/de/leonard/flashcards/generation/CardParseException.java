package de.leonard.flashcards.generation;

/** Die Modellantwort liess sich nicht in Karten uebersetzen; der Chunk wird uebersprungen. */
public class CardParseException extends RuntimeException {

    public CardParseException(String message) {
        super(message);
    }
}
