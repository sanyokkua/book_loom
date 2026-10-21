package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.ui.state.ProgressFigures.Figures;

/**
 * The steps of an operation counted into one share that never falls and a time left taken per kind of step. The clock
 * is a plain {@link Instant} each call is handed, so no test waits and no figure is re-derived here.
 */
class ProgressLedgerTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");

    private final RunClock clock = new RunClock(T0);
    private final ProgressLedger ledger = new ProgressLedger(clock);
    private Instant now = T0;

    /** Ends {@code count} steps of {@code kind}, each {@code seconds} after the last. */
    private void run(final StepKind kind, final int count, final long seconds) {
        IntStream.range(0, count).forEach(n -> {
            now = now.plusSeconds(seconds);
            ledger.ended(kind, StepResult.DONE, now);
        });
    }

    // IF steps were counted as one list per task, THEN three tasks of three requests would not read as nine steps.
    @Test
    void read_threeTasksOfThreeRequestsFourDone_isNineStepsAt44Percent() {
        IntStream.range(0, 3).forEach(task -> ledger.planned(StepKind.REQUEST, 3));
        run(StepKind.REQUEST, 4, 5);

        final Figures read = ledger.read(now);

        assertThat(read.total()).isEqualTo(9);
        assertThat(read.done()).isEqualTo(4);
        assertThat(read.percent()).isEqualTo(44);
    }

    // IF added work lowered the bar, THEN a failed check that adds a repair would move it backwards.
    @Test
    void read_tenStepsAdded_neverLowersTheBarAndMovesOnOnceCaughtUp() {
        ledger.planned(StepKind.DRAFT, 20);
        run(StepKind.DRAFT, 10, 5);
        assertThat(ledger.read(now).percent()).isEqualTo(50);

        ledger.planned(StepKind.REPAIR, 10);
        assertThat(ledger.read(now).percent()).isEqualTo(50);
        assertThat(ledger.read(now).total()).isEqualTo(30);

        run(StepKind.DRAFT, 5, 5);
        assertThat(ledger.read(now).percent()).isEqualTo(50);
        run(StepKind.DRAFT, 1, 5);
        assertThat(ledger.read(now).percent()).isEqualTo(53);
    }

    // IF 51 percent were floored, THEN 140 of 276 steps would read 50; and a bar that is not finished must not read
    // 100.
    @ParameterizedTest(name = "{0} of {1} steps: {2}%")
    @CsvSource({"140, 276, 51", "18, 45, 40", "99, 100, 99", "199, 200, 99", "200, 200, 100"})
    void read_doneOfPlanned_isTheShareRoundedAndNeverFullBeforeTheEnd(
            final int done, final int planned, final int percent) {
        ledger.planned(StepKind.DRAFT, planned);
        run(StepKind.DRAFT, done, 1);

        assertThat(ledger.read(now).percent()).isEqualTo(percent);
    }

    // IF the share were taken per chunk, THEN the bar would reach 100 at the end of every chunk of the book.
    @Test
    void read_oneChunkOfABookDone_isNotTheWholeBar() {
        ledger.seed(100, 0);
        ledger.planned(StepKind.DRAFT, 10);
        ledger.planned(StepKind.CHECK, 1);
        run(StepKind.DRAFT, 10, 5);
        run(StepKind.CHECK, 1, 5);
        ledger.seed(100, 10);

        final Figures read = ledger.read(now);

        assertThat(read.done()).isEqualTo(11);
        assertThat(read.total()).isEqualTo(110);
        assertThat(read.percent()).isEqualTo(10);
    }

    // IF the time left were not the steps left times the measured time per step of each kind, THEN checks that cost
    // time would be missing from it.
    @Test
    void read_draftsAndChecksMeasured_sumsStepsLeftTimesEachKindsOwnTime() {
        ledger.planned(StepKind.DRAFT, 20);
        ledger.planned(StepKind.CHECK, 4);
        run(StepKind.DRAFT, 10, 12);
        run(StepKind.CHECK, 2, 60);

        final Figures read = ledger.read(now);

        // 10 drafts left at 12 s and 2 checks left at 60 s: 240 s = 4 min.
        assertThat(read.left()).isEqualTo(Duration.ofMinutes(4));
    }

    // IF the old rule (a share of segments) stayed, THEN 52 percent after 2 hours would read ten hours left.
    @Test
    void read_52PercentAfterTwoHoursOfSteadySteps_readsAboutTwoHours() {
        ledger.planned(StepKind.DRAFT, 100);
        run(StepKind.DRAFT, 52, 138);

        final Figures read = ledger.read(now);

        assertThat(read.percent()).isEqualTo(52);
        assertThat(read.left()).isEqualTo(Duration.ofHours(2));
    }

    @ParameterizedTest(name = "{0} drafts of 12 s done of {1}: left {2} min")
    @CsvSource({"5, 105, 20", "10, 70, 12"})
    void read_hundredDraftsOfTwelveSecondsLeft_roundsBelowAnHourToTheMinute(
            final int done, final int planned, final long minutes) {
        ledger.planned(StepKind.DRAFT, planned);
        run(StepKind.DRAFT, done, 12);

        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(minutes));
    }

    // IF the time left appeared before anything was measured, THEN the first slow steps would promise a wrong end.
    @ParameterizedTest(name = "{0} of {1} steps done: shown {2}")
    @CsvSource({"4, 276, false", "5, 276, true", "1, 49, false", "2, 49, true", "4, 200, false", "5, 200, true"})
    void read_fewStepsDone_showsTheTimeLeftOnlyFromFiveStepsOrThreePercent(
            final int done, final int planned, final boolean shown) {
        ledger.planned(StepKind.DRAFT, planned);
        run(StepKind.DRAFT, done, 10);

        assertThat(ledger.read(now).left() != null).isEqualTo(shown);
    }

    // IF the shown figure followed the estimate at once, THEN adding a long stretch of work would make it leap.
    @Test
    void read_estimateJumpsOverTenPercent_movesAtMostTenPercentInAMinute() {
        ledger.planned(StepKind.DRAFT, 100);
        run(StepKind.DRAFT, 10, 60);
        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(90));

        now = now.plusSeconds(60);
        ledger.planned(StepKind.DRAFT, 100);
        final Duration shown = ledger.read(now).left();

        // 190 min is the estimate; 90 min plus a tenth of it is 99 min, which reads 1 h 40 min.
        assertThat(shown).isEqualTo(Duration.ofMinutes(100));
    }

    // IF the countdown itself were held to ten percent a minute, THEN the last minutes would read too long.
    @Test
    void read_estimateFallsBecauseTimePasses_followsTheClock() {
        ledger.planned(StepKind.DRAFT, 100);
        run(StepKind.DRAFT, 90, 10);
        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(2));

        run(StepKind.DRAFT, 6, 10);

        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(1));
    }

    // IF a night's pause counted as step time, THEN the next estimate would be absurd.
    @Test
    void read_thirtyMinutePause_isLeftOutOfTheMeasuredTime() {
        ledger.planned(StepKind.DRAFT, 100);
        run(StepKind.DRAFT, 10, 10);
        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(15));

        clock.paused(now);
        now = now.plus(Duration.ofMinutes(30));
        clock.resumed(now);
        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(15));
        run(StepKind.DRAFT, 1, 10);

        assertThat(ledger.read(now).left()).isEqualTo(Duration.ofMinutes(15));
    }

    // IF a kept-as-is draft counted its zero seconds, THEN the mean would drop and the estimate would be too short.
    @Test
    void read_stepsWithNoCall_countAsDoneButNotAsMeasuredTime() {
        ledger.planned(StepKind.DRAFT, 100);
        IntStream.range(0, 10).forEach(n -> ledger.ended(StepKind.DRAFT, StepResult.NO_CALL, now));
        run(StepKind.DRAFT, 10, 12);

        final Figures read = ledger.read(now);

        assertThat(read.done()).isEqualTo(20);
        // 80 drafts left; the 120 s spent are shared by the 10 timed drafts, 12 s each: 960 s = 16 min.
        assertThat(read.left()).isEqualTo(Duration.ofMinutes(16));
    }

    // IF a reopened project started its bar at zero, THEN 74 decided segments would read as nothing done.
    @Test
    void rebuilt_74Of142Decided_startsTheBarThereAndHidesTimeLeftUntilFiveNewSteps() {
        final ProgressLedger rebuilt =
                ProgressLedger.rebuilt(clock, List.of(new ProgressLedger.ChunkState(142, 74)), 0);
        assertThat(rebuilt.read(now).percent()).isEqualTo(52);
        assertThat(rebuilt.read(now).done()).isEqualTo(74);
        assertThat(rebuilt.read(now).total()).isEqualTo(142);
        assertThat(rebuilt.read(now).left()).isNull();

        IntStream.range(0, 4).forEach(n -> {
            now = now.plusSeconds(10);
            rebuilt.ended(StepKind.DRAFT, StepResult.DONE, now);
        });
        assertThat(rebuilt.read(now).left()).isNull();

        now = now.plusSeconds(10);
        rebuilt.ended(StepKind.DRAFT, StepResult.DONE, now);
        assertThat(rebuilt.read(now).left()).isNotNull();
    }

    // IF the checks of finished chunks were not counted, THEN a Balanced book would reopen below where it stood.
    @Test
    void rebuilt_fiveChunksOfEightTwoDecided_is18Of45Steps() {
        final List<ProgressLedger.ChunkState> chunks = List.of(
                new ProgressLedger.ChunkState(8, 8),
                new ProgressLedger.ChunkState(8, 8),
                new ProgressLedger.ChunkState(8, 0),
                new ProgressLedger.ChunkState(8, 0),
                new ProgressLedger.ChunkState(8, 0));

        final Figures read = ProgressLedger.rebuilt(clock, chunks, 1).read(now);

        assertThat(read.done()).isEqualTo(18);
        assertThat(read.total()).isEqualTo(45);
        assertThat(read.percent()).isEqualTo(40);
    }

    // IF "How progress is counted" could not list each kind, THEN the bar would be unexplained.
    @Test
    void read_balancedRun_listsPlannedDoneAndMeasuredSecondsPerKind() {
        ledger.planned(StepKind.DRAFT, 8);
        ledger.planned(StepKind.CHECK, 1);
        run(StepKind.DRAFT, 4, 12);
        run(StepKind.CHECK, 1, 30);

        final List<ProgressFigures.KindFigures> kinds = ledger.read(now).kinds();

        assertThat(kinds)
                .containsExactly(
                        new ProgressFigures.KindFigures(StepKind.DRAFT, 8, 4, 12.0),
                        new ProgressFigures.KindFigures(StepKind.CHECK, 1, 1, 30.0));
    }
}
