package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgress.Step;
import ua.bookloom.ui.BookFixtures;

/** An export is a blocking, stoppable activity that shows the step and the call count its job announces. */
class ExportViewModelProgressTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private ExecutorService worker;

    @BeforeEach
    void openTheBook() throws IOException {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        Files.write(dir.resolve("Frankenstein.uk.epub"), new byte[1]);
        worker = Executors.newSingleThreadExecutor();
        exports = onFx(() -> newExports(worker));
        setOverwrite(true);
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

    // IF the job's announcements never reached the activity, THEN the busy card would spin through a long pass.
    @Test
    void export_jobAnnouncesACountedStep_theActivityShowsFractionStepAndCallCount() throws Exception {
        exportService.announce(new ExportProgress(Step.CONSISTENCY_NEIGHBOUR, 1, 4));
        exportService.holdRuns();

        export();
        WaitForAsyncUtils.waitFor(
                5,
                TimeUnit.SECONDS,
                () -> onFx(() -> activities.running().getFirst().fraction() != null));

        final Activity shown = onFx(() -> activities.running().getFirst());
        assertThat(shown.kind()).isEqualTo(ActivityKind.EXPORT);
        assertThat(shown.cancellable()).isTrue();
        assertThat(shown.fraction()).isEqualTo(0.25);
        assertThat(shown.stepText()).isEqualTo("Checking paragraphs against their neighbours");
        assertThat(shown.details()).extracting(Activity.Detail::value).contains("1 of 4", "Frankenstein.uk.epub");
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
