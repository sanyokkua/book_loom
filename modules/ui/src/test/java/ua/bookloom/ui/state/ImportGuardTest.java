package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt.Asked;

/**
 * Importing another book while the current translation can continue asks first, and only a confirmed answer stops
 * the run, releases its project and opens the new book. The runner, the mirror and the import view model are real;
 * the job, the project service and the question are hand-written fakes.
 */
class ImportGuardTest extends RunnerTestBase {

    private static final Path FRANKENSTEIN = Path.of("Frankenstein.epub");
    private static final Path DRACULA = Path.of("Dracula.epub");

    private ScriptedProjectService projects;
    private RecordingReplaceRunPrompt prompt;
    private ImportGuard guard;

    @BeforeEach
    void setUpGuard() {
        projects = new ScriptedProjectService();
        projects.on(FRANKENSTEIN, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(
                DRACULA, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Dracula", "Bram Stoker", 5)));
        prompt = new RecordingReplaceRunPrompt();
        final CurrentProject current = new CurrentProject();
        final ImportViewModel imports = onFx(() -> new ImportViewModel(
                projects, current, new RecordingToasts(), new RecordingErrorPresenter(), new DirectExecutor()));
        guard = new ImportGuard(mirror, runner, imports, current, prompt);
        onFx(() -> {
            imports.open(FRANKENSTEIN);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void requestImport(final Path source) {
        onFx(() -> {
            guard.requestImport(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void confirm() {
        onFx(() -> {
            prompt.confirm();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitEnded() throws TimeoutException {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> {
            final RunState now = state();
            return now == RunState.COMPLETED || now == RunState.FAILED || now == RunState.STOPPED;
        });
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

    @Test
    void requestImport_noRunExists_opensAtOnceAndNeverAsks() {
        requestImport(DRACULA);

        assertThat(prompt.asked()).isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
    }

    private void assertOverRunIsClearedWithoutAsking(final JobReport report) throws Exception {
        runUntil(null);
        job.finish(Result.ok(report));
        awaitEnded();

        requestImport(DRACULA);

        assertThat(prompt.asked()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(onFx(() -> mirror.runFileName().get())).isNull();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
    }

    // A finished run has nothing left to continue, so the import resets it without a question.
    @Test
    void requestImport_runCompleted_clearsTheRunAndOpensWithoutAsking() throws Exception {
        assertOverRunIsClearedWithoutAsking(completedReport(9));
    }

    @Test
    void requestImport_runFailed_clearsTheRunAndOpensWithoutAsking() throws Exception {
        assertOverRunIsClearedWithoutAsking(failedReport(error()));
    }

    // A pause caused by a provider error can still be resumed, so it asks like any other pause.
    @ParameterizedTest
    @CsvSource(
            value = {"RUNNING,", "PAUSED,REQUESTED", "PAUSED,ON_ERROR"},
            nullValues = "")
    void requestImport_runCanContinue_asksOnceAndImportsNothingYet(final RunState expected, final PauseReason reason)
            throws Exception {
        runUntil(reason);

        requestImport(DRACULA);

        assertThat(state()).isEqualTo(expected);
        assertThat(prompt.asked()).containsExactly(new Asked("Frankenstein.epub", "Dracula.epub", expected));
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"RUNNING,", "PAUSED,REQUESTED", "PAUSED,ON_ERROR"},
            nullValues = "")
    void confirm_runIsActive_cancelsOnceAndReplacesTheBookWhenTheJobReturns(
            final RunState expected, final PauseReason reason) throws Exception {
        runUntil(reason);
        requestImport(DRACULA);

        confirm();

        assertThat(job.calls().stream().filter("cancel"::equals)).hasSize(1);
        assertThat(projects.imports())
                .as("nothing is imported while the run is still stopping")
                .hasSize(1);
        assertThat(prompt.dismissals()).isZero();

        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.IDLE);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(prompt.dismissals()).isEqualTo(1);
        assertThat(projects.events()).containsExactly("import Frankenstein.epub", "close p1", "import Dracula.epub");
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    @Test
    void confirm_runIsStopped_cancelsNothingAndClosesThenImports() throws Exception {
        stopRun();
        requestImport(DRACULA);
        assertThat(prompt.asked()).containsExactly(new Asked("Frankenstein.epub", "Dracula.epub", RunState.STOPPED));

        confirm();

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(projects.events()).containsExactly("import Frankenstein.epub", "close p1", "import Dracula.epub");
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    // Keeping the translation is the default: no cancel, no release, no import, and the run is where it was.
    @ParameterizedTest
    @CsvSource(
            value = {"RUNNING,", "PAUSED,REQUESTED", "PAUSED,ON_ERROR"},
            nullValues = "")
    void keep_runIsActive_changesNothing(final RunState expected, final PauseReason reason) throws Exception {
        runUntil(reason);
        requestImport(DRACULA);

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(projects.events()).containsExactly("import Frankenstein.epub");
        assertThat(state()).isEqualTo(expected);
    }

    @Test
    void keep_runIsStopped_changesNothing() throws Exception {
        stopRun();

        requestImport(DRACULA);

        assertThat(projects.events()).containsExactly("import Frankenstein.epub");
        assertThat(state()).isEqualTo(RunState.STOPPED);
        assertThat(List.copyOf(prompt.asked())).hasSize(1);
    }
}
