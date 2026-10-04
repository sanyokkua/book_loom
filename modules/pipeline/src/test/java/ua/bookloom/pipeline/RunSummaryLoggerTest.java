package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
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

/**
 * The run summary: one INFO line a minute while a job runs and one when it ends, with the counts, call times and
 * current segment a shared log needs. The clock is moved by the test, so nothing sleeps.
 */
class RunSummaryLoggerTest {

    private static final JobProgress START = new JobProgress(JobStage.TRANSLATE, 1, 3, 0, 0, 16);
    private static final JobProgress DECIDED = new JobProgress(JobStage.TRANSLATE, 1, 3, 5, 1, 10, 1, 4, 2, 1, 2);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SettableClock clock = new SettableClock();
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
                        + " p95CallMs=0 tokensPerSecond=- timeouts=0 current=-");
    }

    // The final line says per kind what the calls cost, so a shared log shows where the time went.
    @Test
    void onEvent_finishedAfterCalls_finalLineCarriesTheTotalsOfEachKind() {
        final RunSummaryLogger summary = new RunSummaryLogger(clock);

        summary.onEvent(new StageStarted(JobStage.TRANSLATE, START));
        summary.onEvent(answered(
                Duration.ofSeconds(3), new TokenUsage(800, 90, Duration.ofMillis(2500), Duration.ofMillis(400), 600)));
        summary.onEvent(
                new Finished(new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 16, 5, 1, List.of(), null)));

        assertThat(summaries())
                .singleElement()
                .asString()
                .endsWith(" byKind[DRAFT=1/0 in:800 out:90 promptEvalMs:400 generationMs:2500 cached:600]");
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
                "ch12.xhtml:4", CallKind.JUDGE, elapsed, null, 0, false, List.of("ch12.xhtml:4"), 1, ErrorCode.timeout);
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
