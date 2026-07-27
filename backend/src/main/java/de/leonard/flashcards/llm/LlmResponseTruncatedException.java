package de.leonard.flashcards.llm;

/**
 * Das Modell hat mit {@code finishReason: MAX_TOKENS} abgebrochen. Die Antwort ist
 * abgeschnitten und das JSON darin unvollstaendig - ein Wiederholen mit demselben
 * Prompt aendert daran nichts, stattdessen muss der Chunk oder cardsPerChunk kleiner werden.
 */
public class LlmResponseTruncatedException extends LlmException {

    public LlmResponseTruncatedException(String message) {
        super(message);
    }
}
