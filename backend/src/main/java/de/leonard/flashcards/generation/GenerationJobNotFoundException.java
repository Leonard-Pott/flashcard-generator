package de.leonard.flashcards.generation;

import java.util.UUID;

public class GenerationJobNotFoundException extends RuntimeException {

    public GenerationJobNotFoundException(UUID id) {
        super("Generierungslauf nicht gefunden: " + id);
    }
}
