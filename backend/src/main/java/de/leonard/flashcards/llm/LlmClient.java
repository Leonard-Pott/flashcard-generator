package de.leonard.flashcards.llm;

/**
 * Schmale Sicht auf das Sprachmodell. Der uebrige Code kennt nur dieses Interface,
 * nicht die HTTP-Details, das Rate-Limiting oder die Retry-Logik der Implementierung.
 */
public interface LlmClient {

    /**
     * Schickt System- und User-Prompt an das Modell und gibt die reine Textantwort zurueck.
     *
     * @throws LlmException wenn die Antwort auch nach allen Wiederholungen ausbleibt
     *                      oder unbrauchbar ist
     */
    String complete(String systemPrompt, String userPrompt);

    /** Bezeichnung des verwendeten Modells, wird am Generierungsjob protokolliert. */
    String modelName();
}
