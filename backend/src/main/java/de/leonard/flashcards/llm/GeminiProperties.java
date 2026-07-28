package de.leonard.flashcards.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Konfiguration der Gemini-Anbindung. Der API-Key kommt ausschliesslich aus der
 * Umgebungsvariable GEMINI_API_KEY und steht nie im Repository.
 */
@ConfigurationProperties(prefix = "llm.gemini")
public record GeminiProperties(
        @NotBlank(message = "GEMINI_API_KEY ist nicht gesetzt.")
        String apiKey,
        @NotBlank
        String model,
        @NotBlank
        String baseUrl,
        /** Free Tier: wenige Anfragen pro Minute, modellabhaengig etwa 5-15. */
        @Min(1)
        int requestsPerMinute,
        /**
         * Anfragen pro Tag, die der Tarif zulaesst. Nur zur Vorwarnung im Frontend -
         * durchgesetzt wird das Limit von Google, nicht von uns. Gemessen wird es pro
         * Modell und Projekt, nicht pro Lauf.
         */
        @Min(1)
        int dailyRequestBudget,
        @Min(0)
        int maxRetries,
        /** Wartezeit vor dem ersten neuen Versuch; danach jeweils verdoppelt. */
        Duration initialBackoff,
        Duration timeout) {

    public GeminiProperties {
        if (initialBackoff == null) {
            initialBackoff = Duration.ofSeconds(2);
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(90);
        }
    }
}
