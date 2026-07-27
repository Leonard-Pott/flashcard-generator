package de.leonard.flashcards.deck;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(UUID id) {
        super("Karte nicht gefunden: " + id);
    }
}
