package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgress.Step;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.LiveCallFixtures;

/** An export is a blocking, stoppable activity that shows the step and the call count its job announces. */
class ExportViewModelProgressTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private ExecutorService worker;

    @BeforeEach
    void openTheBook() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        exportService.writeFiles(true);
        worker = Executors.newSingleThreadExecutor();
        exports = onFx(() -> newExports(worker));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void stopTheWorker() {
        worker.shutdownNow();
    }

    private void export() throws Exception {
        onFx(() -> {
            exports.export();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> onFx(() -> !activities.running().isEmpty()));
    }

    // IF an Error thrown by the export (a stack overflow deep in a pass) left the activity registered, THEN the scrim
    // and
    // the disabled navigation would stay until the app was closed.
    @Test
    void export_jobThrowsAnError_theActivityEndsAndTheFailureIsShown() throws Exception {
        exportService.throwOnRun(new StackOverflowError("deep"));
        // The Error goes on to the worker thread's uncaught handler, as in the application, where it is logged.
        final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
        final ExecutorService dying = Executors.newSingleThreadExecutor(work -> {
            final Thread thread = new Thread(work, "export-error-test");
            thread.setUncaughtExceptionHandler((where, error) -> uncaught.add(error));
            return thread;
        });
        try {
            exports = onFx(() -> newExports(dying));
            onFx(() -> {
                exports.export();
                return null;
            });
            WaitForAsyncUtils.waitFor(
                    5,
                    TimeUnit.SECONDS,
                    () -> onFx(() -> !exports.failure().get().isEmpty()));

            assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
            assertThat(onFx(() -> activities.blocking().get())).isFalse();
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !uncaught.isEmpty());
            assertThat(uncaught).singleElement().isInstanceOf(StackOverflowError.class);
        } finally {
            dying.shutdownNow();
        }
    }

    // IF the job's announcements never reached the activity, THEN its dialog would stay on its first words.
    @Test
    void export_jobAnnouncesTheWritingStep_theActivityShowsItsWordsAndTheFile() throws Exception {
        exportService.announce(new ExportProgress(Step.WRITING, 0, 1));
        exportService.holdRuns();

        export();
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> onFx(() ->
                        "Write the file".equals(activities.running().getFirst().stepText())));

        final Activity shown = onFx(() -> activities.running().getFirst());
        assertThat(shown.kind()).isEqualTo(ActivityKind.EXPORT);
        assertThat(shown.cancellable()).isTrue();
        assertThat(shown.details()).extracting(Activity.Detail::value).contains("Frankenstein.uk.epub");
        exportService.release();
    }

    // IF the pass's calls never reached the activity, THEN the busy card could not show what the model is asked.
    @Test
    void export_jobShowsACall_theActivityCarriesItForTheCallView() throws Exception {
        exportService.announceCalls(LiveCallFixtures.waiting(3, 1, "Two.", LiveCallFixtures.smallPrompt()));
        exportService.holdRuns();

        export();
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> onFx(() -> activities.running().getFirst().calls().current() != null));

        final Activity shown = onFx(() -> activities.running().getFirst());
        assertThat(shown.calls().current())
                .isNotNull()
                .extracting(CallSnapshot::callId)
                .isEqualTo(3L);
        exportService.release();
    }

    // IF Stop did not reach the job, THEN a long consistency pass could not be abandoned.
    @Test
    void stop_whileExporting_cancelsTheJobAndTheActivityEnds() throws Exception {
        exportService.holdRuns();
        export();

        onFx(() -> {
            activities.stop(activities.running().getFirst().id());
            return null;
        });
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> onFx(() -> activities.running().isEmpty()));

        assertThat(exportService.cancels()).isEqualTo(1);
        assertThat(onFx(() -> exports.outcome().get())).isNull();
    }
}
