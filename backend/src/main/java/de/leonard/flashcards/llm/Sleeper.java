package de.leonard.flashcards.llm;

import java.time.Duration;

/**
 * Wartezeiten als eigene Abstraktion, damit Tests Rate-Limiting und Backoff pruefen
 * koennen, ohne tatsaechlich zu warten.
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    static Sleeper real() {
        return duration -> {
            if (!duration.isNegative() && !duration.isZero()) {
                Thread.sleep(duration.toMillis());
            }
        };
    }
}
