package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;

/** Starting a run, refusing a second one, and coalescing engine events onto the cadence. */
class TranslationRunnerTest extends RunnerTestBase {

    // IF start did not publish the running state, THEN the screen would never leave its idle presentation.
    @Test
    void start_idleRunner_returnsTrueAndPublishesRunning() throws Exception {
        startJob();

        assertThat(state()).isEqualTo(RunState.RUNNING);
        assertThat(ticks.starts()).isEqualTo(1);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF the listener were subscribed after run() began, THEN the first events of a fast job would be lost.
    @Test
    void start_job_subscribesBeforeRunBeginsAndRunsOffTheFxThread() throws Exception {
        final AtomicReference<Thread> fxThread = new AtomicReference<>();
        onFx(() -> {
            fxThread.set(Thread.currentThread());
            return null;
        });

        startJob();

        assertThat(job.wasSubscribedBeforeRun()).isTrue();
        assertThat(job.runThread()).isNotNull().isNotSameAs(fxThread.get()).isNotSameAs(Thread.currentThread());
        assertThat(job.runThread().getName()).isEqualTo("runner-test-job");
        assertThat(job.calls()).startsWith("subscribe", "run");
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF a second run could start while one is active, THEN two jobs would drive one mirror and one model.
    @Test
    void start_whileARunIsActive_returnsFalseAndNeverTouchesTheSecondJob() throws Exception {
        final RecordingJob second = new RecordingJob();
        startJob();

        final boolean accepted = runner.start(second, REQUEST, SELECTION);

        assertThat(accepted).isFalse();
        assertThat(second.calls()).isEmpty();
        assertThat(state()).isEqualTo(RunState.RUNNING);
        assertThat(ticks.starts()).isEqualTo(1);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF a finished run kept the runner busy, THEN "start a new run" after a stop or a failure would be refused.
    @Test
    void start_afterATerminalRun_isAcceptedAndResetsTheMirror() throws Exception {
        startJob();
        emitAccepted(job, 1, 3);
        job.finish(Result.ok(completedReport(3)));
        awaitState(RunState.COMPLETED);
        final RecordingJob next = new RecordingJob();

        final boolean accepted = runner.start(next, REQUEST, SELECTION);
        next.awaitRunStarted();

        assertThat(accepted).isTrue();
        assertThat(state()).isEqualTo(RunState.RUNNING);
        assertThat(onFx(() -> mirror.accepted().get())).isZero();
        assertThat(onFx(() -> mirror.report().get())).isNull();
        assertThat(logEntries()).isEmpty();
        next.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // ---- coalescing -----------------------------------------------------------------------------------------------

    // IF events published straight through, THEN a fast engine would flood the FX thread on every segment.
    @Test
    void progressAndLog_eventsBetweenTicks_reachTheMirrorOnlyOnATick() throws Exception {
        startJob();
        job.emit(decided("s-1", SegmentStatus.ACCEPTED, progress(1, 0, 4)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.accepted().get())).isZero();
        assertThat(logEntries()).isEmpty();

        ticks.fire();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(1);
        assertThat(onFx(() -> mirror.remaining().get())).isEqualTo(4);
        assertThat(onFx(() -> mirror.total().get())).isEqualTo(5);
        assertThat(logEntries()).containsExactly(log(LogKind.ACCEPTED, "s-1"));
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF each of five thousand segments published, THEN the window would stall; ten ticks plus the terminal publish
    // is the whole budget, and the final counts must still be exact.
    @Test
    void segmentBurst_fiveThousandEventsTenTicks_atMostElevenPublishesAndExactFinalCounts() throws Exception {
        startJob();
        emitTenBatchesWithATickAfterEach();
        emitAccepted(job, TICKS * BATCH + 1, BURST);
        job.finish(Result.ok(completedReport(BURST)));
        awaitState(RunState.COMPLETED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(acceptedChanges.get()).isBetween(TICKS, TICKS + 1);
        assertThat(remainingChanges.get()).isBetween(TICKS, TICKS + 1);
        assertThat(flaggedChanges.get()).isZero();
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(5000);
        assertThat(onFx(() -> mirror.remaining().get())).isZero();
        assertThat(onFx(() -> mirror.total().get())).isEqualTo(5000);
        assertThat(onFx(() -> mirror.progressFraction().get())).isEqualTo(1.0);
    }

    private void emitTenBatchesWithATickAfterEach() {
        IntStream.range(0, TICKS).forEach(batch -> {
            emitAccepted(job, batch * BATCH + 1, (batch + 1) * BATCH);
            tickAfterDelivery();
        });
    }

    private void tickAfterDelivery() {
        try {
            deliverAndTick();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while driving the cadence", cause);
        }
    }

    // IF the burst were dropped from the log instead of bounded, THEN the newest lines would be missing; the tail must
    // hold the newest segment and the closing milestone, in order.
    @Test
    void segmentBurst_fiveThousandEvents_logKeepsTheNewestFiveHundredInOrder() throws Exception {
        startJob();
        emitAccepted(job, 1, BURST);
        job.finish(Result.ok(completedReport(BURST)));
        awaitState(RunState.COMPLETED);
        WaitForAsyncUtils.waitForFxEvents();

        final List<LogEntry> entries = logEntries();
        assertThat(entries).hasSize(500);
        assertThat(entries.get(498)).isEqualTo(log(LogKind.ACCEPTED, "s-5000"));
        assertThat(entries.get(497)).isEqualTo(log(LogKind.ACCEPTED, "s-4999"));
        assertThat(entries.get(499)).isEqualTo(log(LogKind.MILESTONE, "finished"));
        assertThat(entries.get(0)).isEqualTo(log(LogKind.ACCEPTED, "s-4502"));
    }

    // IF the ticker outlived the run, THEN a stale timer thread would keep publishing after the terminal state.
    @Test
    void terminalOutcome_stopsTheCadence() throws Exception {
        startJob();
        assertThat(ticks.stops()).isZero();

        job.finish(Result.ok(completedReport(1)));
        awaitState(RunState.COMPLETED);

        assertThat(ticks.stops()).isEqualTo(1);
    }
}
