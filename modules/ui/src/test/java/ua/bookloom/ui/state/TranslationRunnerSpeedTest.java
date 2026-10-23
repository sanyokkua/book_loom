package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.ui.ScriptedModelSettings;

/** What a run tells the model's stored speed when it ends: the mean time of its drafted segments, or nothing. */
class TranslationRunnerSpeedTest extends RunnerTestBase {

    private static final Instant T0 = Instant.parse("2026-10-23T09:00:00Z");

    /** A clock the test moves by hand. */
    private static final class SteppedClock extends Clock {
        private volatile Instant now = T0;

        void advance(final Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final SteppedClock clock = new SteppedClock();
    private final RecordingRunSpeedSink sink = new RecordingRunSpeedSink();

    private void runnerWith(final RunSpeedSink target) {
        runner = new TranslationRunner(mirror, executor, ticks, desk, clock, target);
    }

    private void draftTook(final long seconds) throws InterruptedException {
        clock.advance(Duration.ofSeconds(seconds));
        job.emit(new StepChanged(StepKind.DRAFT, "draft", 0, StepState.ENDED, null, StepResult.DONE));
        job.drain();
    }

    @Test
    void run_completedWithDrafts_measuredOnRunModel() throws Exception {
        runnerWith(sink);
        startJob();

        draftTook(2);
        draftTook(4);
        job.finish(Result.ok(completedReport(2)));
        awaitState(RunState.COMPLETED);

        assertThat(sink.seen()).containsExactly(new RecordingRunSpeedSink.Measured(SELECTION, Duration.ofSeconds(3)));
    }

    @Test
    void run_cancelledBeforeAnyDraft_nothingMeasured() throws Exception {
        runnerWith(sink);
        startJob();

        clock.advance(Duration.ofSeconds(5));
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        assertThat(sink.seen()).isEmpty();
    }

    @Test
    void run_stoppedAfterDrafts_measured() throws Exception {
        runnerWith(sink);
        startJob();

        draftTook(5);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        assertThat(sink.seen()).containsExactly(new RecordingRunSpeedSink.Measured(SELECTION, Duration.ofSeconds(5)));
    }

    @Test
    void run_afterTestSpeed_runSpeedReplacesTest() throws Exception {
        final ScriptedModelSettings store = new ScriptedModelSettings();
        store.measured(SELECTION.providerId(), SELECTION.modelId(), 3.0, MeasuredFrom.TEST);
        final ModelSpeeds speeds = new ModelSpeeds();
        runnerWith(RunSpeedSink.storingIn(new ModelSpeedRecorder(store, speeds, executor)));
        startJob();

        draftTook(8);
        job.finish(Result.ok(completedReport(1)));
        awaitState(RunState.COMPLETED);
        waitForRevision(speeds);

        final var row =
                store.stored(SELECTION.providerId(), SELECTION.modelId()).orElseThrow();
        assertThat(row.measuredSecondsPerSegment()).isEqualTo(8.0);
        assertThat(row.measuredFrom()).isEqualTo(MeasuredFrom.RUN);
    }

    private static void waitForRevision(final ModelSpeeds speeds) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> speeds.revision().get()) > 0);
    }
}
