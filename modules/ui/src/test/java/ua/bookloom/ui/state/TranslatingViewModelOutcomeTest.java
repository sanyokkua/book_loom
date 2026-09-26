package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.i18n.MessageKey;

/** How a run's end is announced: a completed run once, a stopped run not at all. */
class TranslatingViewModelOutcomeTest extends TranslatingViewModelTestBase {

    private static JobReport completedWithFlagged() {
        return new JobReport(BookFormat.TXT, JobState.COMPLETED, 10, 7, 3, List.of(), DESTINATION, null);
    }

    // IF a finished run raised nothing, THEN a person looking at another screen would not know it was done.
    @Test
    void run_completesWithNothingFlagged_raisesOneSuccessToastWithTheAcceptedCount() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.ok(completedReport(10)));

        awaitState(RunState.COMPLETED);
        assertThat(toasts.raised()).containsExactly(new Raised("success", MessageKey.TOAST_RUN_FINISHED, List.of(10)));
    }

    // IF a run with flagged segments got the plain success message, THEN the flagged ones would be missed.
    @Test
    void run_completesWithFlaggedSegments_raisesOneWarningToastWithBothCounts() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.ok(completedWithFlagged()));

        awaitState(RunState.COMPLETED);
        assertThat(toasts.raised())
                .containsExactly(new Raised("warning", MessageKey.TOAST_RUN_FINISHED_FLAGGED, List.of(7, 3)));
    }

    // IF a stop raised a toast or a dialog, THEN a choice the person made would be reported as if it had gone wrong.
    @Test
    void run_isStopped_raisesNoToastAndPresentsNoError() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);

        job.finish(Result.ok(cancelledReport()));

        awaitState(RunState.STOPPED);
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
    }

    // IF a failed run raised the completion toast, THEN the person would be told a broken run had finished.
    @Test
    void run_fails_raisesNoCompletionToast() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(error()));

        awaitState(RunState.FAILED);
        assertThat(toasts.raised()).isEmpty();
    }
}
