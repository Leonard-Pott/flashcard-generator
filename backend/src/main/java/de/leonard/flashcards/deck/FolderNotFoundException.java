package de.leonard.flashcards.deck;

import java.util.UUID;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(UUID id) {
        super("Ordner nicht gefunden: " + id);
    }
}
