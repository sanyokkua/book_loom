package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.llm.CallLedgerImpl;

/**
 * The run summary: one INFO line a minute while a job runs and one when it ends, with the counts, call times and
 * current segment a shared log needs. The clock is moved by the test, so nothing sleeps.
 */
class RunSummaryLoggerTest {

    private static final JobProgress START = new JobProgress(JobStage.TRANSLATE, 1, 3, 0, 0, 16);
    private static final JobProgress DECIDED = new JobProgress(JobStage.TRANSLATE, 1, 3, 5, 1, 10, 1, 4, 2, 1, 2);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SettableClock clock = new SettableClock();
    private final CallLedgerImpl ledger = new CallLedgerImpl();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RunSummaryLogger.class);
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
        appender.stop();
    }

    @Test
    void onEvent_lessThanAMinuteAfterTheFirstEvent_writesNoSummary() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        clock.advance(Duration.ofSeconds(59));
        summary.onEvent(decided());

        assertThat(summaries()).isEmpty();
    }

    @Test
    void onEvent_aMinuteAfterTheFirstEvent_writesTheCountsCallTimesAndCurrentSegment() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        summary.onEvent(new SegmentStarted("ch12.xhtml:4", "ch12 · p05", "Source", new ChunkPosition(1, 3, 1, 4)));
        summary.onEvent(answered(Duration.ofSeconds(1), new TokenUsage(120, 50, Duration.ofSeconds(2))));
        summary.onEvent(timedOut(Duration.ofSeconds(3)));
        summary.onEvent(decided());
        clock.advance(Duration.ofSeconds(60));
        summary.onEvent(new SegmentStarted("ch12.xhtml:5", "ch12 · p06", "Source", new ChunkPosition(1, 3, 1, 4)));

        assertThat(summaries())
                .containsExactly("run summary periodic accepted=5 flagged=1 verbatim=2 pending=10 calls=2"
                        + " avgCallMs=2000 p95CallMs=3000 tokensPerSecond=25.0 timeouts=1 current=ch12 · p06");
    }

    @Test
    void onEvent_twoMinutesOfEvents_writesOneLinePerMinuteNotPerEvent() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        clock.advance(Duration.ofSeconds(61));
        summary.onEvent(decided());
        summary.onEvent(decided());
        clock.advance(Duration.ofSeconds(30));
        summary.onEvent(decided());
        clock.advance(Duration.ofSeconds(30));
        summary.onEvent(decided());

        assertThat(summaries()).hasSize(2).allMatch(line -> line.startsWith("run summary periodic "));
    }

    @Test
    void onEvent_finished_writesAFinalLineAtOnce() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        summary.onEvent(decided());
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries())
                .containsExactly("run summary final accepted=5 flagged=1 verbatim=2 pending=10 calls=0 avgCallMs=0"
                        + " p95CallMs=0 tokensPerSecond=- timeouts=0 current=- cost=-");
    }

    // The final line says per kind what the calls cost, so a shared log shows where the time went; the ledger, which
    // every stage's calls reach, is what it counts.
    @Test
    void onEvent_finishedAfterCalls_finalLineCarriesTheTotalsOfEachKind() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock, ledger);
        final TokenUsage usage = new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), 600);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        ledger.record(new CallRecord(CallKind.DRAFT, usage, Duration.ofSeconds(3), null, null));
        summary.onEvent(answered(Duration.ofSeconds(3), usage));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries())
                .singleElement()
                .asString()
                .endsWith(
                        " byKind[DRAFT=1/0 in:800 out:90 promptEvalMs:400 generationMs:2500 cached:600 ttftMs:400 prefill:13.3%]");
    }

    // The Proofread stage's calls are counted by kind like every other; 10 of the 55 reported no cache figure, so the
    // cached share is taken over the prompt tokens of the 45 that did (13,500 of 45,000), never over all 55.
    @Test
    void onEvent_finishedAfter55ProofreadCalls_countsThemAndTheCachedShareOverThe45ThatReportedIt() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock, ledger);
        final TokenUsage cached = new TokenUsage(1000, 50, Duration.ofMillis(900), Duration.ofMillis(100), 300);
        final TokenUsage uncounted = new TokenUsage(1000, 50, Duration.ofMillis(900), Duration.ofMillis(100), null);
        java.util.stream.IntStream.range(0, 45)
                .forEach(ignored ->
                        ledger.record(new CallRecord(CallKind.PROOFREAD, cached, Duration.ofSeconds(1), null, null)));
        java.util.stream.IntStream.range(0, 10)
                .forEach(ignored -> ledger.record(
                        new CallRecord(CallKind.PROOFREAD, uncounted, Duration.ofSeconds(1), null, null)));

        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries())
                .singleElement()
                .asString()
                .contains("PROOFREAD=55/0 ")
                .contains(" cached:13500 ");
        assertThat(CallKindTotals.of(ledger.since(0)).totals())
                .hasEntrySatisfying(
                        CallKind.PROOFREAD,
                        totals -> assertThat(totals.cachedShare()).hasValue(0.3));
    }

    // The final line says the seconds per answered call of each kind the run stores on its model; DRAFT is per segment
    // and left out, a failed call too.
    @Test
    void onEvent_finishedAfterProofreadCalls_finalLineCarriesTheSecondsPerCall() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock, ledger);

        ledger.record(new CallRecord(CallKind.DRAFT, null, Duration.ofSeconds(3), null, null));
        ledger.record(new CallRecord(CallKind.PROOFREAD, null, Duration.ofSeconds(5), null, null));
        ledger.record(new CallRecord(CallKind.PROOFREAD, null, Duration.ofSeconds(7), null, null));
        ledger.record(
                new CallRecord(CallKind.PROOFREAD, null, Duration.ofSeconds(60), null, FailureReason.UNCLASSIFIED));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries()).singleElement().asString().endsWith(" secondsPerCall[PROOFREAD=6.0]");
    }

    // The cost is the sum of what the calls reported; a run whose calls reported none prints a dash, never 0.
    @Test
    void summary_withCosts_totalsThemAndWithoutPrintsDash() {
        final RunSummaryLogger paid = new RunSummaryLogger(clock, ledger);
        ledger.record(costed(new BigDecimal("0.00042")));
        ledger.record(costed(new BigDecimal("0.00008")));
        ledger.record(costed(null));
        paid.onEvent(new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 3, 3, 0, List.of(), null)));
        final String withCost = summaries().getFirst();
        appender.list.clear();
        final CallLedgerImpl otherLedger = new CallLedgerImpl();
        final RunSummaryLogger unpaid = new RunSummaryLogger(clock, otherLedger);
        otherLedger.record(costed(null));
        unpaid.onEvent(new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 3, 3, 0, List.of(), null)));

        assertThat(withCost).contains(" cost=0.00050 ");
        assertThat(summaries().getFirst()).contains(" cost=- ");
    }

    private static CallRecord costed(final @Nullable BigDecimal cost) {
        return new CallRecord(CallKind.DRAFT, null, Duration.ofSeconds(1), null, null, cost);
    }

    // 15h.C5: a provider that reports no prompt-evaluation time leaves the first-token figures unknown, never zero.
    @Test
    void onEvent_noPromptEvalReported_firstTokenFiguresAreDashes() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock, ledger);
        final TokenUsage usage = new TokenUsage(800, 90, Duration.ofMillis(2500));

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        ledger.record(new CallRecord(CallKind.DRAFT, usage, Duration.ofSeconds(3), null, null));
        summary.onEvent(answered(Duration.ofSeconds(3), usage));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries()).singleElement().asString().endsWith(" cached:– ttftMs:– prefill:–]");
    }

    // A call that reported no time before its first token prints a dash, never 0; a call made before the job was
    // built is not the run's.
    @Test
    void finalLine_noCallReportedTtft_printsADash() {
        ledger.record(new CallRecord(CallKind.PROOFREAD, null, Duration.ofSeconds(9), null, null));
        final RunSummaryLogger summary = new RunSummaryLogger(clock, ledger);

        ledger.record(new CallRecord(
                CallKind.DRAFT, new TokenUsage(800, 90, Duration.ofMillis(2500)), Duration.ofSeconds(3), null, null));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries())
                .singleElement()
                .asString()
                .contains(" byKind[DRAFT=1/0 ")
                .doesNotContain("PROOFREAD")
                .contains("ttftMs:–")
                .contains("prefill:–");
    }

    // A night's run makes tens of thousands of calls; the line counts them all but keeps only the newest
    // RunSummaryLogger.PERCENTILE_WINDOW times, so its memory does not grow with the book.
    @Test
    void onEvent_moreCallsThanTheWindow_countsAllAndTakesThePercentileOfTheNewest() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        feed(summary, 500, Duration.ofSeconds(10));
        feed(summary, 1_000, Duration.ofSeconds(1));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries()).singleElement().asString().contains("calls=1500 avgCallMs=4000 p95CallMs=1000 ");
        assertThat(summary.heldCallTimes()).isEqualTo(1_000);
    }

    private static void feed(final RunSummaryLogger summary, final int calls, final Duration elapsed) {
        java.util.stream.IntStream.range(0, calls)
                .forEach(ignored -> summary.onEvent(
                        new ModelCallFinished("ch12.xhtml:4", CallKind.DRAFT, elapsed, null, 200, false)));
    }

    private List<String> summaries() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("run summary "))
                .toList();
    }

    private static SegmentDecided decided() {
        return new SegmentDecided("ch12.xhtml:4", SegmentStatus.ACCEPTED, null, DECIDED);
    }

    private static ModelCallFinished answered(final Duration elapsed, final TokenUsage usage) {
        return new ModelCallFinished("ch12.xhtml:4", CallKind.DRAFT, elapsed, usage, 200, false);
    }

    private static ModelCallFinished timedOut(final Duration elapsed) {
        return new ModelCallFinished(
                "ch12.xhtml:4",
                CallKind.PROOFREAD,
                elapsed,
                null,
                0,
                false,
                List.of("ch12.xhtml:4"),
                1,
                ErrorCode.timeout);
    }

    /** A clock the test moves by hand. */
    private static final class SettableClock extends Clock {

        private Instant now = Instant.parse("2026-10-01T10:00:00Z");

        void advance(final Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
