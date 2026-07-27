package de.leonard.flashcards.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.leonard.flashcards.llm.GeminiMessages.Candidate;
import de.leonard.flashcards.llm.GeminiMessages.Part;
import de.leonard.flashcards.llm.GeminiMessages.Request;
import de.leonard.flashcards.llm.GeminiMessages.Response;

/**
 * Gemini ueber einfaches JSON per HTTPS - kein SDK noetig. Kuemmert sich zusaetzlich
 * um Drosselung und Wiederholungen, damit der Rest der Anwendung davon nichts wissen muss.
 */
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    /** Nur diese Antworten sind ein Wiederholen wert; alles andere ist ein echter Fehler. */
    private static final List<Integer> RETRYABLE_STATUS = List.of(429, 500, 502, 503, 504);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;
    private final Pacer pacer;
    private final Sleeper sleeper;

    public GeminiLlmClient(RestClient restClient, ObjectMapper objectMapper,
                           GeminiProperties properties, Sleeper sleeper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sleeper = sleeper;
        this.pacer = new Pacer(properties.requestsPerMinute(), sleeper);
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Request request = Request.of(systemPrompt, userPrompt);
        Duration backoff = properties.initialBackoff();
        Attempt lastFailure = null;

        for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
            pacer.acquire();
            Attempt result = send(request);

            if (result.succeeded()) {
                return extractText(result.response());
            }
            if (!result.retryable() || attempt == properties.maxRetries()) {
                throw new LlmException(result.describe(properties.maxRetries() + 1));
            }

            log.warn("Gemini antwortete mit {}, neuer Versuch in {}s ({} von {})",
                    result.describeShort(), backoff.toSeconds(), attempt + 1, properties.maxRetries());
            sleepQuietly(backoff);
            backoff = backoff.multipliedBy(2);
            lastFailure = result;
        }

        // Nicht erreichbar: die Schleife wirft im letzten Durchlauf.
        throw new LlmException(lastFailure == null
                ? "Gemini lieferte keine Antwort."
                : lastFailure.describe(properties.maxRetries() + 1));
    }

    private Attempt send(Request request) {
        RawResponse raw;
        try {
            raw = restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, httpResponse) -> {
                        HttpStatusCode status = httpResponse.getStatusCode();
                        return new RawResponse(status, httpResponse.getBody().readAllBytes());
                    });
        } catch (ResourceAccessException e) {
            // Zeitueberschreitung oder Netzwerkproblem - genauso einen neuen Versuch wert wie ein 503.
            return Attempt.networkFailure(e);
        }

        if (!raw.status().is2xxSuccessful()) {
            return Attempt.failure(raw.status().value(), new String(raw.body(), StandardCharsets.UTF_8));
        }

        // Bewusst ausserhalb des Exchange: unlesbares JSON ist ein echter Fehler und
        // kein Grund, es noch viermal zu versuchen.
        try {
            return Attempt.success(objectMapper.readValue(raw.body(), Response.class));
        } catch (IOException e) {
            throw new LlmException("Antwort von Gemini war kein gueltiges JSON: " + e.getMessage(), e);
        }
    }

    private record RawResponse(HttpStatusCode status, byte[] body) {
    }

    private String extractText(Response response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            String blockReason = response != null && response.promptFeedback() != null
                    ? response.promptFeedback().blockReason()
                    : null;
            throw new LlmException(blockReason != null
                    ? "Gemini hat den Prompt blockiert: " + blockReason
                    : "Gemini lieferte keine Kandidaten zurueck.");
        }

        Candidate candidate = response.candidates().getFirst();
        if ("MAX_TOKENS".equals(candidate.finishReason())) {
            throw new LlmResponseTruncatedException(
                    "Die Antwort wurde bei MAX_TOKENS abgeschnitten und ist unvollstaendig. "
                            + "Chunk oder cardsPerChunk verkleinern.");
        }
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw new LlmException("Gemini lieferte eine leere Antwort (finishReason: "
                    + candidate.finishReason() + ").");
        }

        String text = candidate.content().parts().stream()
                .map(Part::text)
                .filter(StringUtils::hasText)
                .reduce("", String::concat);

        if (!StringUtils.hasText(text)) {
            throw new LlmException("Gemini lieferte eine leere Antwort.");
        }
        return text;
    }

    private void sleepQuietly(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Warten auf den naechsten Versuch wurde unterbrochen.", e);
        }
    }

    /** Ergebnis eines einzelnen HTTP-Versuchs. */
    private record Attempt(Response response, Integer status, String errorBody, Exception networkError) {

        static Attempt success(Response response) {
            return new Attempt(response, null, null, null);
        }

        static Attempt failure(int status, String errorBody) {
            return new Attempt(null, status, errorBody, null);
        }

        static Attempt networkFailure(Exception e) {
            return new Attempt(null, null, null, e);
        }

        boolean succeeded() {
            return response != null;
        }

        boolean retryable() {
            return networkError != null || (status != null && RETRYABLE_STATUS.contains(status));
        }

        String describeShort() {
            return networkError != null ? networkError.getClass().getSimpleName() : "HTTP " + status;
        }

        String describe(int attempts) {
            if (networkError != null) {
                return "Gemini war nach %d Versuchen nicht erreichbar: %s"
                        .formatted(attempts, networkError.getMessage());
            }
            String body = errorBody == null || errorBody.isBlank() ? "(keine Fehlermeldung)" : errorBody.strip();
            return RETRYABLE_STATUS.contains(status)
                    ? "Gemini antwortete auch nach %d Versuchen mit HTTP %d: %s".formatted(attempts, status, body)
                    : "Gemini antwortete mit HTTP %d: %s".formatted(status, body);
        }
    }
}
