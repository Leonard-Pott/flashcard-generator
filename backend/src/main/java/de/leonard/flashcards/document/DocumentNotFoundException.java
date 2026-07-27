package de.leonard.flashcards.document;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Dokument nicht gefunden: " + id);
    }
}
