package de.leonard.flashcards.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String GENERATION_EXECUTOR = "generationExecutor";

    /**
     * Genau ein Thread: Der Free Tier vertraegt keine parallelen Aufrufe, und die
     * Drosselung im GeminiLlmClient waere bei mehreren Threads schnell wirkungslos.
     * Mehrere Jobs laufen deshalb nacheinander statt gleichzeitig.
     */
    @Bean(GENERATION_EXECUTOR)
    Executor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("generation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
