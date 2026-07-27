package de.leonard.flashcards.generation;

public enum JobStatus {
    /** Angelegt, die Verarbeitung hat noch nicht begonnen. */
    PENDING,
    RUNNING,
    /** Durchgelaufen. Einzelne Abschnitte koennen trotzdem uebersprungen worden sein. */
    COMPLETED,
    FAILED
}
