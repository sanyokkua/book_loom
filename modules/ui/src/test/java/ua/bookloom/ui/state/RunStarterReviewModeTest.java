package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.ui.ScriptedTranslationEngine;

/** The review mode is read each time a run starts, and a run in progress keeps the mode it began with. */
class RunStarterReviewModeTest extends TranslatingViewModelTestBase {

    // IF the mode were fixed when the window was built, THEN a change in Settings would never reach the next run:
    // the resumed run must ask for Manual with Manual's own pause points, while the first run stays Unattended.
    @Test
    void resume_modeChangedWhileStopped_nextRunUsesTheNewModeAndItsPausePoints() throws Exception {
        final RecordingJob second = new RecordingJob();
        engine = ScriptedTranslationEngine.returning(job, second);
        reviewMode = ReviewMode.UNATTENDED;
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        reviewMode = ReviewMode.MANUAL;
        press(viewModel::resume);
        queued.runAll();
        second.awaitRunStarted();

        assertThat(engine.requests())
                .containsExactly(
                        new RunRequest("p1", ReviewMode.UNATTENDED, null, MODEL),
                        new RunRequest("p1", ReviewMode.MANUAL, null, MODEL));
        assertThat(job.pausePoints()).isEqualTo(Set.of(PausePoint.ON_ERROR));
        assertThat(second.pausePoints())
                .isEqualTo(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.ON_FLAGGED, PausePoint.ON_ERROR));
    }

    // IF a change of the stored mode reached a run already going, THEN the title bar and the pauses would disagree
    // with what the run was started as; the mirror keeps the mode the run began with.
    @Test
    void start_modeChangesMidRun_runInProgressKeepsItsMode() throws Exception {
        reviewMode = ReviewMode.UNATTENDED;
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        reviewMode = ReviewMode.MANUAL;
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.runMode().get().reviewMode())).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(job.pausePoints()).isEqualTo(Set.of(PausePoint.ON_ERROR));
    }

    // IF the ready card kept the launch-time mode, THEN it would name a mode the next run will not use.
    @Test
    void reviewMode_providerChanges_nameTheCurrentModeForTheReadyCard() {
        reviewMode = ReviewMode.UNATTENDED;
        openBookAndChooseModel();
        buildViewModel();

        reviewMode = ReviewMode.ASSISTED;

        assertThat(onFx(() -> viewModel.reviewMode())).isEqualTo(ReviewMode.ASSISTED);
    }
}
