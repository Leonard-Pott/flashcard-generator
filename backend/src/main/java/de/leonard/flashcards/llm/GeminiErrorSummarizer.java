package de.leonard.flashcards.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Macht aus dem Fehlerkoerper von Gemini einen Satz. Der rohe Koerper ist gut zwei
 * Kilobyte JSON - im Log und erst recht im Frontend ist das unlesbar.
 */
class GeminiErrorSummarizer {

    private static final int MAX_RAW_LENGTH = 200;

    private final ObjectMapper objectMapper;

    GeminiErrorSummarizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "(keine Fehlermeldung)";
        }

        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            // "error" ist normalerweise ein Objekt, kann aber auch ein blosser Text sein.
            if (error.isTextual()) {
                return firstSentence(error.asText());
            }
            if (!error.isObject()) {
                return truncate(body);
            }

            String quota = quotaSummary(error);
            if (quota != null) {
                return quota;
            }
            String message = error.path("message").asText("");
            return message.isBlank() ? truncate(body) : firstSentence(message);
        } catch (Exception e) {
            // Kein JSON: dann eben der Anfang des Rohtextes.
            return truncate(body);
        }
    }

    /**
     * Aus der QuotaFailure das herauslesen, was man wirklich wissen will: wie viele
     * Anfragen erlaubt sind und in welchem Zeitraum.
     */
    private String quotaSummary(JsonNode error) {
        for (JsonNode detail : error.path("details")) {
            if (!detail.path("@type").asText("").endsWith("QuotaFailure")) {
                continue;
            }
            for (JsonNode violation : detail.path("violations")) {
                String quotaId = violation.path("quotaId").asText("");
                String value = violation.path("quotaValue").asText("");
                String model = violation.path("quotaDimensions").path("model").asText("");

                if (value.isEmpty()) {
                    continue;
                }
                String period = quotaId.contains("PerDay") ? "pro Tag"
                        : quotaId.contains("PerMinute") ? "pro Minute"
                        : "";
                return "Kontingent erschoepft: hoechstens %s Anfragen%s%s.".formatted(
                        value,
                        period.isEmpty() ? "" : " " + period,
                        model.isEmpty() ? "" : " fuer " + model);
            }
        }
        return null;
    }

    /** Gemini haengt an die Meldung noch Details mit Zeilenumbruch an; die interessieren hier nicht. */
    private String firstSentence(String message) {
        if (message.isBlank()) {
            return "(keine Fehlermeldung)";
        }
        int lineBreak = message.indexOf('\n');
        String firstLine = lineBreak < 0 ? message : message.substring(0, lineBreak);
        return truncate(firstLine.strip());
    }

    private String truncate(String text) {
        String stripped = text.strip();
        return stripped.length() <= MAX_RAW_LENGTH
                ? stripped
                : stripped.substring(0, MAX_RAW_LENGTH) + " …";
    }
}
