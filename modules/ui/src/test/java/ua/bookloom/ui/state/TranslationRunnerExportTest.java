package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ExportRequest;

/** A completed run is followed by the export to the chosen destination, and only a completed run. */
class TranslationRunnerExportTest extends RunnerTestBase {

    // IF a completed run were not exported, THEN the window would report a finished book that was never written.
    @Test
    void run_completed_exportsTheProjectToTheChosenDestinationAndPublishesTheFile() throws Exception {
        startJob();

        job.finish(Result.ok(completedReport(3)));
        awaitState(RunState.COMPLETED);

        assertThat(exports.requests())
                .containsExactly(new ExportRequest(PROJECT_ID, DESTINATION, false, Set.of(), false));
        assertThat(exportedFile()).isEqualTo(DESTINATION);
    }

    // IF a failed export still ended the run as completed, THEN the person would be told a book exists that does not.
    @Test
    void run_completedButExportFails_endsFailedWithTheExportErrorAndNoFile() throws Exception {
        exports.failWith(AppError.of(ErrorCode.validation, "This destination already exists", "Allow replacing it."));
        startJob();

        job.finish(Result.ok(completedReport(3)));
        awaitState(RunState.FAILED);

        assertThat(onFx(() -> mirror.failure().get()))
                .extracting(AppError::code, AppError::title)
                .containsExactly(ErrorCode.validation, "This destination already exists");
        assertThat(exportedFile()).isNull();
    }

    // IF a stopped run were exported, THEN Stop would still write a half-translated book.
    @Test
    void run_cancelled_writesNothing() throws Exception {
        startJob();

        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        assertThat(exports.requests()).isEmpty();
        assertThat(exportedFile()).isNull();
    }

    // IF the previous run's file stayed on the mirror, THEN the export screen would show it for a run that wrote none.
    @Test
    void publishRunStarted_afterAnExportedRun_clearsTheFile() throws Exception {
        startJob();
        job.finish(Result.ok(completedReport(3)));
        awaitState(RunState.COMPLETED);

        mirror.publishRunStarted("Book.epub");
        awaitState(RunState.RUNNING);

        assertThat(exportedFile()).isNull();
    }

    private @Nullable Path exportedFile() {
        return onFx(() -> mirror.exportedFile().get());
    }
}
