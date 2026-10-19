package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt.Asked;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt.Earlier;

/**
 * Importing a file whose stored project already holds decided segments asks "continue the earlier translation or
 * start over" instead of resetting them silently. The guard, the runner, the mirror and the import view model are
 * real; the project service and the question are hand-written fakes.
 */
class ImportGuardEarlierWorkTest extends RunnerTestBase {

    private static final Path FRANKENSTEIN = Path.of("Frankenstein.epub");
    private static final Path DRACULA = Path.of("Dracula.epub");

    private ScriptedProjectService projects;
    private RecordingReplaceRunPrompt prompt;
    private CurrentProject current;
    private ImportViewModel imports;
    private ImportGuard guard;
    private ExecutorService lookups;

    @BeforeEach
    void setUpGuard() {
        projects = new ScriptedProjectService();
        projects.on(FRANKENSTEIN, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(
                DRACULA, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Dracula", "Bram Stoker", 5)));
        prompt = new RecordingReplaceRunPrompt();
        current = new CurrentProject();
        imports = onFx(() -> new ImportViewModel(
                projects,
                current,
                new RecordingToasts(),
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                new ActivityTracker(new StateMirror())));
        lookups = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "decided-lookup-test");
            thread.setDaemon(true);
            return thread;
        });
        guard = new ImportGuard(mirror, runner, imports, current, prompt, projects, lookups);
        onFx(() -> {
            imports.open(FRANKENSTEIN);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void closeLookups() {
        lookups.shutdownNow();
    }

    private void requestImport(final Path source) throws TimeoutException {
        onFx(() -> {
            guard.requestImport(source);
            return null;
        });
        settle();
    }

    // The lookup runs on a real thread and answers through the FX queue, so wait until nothing more is in flight.
    private void settle() throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !projects.decidedAsked().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void answer(final Runnable choice) {
        onFx(() -> {
            choice.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String openProjectId() {
        return onFx(() -> current.book().get().projectId());
    }

    // IF the question were skipped, THEN importing the file again would reset seven decided segments with no warning.
    @Test
    void requestImport_fileHasDecidedSegments_asksAndOpensNothingYet() throws Exception {
        projects.onDecided(DRACULA, 7);

        requestImport(DRACULA);

        assertThat(prompt.earlier()).containsExactly(new Earlier("Dracula.epub", 7));
        assertThat(prompt.asked()).isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
        assertThat(projects.continues()).isEmpty();
        assertThat(openProjectId()).isEqualTo("p1");
    }

    // IF Continue re-imported the file, THEN the stored decisions would be reset and the question would be a lie.
    @Test
    void continueChosen_keepsTheStoredBookAndNeverReimports() throws Exception {
        projects.onDecided(DRACULA, 7);
        requestImport(DRACULA);

        answer(prompt::continueEarlier);

        assertThat(projects.continues()).containsExactly(DRACULA);
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
        assertThat(openProjectId()).isEqualTo("p2");
    }

    @Test
    void startOverChosen_importsAgainAndNeverContinues() throws Exception {
        projects.onDecided(DRACULA, 7);
        requestImport(DRACULA);

        answer(prompt::startOver);

        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        assertThat(projects.continues()).isEmpty();
        assertThat(openProjectId()).isEqualTo("p2");
    }

    // IF closing the card without a choice imported anyway, THEN Escape would discard the work it was meant to protect.
    @Test
    void cancelled_noChoiceMade_leavesTheOpenBookAndTheStoredWorkAlone() throws Exception {
        projects.onDecided(DRACULA, 7);

        requestImport(DRACULA);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
        assertThat(projects.continues()).isEmpty();
        assertThat(openProjectId()).isEqualTo("p1");
    }

    @Test
    void requestImport_fileNeverImportedOrNothingDecided_opensWithoutAsking() throws Exception {
        requestImport(DRACULA);

        assertThat(prompt.earlier()).isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        assertThat(projects.continues()).isEmpty();
    }

    // IF a failing lookup stopped the import, THEN a book could not be opened because of a side question.
    @Test
    void requestImport_lookupFails_opensWithoutAsking() throws Exception {
        projects.failDecidedWith(AppError.of(ErrorCode.internal, "Unexpected error", "The count could not be read."));

        requestImport(DRACULA);

        assertThat(prompt.earlier()).isEmpty();
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
    }

    // IF the lookup ran on the FX thread, THEN a slow store would freeze the window while the person only chose a file.
    @Test
    void requestImport_lookup_neverRunsOnTheFxThread() throws Exception {
        projects.onDecided(DRACULA, 2);

        requestImport(DRACULA);

        assertThat(projects.decidedAsked()).containsExactly(DRACULA);
        assertThat(projects.decidedCallsOnFxThread()).containsExactly(false);
    }

    // A finished run is cleared only when the import goes ahead; backing out of the question leaves it showing.
    @Test
    void requestImport_runCompletedAndFileHasDecidedSegments_clearsTheRunOnlyAfterTheChoice() throws Exception {
        startJob();
        awaitState(RunState.RUNNING);
        job.finish(Result.ok(completedReport(9)));
        awaitState(RunState.COMPLETED);
        projects.onDecided(DRACULA, 3);

        requestImport(DRACULA);

        assertThat(prompt.earlier()).containsExactly(new Earlier("Dracula.epub", 3));
        assertThat(state()).isEqualTo(RunState.COMPLETED);

        answer(prompt::continueEarlier);

        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(projects.continues()).containsExactly(DRACULA);
    }

    // Replacing a live run asks its own question first; the file's earlier work is asked about once that is confirmed.
    @Test
    void confirm_stoppedRunAndFileHasDecidedSegments_thenAsksAboutTheEarlierTranslation() throws Exception {
        startJob();
        awaitState(RunState.RUNNING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
        projects.onDecided(DRACULA, 4);
        onFx(() -> {
            guard.requestImport(DRACULA);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(prompt.asked()).containsExactly(new Asked("Frankenstein.epub", "Dracula.epub", RunState.STOPPED));

        answer(prompt::confirm);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !prompt.earlier().isEmpty());

        assertThat(prompt.earlier()).containsExactly(new Earlier("Dracula.epub", 4));
        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
    }
}
