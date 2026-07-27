package de.leonard.flashcards.generation;

public class InvalidGenerationRequestException extends RuntimeException {

    public InvalidGenerationRequestException(String message) {
        super(message);
    }
}
