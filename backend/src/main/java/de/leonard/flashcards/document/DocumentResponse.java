package de.leonard.flashcards.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadaten eines Dokuments. Der extrahierte Volltext bleibt bewusst aussen vor -
 * er wird nur serverseitig fuer die Generierung gebraucht und waere in einer Liste
 * schnell mehrere Megabyte gross.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String mimeType,
        long sizeBytes,
        Instant uploadedAt,
        int charCount,
        DocumentStatus status,
        String errorMessage) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getUploadedAt(),
                document.getCharCount(),
                document.getStatus(),
                document.getErrorMessage());
    }
}
