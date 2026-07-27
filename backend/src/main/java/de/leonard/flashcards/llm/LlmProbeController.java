package de.leonard.flashcards.llm;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kleiner Testendpunkt, um die Anbindung einmal gegen das echte Gemini zu pruefen.
 * Standardmaessig abgeschaltet - jeder Aufruf geht vom Tageskontingent ab. Aktivieren
 * mit {@code LLM_PROBE_ENABLED=true}.
 */
@RestController
@RequestMapping("/api/llm")
@ConditionalOnProperty(name = "llm.probe.enabled", havingValue = "true")
public class LlmProbeController {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Du bist ein Ersteller von Lernkarten. Antworte ausschliesslich mit einem
            JSON-Array im Format [{"front": "...", "back": "..."}]. Kein Markdown, kein Vorwort.
            """;

    private final LlmClient llmClient;

    public LlmProbeController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping("/probe")
    public ProbeResponse probe(@RequestBody(required = false) ProbeRequest request) {
        String systemPrompt = request != null && request.systemPrompt() != null
                ? request.systemPrompt()
                : DEFAULT_SYSTEM_PROMPT;
        String userPrompt = request != null && request.userPrompt() != null
                ? request.userPrompt()
                : "Erstelle genau eine Lernkarte zum Begriff Incident aus ITIL.";

        Instant start = Instant.now();
        String text = llmClient.complete(systemPrompt, userPrompt);
        return new ProbeResponse(
                llmClient.modelName(), text, Duration.between(start, Instant.now()).toMillis());
    }

    public record ProbeRequest(String systemPrompt, String userPrompt) {
    }

    public record ProbeResponse(String model, String text, long durationMs) {
    }
}
