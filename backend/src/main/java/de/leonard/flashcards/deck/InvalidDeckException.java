package de.leonard.flashcards.deck;

/** Fachlich unzulaessige Aenderung an einem Stapel oder einer Karte. */
public class InvalidDeckException extends RuntimeException {

    public InvalidDeckException(String message) {
        super(message);
    }
}
