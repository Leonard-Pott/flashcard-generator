package de.leonard.flashcards.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(nullable = false, length = 512)
    private String filename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** Der von Tika extrahierte Volltext. TEXT statt der Default-Laenge von 255 Zeichen. */
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected Document() {
        // fuer JPA
    }

    public Document(String filename, String mimeType, long sizeBytes) {
        this.id = UUID.randomUUID();
        this.filename = filename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Instant.now();
        this.status = DocumentStatus.UPLOADED;
        this.charCount = 0;
    }

    public void markExtracted(String text) {
        this.extractedText = text;
        this.charCount = text == null ? 0 : text.length();
        this.status = DocumentStatus.EXTRACTED;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.extractedText = null;
        this.charCount = 0;
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public int getCharCount() {
        return charCount;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
