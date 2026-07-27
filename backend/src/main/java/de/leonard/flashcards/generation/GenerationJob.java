package de.leonard.flashcards.generation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.leonard.flashcards.deck.Language;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(name = "target_deck_id")
    private UUID targetDeckId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardStyle style;

    @Column(name = "cards_per_chunk", nullable = false)
    private int cardsPerChunk;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "generation_job_documents", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "position")
    @Column(name = "document_id", nullable = false)
    private List<UUID> documentIds = new ArrayList<>();

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "processed_chunks", nullable = false)
    private int processedChunks;

    @Column(name = "skipped_chunks", nullable = false)
    private int skippedChunks;

    @Column(name = "generated_card_count", nullable = false)
    private int generatedCardCount;

    @Column(name = "llm_model", length = 64)
    private String llmModel;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected GenerationJob() {
        // fuer JPA
    }

    public GenerationJob(List<UUID> documentIds, UUID targetDeckId, Language language,
                         CardStyle style, int cardsPerChunk) {
        this.id = UUID.randomUUID();
        this.status = JobStatus.PENDING;
        this.documentIds = new ArrayList<>(documentIds);
        this.targetDeckId = targetDeckId;
        this.language = language;
        this.style = style;
        this.cardsPerChunk = cardsPerChunk;
        this.createdAt = Instant.now();
    }

    public void start(int totalChunks, String llmModel) {
        this.status = JobStatus.RUNNING;
        this.totalChunks = totalChunks;
        this.llmModel = llmModel;
    }

    public void recordProcessedChunk(int generatedCards) {
        this.processedChunks++;
        this.generatedCardCount += generatedCards;
    }

    public void recordSkippedChunk() {
        this.processedChunks++;
        this.skippedChunks++;
    }

    public void complete(String note) {
        this.status = JobStatus.COMPLETED;
        this.errorMessage = note;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public void assignDeck(UUID deckId) {
        this.targetDeckId = deckId;
    }

    public UUID getId() {
        return id;
    }

    public JobStatus getStatus() {
        return status;
    }

    public UUID getTargetDeckId() {
        return targetDeckId;
    }

    public Language getLanguage() {
        return language;
    }

    public CardStyle getStyle() {
        return style;
    }

    public int getCardsPerChunk() {
        return cardsPerChunk;
    }

    public List<UUID> getDocumentIds() {
        return List.copyOf(documentIds);
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public int getSkippedChunks() {
        return skippedChunks;
    }

    public int getGeneratedCardCount() {
        return generatedCardCount;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
