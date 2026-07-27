package de.leonard.flashcards.generation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.leonard.flashcards.deck.Language;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class GenerationDtos {

    private GenerationDtos() {
    }

    /**
     * Entweder {@code deckId} auf einen bestehenden Stapel setzen oder mit
     * {@code newDeckName} einen neuen anlegen lassen - genau eines von beidem.
     */
    public record GenerationRequest(
            @NotEmpty(message = "Es muss mindestens ein Dokument ausgewaehlt sein.")
            List<UUID> documentIds,
            UUID deckId,
            @Size(max = 200, message = "Der Stapelname darf hoechstens 200 Zeichen lang sein.")
            String newDeckName,
            Language language,
            @Min(value = 1, message = "cardsPerChunk muss zwischen 1 und 20 liegen.")
            @Max(value = 20, message = "cardsPerChunk muss zwischen 1 und 20 liegen.")
            Integer cardsPerChunk,
            CardStyle style) {

        public static final int DEFAULT_CARDS_PER_CHUNK = 5;

        public Language languageOrDefault() {
            return language != null ? language : Language.DE;
        }

        public CardStyle styleOrDefault() {
            return style != null ? style : CardStyle.QA;
        }

        public int cardsPerChunkOrDefault() {
            return cardsPerChunk != null ? cardsPerChunk : DEFAULT_CARDS_PER_CHUNK;
        }
    }

    public record GenerationJobResponse(
            UUID id,
            JobStatus status,
            UUID targetDeckId,
            List<UUID> documentIds,
            Language language,
            CardStyle style,
            int cardsPerChunk,
            int totalChunks,
            int processedChunks,
            int skippedChunks,
            int generatedCardCount,
            String llmModel,
            String errorMessage,
            Instant createdAt,
            Instant finishedAt) {

        public static GenerationJobResponse from(GenerationJob job) {
            return new GenerationJobResponse(
                    job.getId(),
                    job.getStatus(),
                    job.getTargetDeckId(),
                    job.getDocumentIds(),
                    job.getLanguage(),
                    job.getStyle(),
                    job.getCardsPerChunk(),
                    job.getTotalChunks(),
                    job.getProcessedChunks(),
                    job.getSkippedChunks(),
                    job.getGeneratedCardCount(),
                    job.getLlmModel(),
                    job.getErrorMessage(),
                    job.getCreatedAt(),
                    job.getFinishedAt());
        }
    }
}
