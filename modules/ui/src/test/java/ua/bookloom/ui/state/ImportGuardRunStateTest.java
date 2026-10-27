package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.dialog.RecordingOpenAnotherBookPrompt.Asked;

/**
 * What the two answers of "Open another book?" do to the translation: a running run is stopped first and waited for
 * (it can be continued from Projects later), a paused or stopped one is left as it is by "Open as a new project", and
 * "Replace" ends the run because its project is deleted.
 */
class ImportGuardRunStateTest extends ImportGuardTestBase {

    @BeforeEach
    void openFirst() {
        openFirstBook();
    }

    /** Starts the run and brings it to RUNNING, or to PAUSED for the given reason. */
    private void runUntil(final @Nullable PauseReason reason) throws Exception {
        startJob();
        awaitState(RunState.RUNNING);
        if (reason != null) {
            job.emit(new Paused(reason, reason == PauseReason.ON_ERROR ? error() : null, progress(2, 0, 7)));
            job.drain();
            awaitState(RunState.PAUSED);
        }
    }

    private void stopRun() throws Exception {
        runUntil(null);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF a finished run skipped the question, THEN the open project would be replaced without being asked about.
    @Test
    void requestImport_runCompleted_stillAsks() throws Exception {
        runUntil(null);
        job.finish(Result.ok(completedReport(9)));
        awaitState(RunState.COMPLETED);

        requestImport(DRACULA);

        assertThat(prompt.asked()).containsExactly(new Asked(OPEN_NAME, "Dracula.epub", RunState.COMPLETED));
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"RUNNING,", "PAUSED,REQUESTED", "PAUSED,ON_ERROR"},
            nullValues = "")
    void requestImport_runCanContinue_asksWithTheRunStateAndChangesNothing(
            final RunState expected, final PauseReason reason) throws Exception {
        runUntil(reason);

        requestImport(DRACULA);

        assertThat(prompt.asked()).containsExactly(new Asked(OPEN_NAME, "Dracula.epub", expected));
        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
        assertThat(state()).isEqualTo(expected);
    }

    // The run cannot keep running beside a second book, so it is stopped and waited for; Projects continues it later.
    @Test
    void openNew_runIsRunning_stopsTheRunFirstThenOpensTheBookKeepingTheProject() throws Exception {
        runUntil(null);
        requestImport(DRACULA);

        openNew();

        assertThat(job.calls().stream().filter("cancel"::equals)).hasSize(1);
        assertThat(projects.imports())
                .as("nothing opens while the run is stopping")
                .containsExactly(FRANKENSTEIN);
        assertThat(prompt.dismissals()).isZero();

        job.finish(Result.ok(cancelledReport()));
        awaitOpened();

        assertThat(prompt.dismissals()).isEqualTo(1);
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        assertThat(projects.removedProjects()).isEmpty();
    }

    // A paused run is already not running, so it stays paused and nothing is cancelled.
    @Test
    void openNew_runIsPaused_opensTheBookAtOnceAndLeavesTheRunPaused() throws Exception {
        runUntil(PauseReason.REQUESTED);
        requestImport(DRACULA);

        openNew();

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        assertThat(projects.removedProjects()).isEmpty();
        assertThat(state()).isEqualTo(RunState.PAUSED);
    }

    @Test
    void openNew_runIsStopped_opensTheBookAtOnceAndLeavesTheStoppedRunBehind() throws Exception {
        stopRun();
        requestImport(DRACULA);

        openNew();

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        // The stopped run belongs to the project left behind; the new book shows its own state.
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NONE", "REQUESTED", "ON_ERROR"},
            nullValues = "NONE")
    void replace_runCanContinue_cancelsOnceThenRemovesTheProjectAndOpensTheBook(final PauseReason reason)
            throws Exception {
        runUntil(reason);
        requestImport(DRACULA);

        replace();

        assertThat(job.calls().stream().filter("cancel"::equals)).hasSize(1);
        assertThat(projects.removedProjects())
                .as("nothing is deleted while the run stops")
                .isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);

        job.finish(Result.ok(cancelledReport()));
        awaitOpened();

        assertThat(projects.removedProjects()).containsExactly("p1");
        assertThat(projects.events()).containsSubsequence("import Dracula.epub", "remove p1");
        assertThat(prompt.dismissals()).isEqualTo(1);
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    private void awaitOpened() throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> projects.imports().size() == 2);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
