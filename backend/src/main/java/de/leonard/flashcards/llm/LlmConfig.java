package de.leonard.flashcards.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class LlmConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Eigener RestClient nur fuer Gemini: fester Basispfad, Authentifizierung ueber
     * den Header x-goog-api-key und ein Lesetimeout, damit ein haengender Aufruf den
     * Generierungslauf nicht blockiert.
     */
    @Bean
    RestClient geminiRestClient(RestClient.Builder builder, GeminiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(properties.timeout());

        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Bean
    LlmClient llmClient(RestClient geminiRestClient, ObjectMapper objectMapper, GeminiProperties properties) {
        return new GeminiLlmClient(geminiRestClient, objectMapper, properties, Sleeper.real());
    }
}
