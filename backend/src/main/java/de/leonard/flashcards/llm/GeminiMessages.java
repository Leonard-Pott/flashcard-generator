package de.leonard.flashcards.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Anfrage- und Antwortformat der Gemini-API
 * ({@code POST {base-url}/models/{model}:generateContent}).
 */
final class GeminiMessages {

    private GeminiMessages() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(Content systemInstruction, List<Content> contents, GenerationConfig generationConfig) {

        static Request of(String systemPrompt, String userPrompt) {
            return new Request(
                    systemPrompt == null ? null : Content.of(null, systemPrompt),
                    List.of(Content.of("user", userPrompt)),
                    // JSON-Modus: Gemini antwortet ohne Markdown-Fences.
                    new GenerationConfig("application/json"));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Content(String role, List<Part> parts) {

        static Content of(String role, String text) {
            return new Content(role, List.of(new Part(text)));
        }
    }

    record Part(String text) {
    }

    record GenerationConfig(String responseMimeType) {
    }

    record Response(List<Candidate> candidates, PromptFeedback promptFeedback) {
    }

    record Candidate(Content content, String finishReason) {
    }

    /** Gefuellt, wenn der Prompt selbst blockiert wurde - dann fehlen die Kandidaten. */
    record PromptFeedback(String blockReason) {
    }
}
