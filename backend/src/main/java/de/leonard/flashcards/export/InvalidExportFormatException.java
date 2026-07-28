package de.leonard.flashcards.export;

public class InvalidExportFormatException extends RuntimeException {

    public InvalidExportFormatException(String message) {
        super(message);
    }
}
