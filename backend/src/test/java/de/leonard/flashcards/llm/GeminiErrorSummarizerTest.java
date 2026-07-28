package de.leonard.flashcards.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiErrorSummarizerTest {

    private final GeminiErrorSummarizer summarizer = new GeminiErrorSummarizer(new ObjectMapper());

    /** Echter Fehlerkoerper aus einem Lauf, der ins Tageskontingent gelaufen ist. */
    private static final String QUOTA_ERROR = """
            {
              "error": {
                "code": 429,
                "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.\\n* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash\\nPlease retry in 27.26908239s.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {"@type": "type.googleapis.com/google.rpc.Help", "links": []},
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [{
                      "quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
                      "quotaId": "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
                      "quotaDimensions": {"location": "global", "model": "gemini-3.6-flash"},
                      "quotaValue": "20"
                    }]
                  },
                  {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "27s"}
                ]
              }
            }""";

    @Test
    void turnsTheQuotaErrorIntoOneSentence() {
        String summary = summarizer.summarize(QUOTA_ERROR);

        assertThat(summary)
                .isEqualTo("Kontingent erschoepft: hoechstens 20 Anfragen pro Tag fuer gemini-3.6-flash.");
    }

    @Test
    void keepsTheSummaryShort() {
        // Der Rohkoerper ist ueber ein Kilobyte gross - genau das soll nicht mehr im Job landen.
        assertThat(QUOTA_ERROR.length()).isGreaterThan(1_000);
        assertThat(summarizer.summarize(QUOTA_ERROR).length()).isLessThan(100);
    }

    @Test
    void recognisesAMinuteQuota() {
        String body = """
                {"error": {"code": 429, "message": "Too many requests", "details": [
                  {"@type": "type.googleapis.com/google.rpc.QuotaFailure", "violations": [{
                    "quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier",
                    "quotaDimensions": {"model": "gemini-3.6-flash"},
                    "quotaValue": "15"}]}]}}""";

        assertThat(summarizer.summarize(body))
                .isEqualTo("Kontingent erschoepft: hoechstens 15 Anfragen pro Minute fuer gemini-3.6-flash.");
    }

    @Test
    void usesTheFirstLineWhenThereIsNoQuotaDetail() {
        String body = """
                {"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.",
                 "status": "INVALID_ARGUMENT"}}""";

        assertThat(summarizer.summarize(body))
                .isEqualTo("API key not valid. Please pass a valid API key.");
    }

    @Test
    void cutsOffTheDetailsAfterTheFirstLine() {
        String body = """
                {"error": {"message": "Kurzfassung.\\n* Viele Details\\n* Noch mehr Details"}}""";

        assertThat(summarizer.summarize(body)).isEqualTo("Kurzfassung.");
    }

    @Test
    void fallsBackToTheRawTextWhenItIsNotJson() {
        assertThat(summarizer.summarize("502 Bad Gateway")).isEqualTo("502 Bad Gateway");
    }

    @Test
    void handlesErrorAsPlainText() {
        // Nicht jede Fehlerantwort hat "error" als Objekt.
        assertThat(summarizer.summarize("{\"error\": \"API key not valid\"}"))
                .isEqualTo("API key not valid");
    }

    @Test
    void fallsBackWhenTheErrorObjectHasNoMessage() {
        assertThat(summarizer.summarize("{\"error\": {\"code\": 500}}")).contains("500");
    }

    @Test
    void shortensAVeryLongRawText() {
        String summary = summarizer.summarize("x".repeat(1_000));

        assertThat(summary).hasSize(202).endsWith(" …");
    }

    @Test
    void handlesAnEmptyBody() {
        assertThat(summarizer.summarize("")).isEqualTo("(keine Fehlermeldung)");
        assertThat(summarizer.summarize(null)).isEqualTo("(keine Fehlermeldung)");
    }
}
