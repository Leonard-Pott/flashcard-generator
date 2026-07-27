package de.leonard.flashcards.generation;

public enum CardStyle {
    /** Frage auf der Vorderseite, Antwort auf der Rueckseite. */
    QA,
    /** Lueckentext: ein Satz mit ausgelassenem Begriff, die Rueckseite nennt ihn. */
    CLOZE,
    /** Begriff auf der Vorderseite, Definition auf der Rueckseite. */
    DEFINITION
}
