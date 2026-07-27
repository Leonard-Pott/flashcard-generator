package de.leonard.flashcards.document;

public enum DocumentStatus {
    /** Hochgeladen, Textextraktion steht noch aus. */
    UPLOADED,
    /** Text erfolgreich extrahiert, das Dokument kann fuer die Generierung verwendet werden. */
    EXTRACTED,
    /** Extraktion fehlgeschlagen, Grund steht in errorMessage. */
    FAILED
}
