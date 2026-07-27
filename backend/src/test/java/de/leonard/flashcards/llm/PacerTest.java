package de.leonard.flashcards.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PacerTest {

    /** Simulierte Uhr: schreitet nur voran, wenn der Test sie voranstellt. */
    private final AtomicLong clock = new AtomicLong(0);

    private final RecordingSleeper sleeper = new RecordingSleeper();

    @Test
    void firstRequestPassesWithoutWaiting() {
        Pacer pacer = pacer(10);

        pacer.acquire();

        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void keepsSixSecondsBetweenRequestsAtTenPerMinute() {
        Pacer pacer = pacer(10);

        pacer.acquire();
        pacer.acquire();

        assertThat(sleeper.sleeps()).hasSize(1);
        assertThat(sleeper.sleeps().getFirst()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void waitsOnlyForTheRemainingTime() {
        Pacer pacer = pacer(10);

        pacer.acquire();
        advance(Duration.ofSeconds(4));
        pacer.acquire();

        assertThat(sleeper.sleeps().getFirst()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void doesNotWaitWhenEnoughTimeHasPassed() {
        Pacer pacer = pacer(10);

        pacer.acquire();
        advance(Duration.ofSeconds(30));
        pacer.acquire();

        assertThat(sleeper.sleeps()).isEmpty();
    }

    @Test
    void higherRateMeansShorterInterval() {
        Pacer pacer = pacer(60);

        pacer.acquire();
        pacer.acquire();

        assertThat(sleeper.sleeps().getFirst()).isEqualTo(Duration.ofSeconds(1));
    }

    private Pacer pacer(int requestsPerMinute) {
        // Der Sleeper stellt die simulierte Uhr mit vor, sonst wuerde sie nie voranschreiten.
        Sleeper advancingSleeper = duration -> {
            sleeper.sleep(duration);
            advance(duration);
        };
        return new Pacer(requestsPerMinute, advancingSleeper, clock::get);
    }

    private void advance(Duration duration) {
        clock.addAndGet(duration.toNanos());
    }
}
