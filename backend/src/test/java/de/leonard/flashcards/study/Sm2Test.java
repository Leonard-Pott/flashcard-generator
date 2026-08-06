package de.leonard.flashcards.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.study.Sm2.ReviewOutcome;
import de.leonard.flashcards.study.Sm2.State;

class Sm2Test {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);

    /** Bewusst spaet am Abend: so faellt auf, wenn Tagesintervalle an der Uhrzeit kleben. */
    private static final Instant NOW = TODAY.atTime(21, 30).atZone(ZONE).toInstant();

    private final Sm2 sm2 = new Sm2();

    /** Frische Karte: erster Lernschritt, noch nie gewusst. */
    private static State newCard() {
        return new State(Card.INITIAL_EASE_FACTOR, 0, 0, 0);
    }

    /** Karte im Tagesrhythmus. */
    private static State graduated(double easeFactor, int intervalDays, int repetitions) {
        return new State(easeFactor, intervalDays, repetitions, Sm2.GRADUATED);
    }

    private static Instant inMinutes(long minutes) {
        return NOW.plus(Duration.ofMinutes(minutes));
    }

    private static Instant startOfDayIn(int days) {
        return TODAY.plusDays(days).atStartOfDay(ZONE).toInstant();
    }

    private ReviewOutcome review(State state, int quality) {
        return sm2.apply(state, quality, NOW, ZONE, null);
    }

    // --- Lernschritte einer neuen Karte -------------------------------------------------

    @Test
    void againOnANewCardComesBackInTenMinutes() {
        ReviewOutcome outcome = review(newCard(), 0);

        assertThat(outcome.learningStep()).isZero();
        assertThat(outcome.dueAt()).isEqualTo(inMinutes(10));
        // Vor dem Abschluss der Lernschritte laeuft noch kein Tagesintervall.
        assertThat(outcome.repetitions()).isZero();
        assertThat(outcome.intervalDays()).isZero();
    }

    @Test
    void hardRepeatsTheCurrentStep() {
        ReviewOutcome outcome = review(new State(Card.INITIAL_EASE_FACTOR, 0, 0, 0), 3);

        assertThat(outcome.learningStep()).isZero();
        assertThat(outcome.dueAt()).isEqualTo(inMinutes(10));
    }

    @Test
    void goodOnANewCardGraduatesIntoTheDailyRhythm() {
        // Wer die Karte auf Anhieb weiss, braucht keinen Zehn-Minuten-Nachtest.
        ReviewOutcome outcome = review(newCard(), 4);

        assertThat(outcome.learningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(outcome.repetitions()).isEqualTo(1);
        assertThat(outcome.intervalDays()).isEqualTo(1);
        assertThat(outcome.dueAt()).isEqualTo(startOfDayIn(1));
    }

    @Test
    void goodAfterALapsedStepGraduates() {
        ReviewOutcome outcome = review(new State(Card.INITIAL_EASE_FACTOR, 0, 0, 0), 4);

        assertThat(outcome.learningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(outcome.intervalDays()).isEqualTo(1);
    }

    @Test
    void easySkipsTheLearningStepsEntirely() {
        ReviewOutcome outcome = review(newCard(), 5);

        assertThat(outcome.learningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(outcome.repetitions()).isEqualTo(1);
        assertThat(outcome.intervalDays()).isEqualTo(4);
        assertThat(outcome.dueAt()).isEqualTo(startOfDayIn(4));
    }

    @Test
    void learningStepsLeaveTheEaseFactorAlone() {
        // Eine noch nicht gelernte Karte sagt nichts ueber ihre dauerhafte Schwierigkeit.
        double easeFactor = Card.INITIAL_EASE_FACTOR;
        for (int i = 0; i < 5; i++) {
            easeFactor = review(new State(easeFactor, 0, 0, 0), 0).easeFactor();
        }

        assertThat(easeFactor).isEqualTo(Card.INITIAL_EASE_FACTOR);
    }

    // --- Tagesrhythmus: das urspruengliche SM-2 ------------------------------------------

    @Test
    void secondCorrectAnswerSchedulesSixDaysAhead() {
        ReviewOutcome outcome = review(graduated(Card.INITIAL_EASE_FACTOR, 1, 1), 4);

        assertThat(outcome.repetitions()).isEqualTo(2);
        assertThat(outcome.intervalDays()).isEqualTo(6);
        assertThat(outcome.dueAt()).isEqualTo(startOfDayIn(6));
    }

    @Test
    void fromTheThirdRepetitionTheIntervalGrowsByTheEaseFactor() {
        // 6 Tage * 2.5 = 15
        ReviewOutcome outcome = review(graduated(2.5, 6, 2), 4);

        assertThat(outcome.repetitions()).isEqualTo(3);
        assertThat(outcome.intervalDays()).isEqualTo(15);
        assertThat(outcome.dueAt()).isEqualTo(startOfDayIn(15));
    }

    @Test
    void roundsTheGrownInterval() {
        // 15 * 2.36 = 35.4 -> 35
        assertThat(review(graduated(2.36, 15, 3), 4).intervalDays()).isEqualTo(35);
        // 10 * 2.45 = 24.5 -> 25
        assertThat(review(graduated(2.45, 10, 3), 4).intervalDays()).isEqualTo(25);
    }

    @Test
    void dayIntervalsLandOnTheStartOfTheDayNotOnTheClockTimeOfTheReview() {
        // Abends um 21:30 gelernt und trotzdem am naechsten Morgen faellig.
        assertThat(review(graduated(2.5, 1, 1), 4).dueAt())
                .isEqualTo(TODAY.plusDays(6).atStartOfDay(ZONE).toInstant());
    }

    // --- Patzer und Nachlernen ----------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aLapseBringsTheCardBackInTenMinutes(int quality) {
        ReviewOutcome outcome = review(graduated(2.5, 30, 5), quality);

        assertThat(outcome.learningStep()).isZero();
        assertThat(outcome.dueAt()).isEqualTo(inMinutes(10));
    }

    @Test
    void aLapseShortensTheIntervalInsteadOfResettingIt() {
        // 30 Tage * 0.3 = 9, nicht zurueck auf 1.
        ReviewOutcome outcome = review(graduated(2.5, 30, 5), 0);

        assertThat(outcome.intervalDays()).isEqualTo(9);
        // Die Zaehlung der Wiederholungen bleibt stehen, die Karte faengt nicht von vorn an.
        assertThat(outcome.repetitions()).isEqualTo(5);
    }

    @Test
    void aShortIntervalNeverShrinksBelowOneDay() {
        assertThat(review(graduated(2.5, 1, 1), 0).intervalDays()).isEqualTo(1);
    }

    @Test
    void relearningUsesASingleTenMinuteStep() {
        // Karte steckt im Nachlernen (repetitions > 0, learningStep >= 0).
        ReviewOutcome outcome = sm2.apply(new State(1.7, 9, 5, 0), 4, NOW, ZONE, null);

        assertThat(outcome.learningStep()).isEqualTo(Sm2.GRADUATED);
        assertThat(outcome.intervalDays()).isEqualTo(9);
        assertThat(outcome.repetitions()).isEqualTo(6);
        assertThat(outcome.dueAt()).isEqualTo(startOfDayIn(9));
    }

    @Test
    void relearningAgainStaysInTheSession() {
        ReviewOutcome outcome = sm2.apply(new State(1.7, 9, 5, 0), 0, NOW, ZONE, null);

        assertThat(outcome.learningStep()).isZero();
        assertThat(outcome.dueAt()).isEqualTo(inMinutes(10));
    }

    @Test
    void aFullLapseCycleEndsOnTheShortenedInterval() {
        // Patzer bei 30 Tagen, danach im selben Durchgang nachgelernt.
        ReviewOutcome lapse = review(graduated(2.5, 30, 5), 0);
        ReviewOutcome recovered = sm2.apply(
                new State(lapse.easeFactor(), lapse.intervalDays(), lapse.repetitions(),
                        lapse.learningStep()),
                4, NOW, ZONE, null);

        assertThat(recovered.intervalDays()).isEqualTo(9);
        assertThat(recovered.dueAt()).isEqualTo(startOfDayIn(9));
    }

    // --- Pruefungstermin ----------------------------------------------------------------

    private ReviewOutcome reviewBeforeExam(State state, int quality, int daysUntilExam) {
        return sm2.apply(state, quality, NOW, ZONE, TODAY.plusDays(daysUntilExam));
    }

    @Test
    void anIntervalNeverReachesPastTheExam() {
        // Ohne Deckel waeren es 15 Tage - die Karte saehe man vor der Pruefung nie wieder.
        assertThat(reviewBeforeExam(graduated(2.5, 6, 2), 4, 10).intervalDays()).isEqualTo(5);
    }

    @Test
    void theCapIsHalfTheRemainingTime() {
        assertThat(reviewBeforeExam(graduated(2.5, 30, 3), 4, 10).intervalDays()).isEqualTo(5);
        assertThat(reviewBeforeExam(graduated(2.5, 30, 3), 4, 4).intervalDays()).isEqualTo(2);
        // Am Vortag bleibt nur noch der Pruefungstag selbst.
        assertThat(reviewBeforeExam(graduated(2.5, 30, 3), 4, 1).intervalDays()).isEqualTo(1);
    }

    @Test
    void shortIntervalsStayBelowTheCap() {
        // Der Deckel verlaengert nie - er kuerzt nur.
        assertThat(reviewBeforeExam(graduated(2.5, 1, 1), 4, 60).intervalDays()).isEqualTo(6);
    }

    @Test
    void theCapAlsoAppliesWhenACardGraduates() {
        assertThat(reviewBeforeExam(newCard(), 5, 4).intervalDays()).isEqualTo(2);
    }

    @Test
    void aPastExamTurnsTheCapOff() {
        assertThat(sm2.apply(graduated(2.5, 6, 2), 4, NOW, ZONE, TODAY.minusDays(1)).intervalDays())
                .isEqualTo(15);
        assertThat(sm2.apply(graduated(2.5, 6, 2), 4, NOW, ZONE, TODAY).intervalDays()).isEqualTo(15);
    }

    @Test
    void theLapsePathIgnoresTheCapAndStaysOnMinutes() {
        ReviewOutcome outcome = reviewBeforeExam(graduated(2.5, 30, 5), 0, 10);

        assertThat(outcome.dueAt()).isEqualTo(inMinutes(10));
        assertThat(outcome.intervalDays()).isEqualTo(9);
    }

    @Test
    void aTenDayRunTapersTowardsTheExam() {
        // Eine Karte, die durchgehend mit "Gut" bewertet wird, zehn Tage vor der Pruefung.
        LocalDate exam = TODAY.plusDays(10);
        State state = graduated(Card.INITIAL_EASE_FACTOR, 1, 1);

        int[] intervals = new int[5];
        LocalDate day = TODAY;
        for (int i = 0; i < 5; i++) {
            Instant at = day.atTime(21, 30).atZone(ZONE).toInstant();
            ReviewOutcome outcome = sm2.apply(state, 4, at, ZONE, exam);
            intervals[i] = outcome.intervalDays();
            state = graduated(outcome.easeFactor(), outcome.intervalDays(), outcome.repetitions());
            day = day.plusDays(outcome.intervalDays());
        }

        // Abstaende wachsen nicht, sondern ziehen sich zusammen - und der letzte Termin
        // liegt auf dem Pruefungstag, nicht dahinter.
        assertThat(intervals).containsExactly(5, 2, 1, 1, 1);
        assertThat(day).isEqualTo(exam);
    }

    // --- Ease-Faktor --------------------------------------------------------------------

    @Test
    void perfectAnswerRaisesTheEaseFactor() {
        // 2.5 + (0.1 - 0 * (0.08 + 0)) = 2.6
        assertThat(review(graduated(2.5, 6, 2), 5).easeFactor()).isCloseTo(2.6, within(0.0001));
    }

    @Test
    void barelyPassedAnswerLowersTheEaseFactor() {
        // miss = 2: 2.5 + (0.1 - 2 * (0.08 + 2 * 0.02)) = 2.5 - 0.14 = 2.36
        assertThat(review(graduated(2.5, 6, 2), 3).easeFactor()).isCloseTo(2.36, within(0.0001));
    }

    @Test
    void goodAnswerKeepsTheEaseFactorUnchanged() {
        // miss = 1: 2.5 + (0.1 - 1 * (0.08 + 0.02)) = 2.5
        assertThat(review(graduated(2.5, 6, 2), 4).easeFactor()).isCloseTo(2.5, within(0.0001));
    }

    @Test
    void easeFactorNeverDropsBelowTheFloor() {
        // Auch nach vielen schlechten Bewertungen bleibt der Faktor bei 1.3.
        double easeFactor = Card.INITIAL_EASE_FACTOR;
        for (int i = 0; i < 20; i++) {
            easeFactor = review(graduated(easeFactor, 10, 5), 0).easeFactor();
        }

        assertThat(easeFactor).isEqualTo(1.3);
    }

    @Test
    void failedAnswerStillLowersTheEaseFactor() {
        // miss = 5: 2.5 + (0.1 - 5 * (0.08 + 5 * 0.02)) = 2.5 - 0.8 = 1.7
        ReviewOutcome outcome = review(graduated(2.5, 30, 5), 0);

        // Exakt 1.7, nicht 1.7000000000000002 - der Wert wird gerundet gespeichert.
        assertThat(outcome.easeFactor()).isEqualTo(1.7);
    }

    @Test
    void aSeriesOfGoodAnswersProducesGrowingIntervals() {
        // Start nach dem Abschluss der Lernschritte: 1 Tag, eine Wiederholung.
        State state = graduated(Card.INITIAL_EASE_FACTOR, 1, 1);

        int[] intervals = new int[4];
        for (int i = 0; i < 4; i++) {
            ReviewOutcome outcome = review(state, 5);
            state = graduated(outcome.easeFactor(), outcome.intervalDays(), outcome.repetitions());
            intervals[i] = outcome.intervalDays();
        }

        // Ease waechst 2.5 -> 2.6 -> 2.7 -> 2.8. Daraus 6, 6*2.6=16, 16*2.7=43, 43*2.8=120.
        assertThat(intervals).containsExactly(6, 16, 43, 120);
        assertThat(state.easeFactor()).isCloseTo(2.9, within(0.0001));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 42})
    void rejectsQualityOutsideZeroToFive(int quality) {
        assertThatThrownBy(() -> review(graduated(2.5, 1, 1), quality))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
