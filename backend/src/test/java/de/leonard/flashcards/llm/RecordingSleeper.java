package de.leonard.flashcards.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Merkt sich Wartezeiten, statt tatsaechlich zu warten. */
class RecordingSleeper implements Sleeper {

    private final List<Duration> sleeps = new ArrayList<>();

    @Override
    public void sleep(Duration duration) {
        sleeps.add(duration);
    }

    List<Duration> sleeps() {
        return List.copyOf(sleeps);
    }

    List<Long> sleepSeconds() {
        return sleeps.stream().map(Duration::toSeconds).toList();
    }
}
