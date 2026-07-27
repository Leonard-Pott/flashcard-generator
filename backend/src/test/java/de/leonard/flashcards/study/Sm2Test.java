package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.study.Sm2.ReviewOutcome;

class Sm2Test {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);

    private final Sm2 sm2 = new Sm2();

    @Test
    void firstCorrectAnswerSchedulesForTomorrow() {
        ReviewOutcome outcome = sm2.apply(Card.INITIAL_EASE_FACTOR, 0, 0, 4, TODAY);

        assertThat(outcome.repetitions()).isEqualTo(1);
        assertThat(outcome.intervalDays()).isEqualTo(1);
        assertThat(outcome.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void secondCorrectAnswerSchedulesSixDaysAhead() {
        ReviewOutcome outcome = sm2.apply(Card.INITIAL_EASE_FACTOR, 1, 1, 4, TODAY);

        assertThat(outcome.repetitions()).isEqualTo(2);
        assertThat(outcome.intervalDays()).isEqualTo(6);
        assertThat(outcome.dueDate()).isEqualTo(TODAY.plusDays(6));
    }

    @Test
    void fromTheThirdRepetitionTheIntervalGrowsByTheEaseFactor() {
        // 6 Tage * 2.5 = 15
        ReviewOutcome outcome = sm2.apply(2.5, 6, 2, 4, TODAY);

        assertThat(outcome.repetitions()).isEqualTo(3);
        assertThat(outcome.intervalDays()).isEqualTo(15);
        assertThat(outcome.dueDate()).isEqualTo(TODAY.plusDays(15));
    }

    @Test
    void roundsTheGrownInterval() {
        // 15 * 2.36 = 35.4 -> 35
        assertThat(sm2.apply(2.36, 15, 3, 4, TODAY).intervalDays()).isEqualTo(35);
        // 10 * 2.45 = 24.5 -> 25
        assertThat(sm2.apply(2.45, 10, 3, 4, TODAY).intervalDays()).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void failedAnswerResetsRepetitionsAndSchedulesForTomorrow(int quality) {
        ReviewOutcome outcome = sm2.apply(2.5, 30, 5, quality, TODAY);

        assertThat(outcome.repetitions()).isZero();
        assertThat(outcome.intervalDays()).isEqualTo(1);
        assertThat(outcome.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void perfectAnswerRaisesTheEaseFactor() {
        // 2.5 + (0.1 - 0 * (0.08 + 0)) = 2.6
        assertThat(sm2.apply(2.5, 6, 2, 5, TODAY).easeFactor()).isCloseTo(2.6, within(0.0001));
    }

    @Test
    void barelyPassedAnswerLowersTheEaseFactor() {
        // miss = 2: 2.5 + (0.1 - 2 * (0.08 + 2 * 0.02)) = 2.5 - 0.14 = 2.36
        assertThat(sm2.apply(2.5, 6, 2, 3, TODAY).easeFactor()).isCloseTo(2.36, within(0.0001));
    }

    @Test
    void goodAnswerKeepsTheEaseFactorUnchanged() {
        // miss = 1: 2.5 + (0.1 - 1 * (0.08 + 0.02)) = 2.5
        assertThat(sm2.apply(2.5, 6, 2, 4, TODAY).easeFactor()).isCloseTo(2.5, within(0.0001));
    }

    @Test
    void easeFactorNeverDropsBelowTheFloor() {
        // Auch nach vielen schlechten Bewertungen bleibt der Faktor bei 1.3.
        double easeFactor = Card.INITIAL_EASE_FACTOR;
        for (int i = 0; i < 20; i++) {
            easeFactor = sm2.apply(easeFactor, 1, 0, 0, TODAY).easeFactor();
        }

        assertThat(easeFactor).isEqualTo(1.3);
    }

    @Test
    void failedAnswerStillLowersTheEaseFactor() {
        // Der Faktor wird auch bei q < 3 angepasst, nicht nur das Intervall zurueckgesetzt.
        // miss = 5: 2.5 + (0.1 - 5 * (0.08 + 5 * 0.02)) = 2.5 - 0.8 = 1.7
        ReviewOutcome outcome = sm2.apply(2.5, 30, 5, 0, TODAY);

        // Exakt 1.7, nicht 1.7000000000000002 - der Wert wird gerundet gespeichert.
        assertThat(outcome.easeFactor()).isEqualTo(1.7);
    }

    @Test
    void aSeriesOfGoodAnswersProducesGrowingIntervals() {
        double easeFactor = Card.INITIAL_EASE_FACTOR;
        int interval = 0;
        int repetitions = 0;

        int[] intervals = new int[5];
        for (int i = 0; i < 5; i++) {
            ReviewOutcome outcome = sm2.apply(easeFactor, interval, repetitions, 5, TODAY);
            easeFactor = outcome.easeFactor();
            interval = outcome.intervalDays();
            repetitions = outcome.repetitions();
            intervals[i] = interval;
        }

        // Der Ease-Faktor waechst bei jeder Bestnote mit: 2.5 -> 2.6 -> 2.7 -> 2.8 -> 2.9.
        // Daraus 6*2.7=16, 16*2.8=45, 45*2.9=131.
        assertThat(intervals).containsExactly(1, 6, 16, 45, 131);
        assertThat(easeFactor).isCloseTo(3.0, within(0.0001));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 42})
    void rejectsQualityOutsideZeroToFive(int quality) {
        assertThatThrownBy(() -> sm2.apply(2.5, 1, 1, quality, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
