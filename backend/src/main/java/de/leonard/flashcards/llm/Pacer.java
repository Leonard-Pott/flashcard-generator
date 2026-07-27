package de.leonard.flashcards.llm;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Haelt einen Mindestabstand zwischen zwei Anfragen ein. Der Free Tier erlaubt nur
 * wenige Anfragen pro Minute; ohne Drosselung scheitert jeder laengere Lauf an HTTP 429.
 */
public class Pacer {

    private final long minimumIntervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    private Long lastRequestAt;

    public Pacer(int requestsPerMinute, Sleeper sleeper) {
        this(requestsPerMinute, sleeper, System::nanoTime);
    }

    Pacer(int requestsPerMinute, Sleeper sleeper, LongSupplier nanoTime) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("requestsPerMinute muss mindestens 1 sein.");
        }
        this.minimumIntervalNanos = Duration.ofMinutes(1).toNanos() / requestsPerMinute;
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    /** Blockiert so lange, bis die naechste Anfrage erlaubt ist. */
    public synchronized void acquire() {
        long now = nanoTime.getAsLong();

        if (lastRequestAt != null) {
            long waitNanos = minimumIntervalNanos - (now - lastRequestAt);
            if (waitNanos > 0) {
                sleepQuietly(Duration.ofNanos(waitNanos));
                now = nanoTime.getAsLong();
            }
        }
        lastRequestAt = now;
    }

    private void sleepQuietly(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Warten auf das naechste Zeitfenster wurde unterbrochen.", e);
        }
    }
}
