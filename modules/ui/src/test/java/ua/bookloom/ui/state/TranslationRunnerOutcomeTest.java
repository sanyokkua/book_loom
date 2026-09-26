package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.StageStarted;

/** The returned result, never an event, decides how a run ends. */
class TranslationRunnerOutcomeTest extends RunnerTestBase {

    // ---- the returned result decides the outcome ------------------------------------------------------------------

    // IF a completed job did not report itself finished, THEN the export screen would have no report to show.
    @Test
    void run_returnsCompleted_endsCompletedWithTheReportAndAFinishedMilestone() throws Exception {
        final JobReport report = completedReport(2);
        startJob();
        job.emit(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 2)));
        job.emit(decided("s-1", SegmentStatus.ACCEPTED, progress(1, 0, 1)));
        job.emit(decided("s-2", SegmentStatus.ACCEPTED, progress(2, 0, 0)));

        job.finish(Result.ok(report));
        awaitState(RunState.COMPLETED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.COMPLETED);
        assertThat(onFx(() -> mirror.report().get())).isSameAs(report);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(2);
        assertThat(onFx(() -> mirror.remaining().get())).isZero();
        assertThat(logEntries())
                .containsExactly(
                        log(LogKind.MILESTONE, "stageStarted"),
                        log(LogKind.ACCEPTED, "s-1"),
                        log(LogKind.ACCEPTED, "s-2"),
                        log(LogKind.MILESTONE, "finished"));
    }

    // IF a cancelled run looked failed, THEN a person who pressed Stop would be shown an error.
    @Test
    void run_returnsCancelled_endsStoppedWithNoFailureAndTheReport() throws Exception {
        final JobReport report = cancelledReport();
        startJob();
        emitAccepted(job, 1, 3);

        job.finish(Result.ok(report));
        awaitState(RunState.STOPPED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPED);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.report().get())).isSameAs(report);
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(3);
        assertThat(logEntries()).doesNotContain(log(LogKind.MILESTONE, "finished"));
    }

    // IF a cancelled error result were shown as a failure, THEN a stop the person chose would read as a run that went
    // wrong.
    @Test
    void run_returnsErrCancelled_endsStoppedWithNoFailureAndNoReport() throws Exception {
        startJob();
        emitAccepted(job, 1, 3);

        job.finish(Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The run was stopped.")));
        awaitState(RunState.STOPPED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPED);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.report().get())).isNull();
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(3);
    }

    // IF the outcome came from the Finished event, THEN a run refused before it started (which sends none) would sit
    // in the running state forever.
    @Test
    void run_returnsErrWithoutAFinishedEvent_leavesRunningAndEndsFailedWithTheError() throws Exception {
        final AppError refusal = error();
        startJob();
        job.drain();
        assertThat(state()).isEqualTo(RunState.RUNNING);

        job.finish(Result.err(refusal));
        awaitState(RunState.FAILED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.FAILED);
        assertThat(onFx(() -> mirror.failure().get())).isSameAs(refusal);
        assertThat(onFx(() -> mirror.report().get())).isNull();
    }

    // IF the Finished event could decide the state, THEN an event and a returned error would disagree; the returned
    // result is the authority.
    @Test
    void run_finishedEventThenReturnedErr_theReturnedResultDecides() throws Exception {
        final AppError refusal = error();
        startJob();
        job.emit(new Finished(completedReport(1)));

        job.finish(Result.err(refusal));
        awaitState(RunState.FAILED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.FAILED);
        assertThat(onFx(() -> mirror.failure().get())).isSameAs(refusal);
    }

    // IF a failure were reported as the last progress instead of the report's own error, THEN the person would see the
    // wrong code and the counts reached before it would be lost.
    @Test
    void run_progressThenAFailedReport_endsFailedWithTheReportsErrorAndKeepsTheCounts() throws Exception {
        final AppError failure = error();
        startJob();
        job.emit(decided("s-1", SegmentStatus.ACCEPTED, progress(10, 3, 7)));

        job.finish(Result.ok(failedReport(failure)));
        awaitState(RunState.FAILED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.FAILED);
        assertThat(onFx(() -> mirror.failure().get())).isSameAs(failure);
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(10);
        assertThat(onFx(() -> mirror.flagged().get())).isEqualTo(3);
        assertThat(onFx(() -> mirror.remaining().get())).isEqualTo(7);
    }

    static Stream<Arguments> throwingEnds() {
        return Stream.of(
                Arguments.of(Named.<Consumer<RecordingJob>>of(
                        "RuntimeException", j -> j.finishThrowing(new IllegalStateException("boom")))),
                Arguments.of(
                        Named.<Consumer<RecordingJob>>of("Error", j -> j.finishThrowing(new AssertionError("fatal")))));
    }

    // IF a throwable escaping run() were not caught, THEN the run would stay "running" forever with no error shown.
    @ParameterizedTest
    @MethodSource("throwingEnds")
    void run_throws_endsFailedWithAnInternalError(final Consumer<RecordingJob> endTheRun) throws Exception {
        startJob();
        job.drain();

        endTheRun.accept(job);
        awaitState(RunState.FAILED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.FAILED);
        final AppError failure = onFx(() -> mirror.failure().get());
        assertThat(failure).isNotNull();
        assertThat(failure.code()).isEqualTo(ErrorCode.internal);
    }
}
