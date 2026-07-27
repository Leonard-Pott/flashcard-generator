package de.leonard.flashcards.deck;

import java.util.UUID;

public class DeckNotFoundException extends RuntimeException {

    public DeckNotFoundException(UUID id) {
        super("Stapel nicht gefunden: " + id);
    }
}
