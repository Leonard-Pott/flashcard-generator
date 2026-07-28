package de.leonard.flashcards.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/**
 * Prueft die Gemini-Anbindung gegen einen gemockten Server. Es gehen nie echte
 * Aufrufe hinaus - die wuerden Tageskontingent kosten.
 */
class GeminiLlmClientTest {

    private static final String PATH = "/v1beta/models/gemini-3.6-flash:generateContent";

    private static WireMockServer wireMock;

    private RecordingSleeper sleeper;

    @BeforeAll
    static void startServer() {
        // h2c aus: der JDK-HttpClient hinter RestClient handelt sonst HTTP/2 im Klartext
        // aus und bricht die Antwort mit RST_STREAM ab. Gegen Gemini laeuft alles ueber TLS.
        wireMock = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .http2PlainDisabled(true));
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        sleeper = new RecordingSleeper();
    }

    @Test
    void sendsSystemAndUserPromptInGeminiFormat() {
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(candidate("[]", "STOP"))));

        client().complete("Du bist ein Lernkarten-Ersteller.", "Erstelle Karten zu ITIL.");

        wireMock.verify(postRequestedFor(urlPathEqualTo(PATH))
                .withHeader("x-goog-api-key", equalTo("test-key"))
                .withRequestBody(equalToJson("""
                        {
                          "systemInstruction": {"parts": [{"text": "Du bist ein Lernkarten-Ersteller."}]},
                          "contents": [{"role": "user", "parts": [{"text": "Erstelle Karten zu ITIL."}]}],
                          "generationConfig": {"responseMimeType": "application/json"}
                        }
                        """)));
    }

    @Test
    void returnsTheTextOfTheFirstCandidate() {
        String json = """
                [{"front": "Was ist ein Incident?", "back": "Eine ungeplante Unterbrechung."}]""";
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson(candidate(json, "STOP"))));

        String text = client().complete("System", "User");

        assertThat(text).isEqualTo(json);
    }

    @Test
    void joinsMultipleParts() {
        wireMock.stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("""
                {"candidates": [{"finishReason": "STOP", "content": {"parts": [
                  {"text": "[{\\"front\\": \\"A\\","},
                  {"text": " \\"back\\": \\"B\\"}]"}
                ]}}]}
                """)));

        assertThat(client().complete("System", "User"))
                .isEqualTo("[{\"front\": \"A\", \"back\": \"B\"}]");
    }

    @Test
    void retriesOnTooManyRequestsAndSucceeds() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .inScenario("rate limit").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withBody("{\"error\": \"quota\"}"))
                .willSetStateTo("zweiter Versuch"));
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .inScenario("rate limit").whenScenarioStateIs("zweiter Versuch")
                .willReturn(okJson(candidate("[]", "STOP"))));

        String text = client().complete("System", "User");

        assertThat(text).isEqualTo("[]");
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(PATH)));
        assertThat(backoffs()).containsExactly(Duration.ofSeconds(2));
        assertThat(throttles()).hasSize(1);
    }

    @Test
    void usesExponentialBackoffAndGivesUpAfterMaxRetries() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(503).withBody("overloaded")));

        assertThatThrownBy(() -> client().complete("System", "User"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("nach 5 Versuchen")
                .hasMessageContaining("overloaded");

        wireMock.verify(5, postRequestedFor(urlPathEqualTo(PATH)));
        assertThat(backoffs()).containsExactly(
                Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(16));
        // Vor jedem Versuch ausser dem ersten wird gedrosselt, hoechstens die vollen 6s.
        assertThat(throttles()).hasSize(4)
                .allSatisfy(wait -> assertThat(wait).isLessThanOrEqualTo(Duration.ofSeconds(6)));
    }

    @Test
    void doesNotRetryOnBadRequest() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\": \"API key not valid\"}")));

        assertThatThrownBy(() -> client().complete("System", "User"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("API key not valid");

        wireMock.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void reportsTruncatedResponseSeparately() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(okJson(candidate("[{\"front\": \"unvollstaend", "MAX_TOKENS"))));

        assertThatThrownBy(() -> client().complete("System", "User"))
                .isInstanceOf(LlmResponseTruncatedException.class)
                .hasMessageContaining("cardsPerChunk");

        // Ein Wiederholen wuerde dasselbe abgeschnittene Ergebnis liefern.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void reportsBlockedPrompt() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}")));

        assertThatThrownBy(() -> client().complete("System", "User"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("blockiert")
                .hasMessageContaining("SAFETY");
    }

    @Test
    void reportsEmptyAnswer() {
        wireMock.stubFor(post(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"candidates\": [{\"finishReason\": \"STOP\"}]}")));

        assertThatThrownBy(() -> client().complete("System", "User"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("leere Antwort");
    }

    @Test
    void reportsTheConfiguredModel() {
        assertThat(client().modelName()).isEqualTo("gemini-3.6-flash");
    }

    /**
     * Die Wartezeiten wechseln sich ab: erst der Backoff nach einem Fehlschlag, dann die
     * Drosselung vor dem naechsten Versuch. Die Drosselung haengt an der echten Uhr und
     * ist deshalb nur der Groesse nach pruefbar - ihre Rechnung deckt {@link PacerTest} ab.
     */
    private List<Duration> backoffs() {
        return everySecondFrom(0);
    }

    private List<Duration> throttles() {
        return everySecondFrom(1);
    }

    private List<Duration> everySecondFrom(int offset) {
        List<Duration> sleeps = sleeper.sleeps();
        return IntStream.range(0, sleeps.size())
                .filter(i -> i % 2 == offset)
                .mapToObj(sleeps::get)
                .toList();
    }

    private GeminiLlmClient client() {
        GeminiProperties properties = new GeminiProperties(
                "test-key",
                "gemini-3.6-flash",
                wireMock.baseUrl() + "/v1beta",
                10,
                20,
                4,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .build();

        return new GeminiLlmClient(restClient, new ObjectMapper(), properties, sleeper);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String candidate(String text, String finishReason) {
        return """
                {"candidates": [{"finishReason": "%s", "content": {"role": "model", "parts": [{"text": %s}]}}]}
                """.formatted(finishReason, quote(text));
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
