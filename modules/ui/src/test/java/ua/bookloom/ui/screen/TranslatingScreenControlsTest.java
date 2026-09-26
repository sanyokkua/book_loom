package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;

/**
 * The dashboard's buttons wired to the run: each one fired on the screen reaches the recording job the scripted engine
 * hands out, and what the job then reports comes back to the screen through the mirror.
 */
class TranslatingScreenControlsTest extends TranslatingScreenTestBase {

    private void awaitBanner(final String title) throws Exception {
        awaitFx(() -> title.equals(labelText("translating-banner-title")));
    }

    private void startRun() throws Exception {
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitBanner("Translating");
    }

    // IF the start control were not wired, THEN nothing would ever be translated from the screen.
    @Test
    void startControl_bookAndModelReady_firingItBeginsARunThroughTheModelFactoryAndEngine() throws Exception {
        startRun();

        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", MODEL));
        assertThat(engine.requests()).hasSize(1);
        assertThat(job.calls()).containsExactly("pauseAt", "subscribe", "run");
        assertThat(enabledControls()).containsExactlyInAnyOrder("translating-pause", "translating-stop");
    }

    // IF the pause control were not wired, THEN a person could not halt a run at its next boundary.
    @Test
    void pauseControl_running_firingItAsksTheJobToPauseAndTheScreenReadsAsPausing() throws Exception {
        startRun();

        onFx(() -> button("translating-pause").fire());

        assertThat(job.calls()).contains("pause");
        awaitBanner("Pausing");
        assertThat(disabledControls()).containsExactly("translating-pause");
    }

    // IF the resume control were not wired, THEN a paused run could never continue.
    @Test
    void resumeControl_paused_firingItAsksTheJobToResume() throws Exception {
        startRun();
        onFx(() -> button("translating-pause").fire());
        job.emit(new Paused(PauseReason.REQUESTED, null, new JobProgress(JobStage.TRANSLATE, 1, 2, 5, 0, 5)));
        awaitBanner("Paused");

        onFx(() -> button("translating-resume").fire());

        assertThat(job.calls()).contains("resume");
    }

    // IF the stop control were not wired, THEN a run could not be ended from the screen.
    @Test
    void stopControl_running_firingItAsksTheJobToCancelAndTheReturnedRunReadsAsStopped() throws Exception {
        startRun();

        onFx(() -> button("translating-stop").fire());

        assertThat(job.calls()).contains("cancel");
        awaitBanner("Stopping");
        job.finish(Result.ok(cancelledReport()));
        awaitBanner("Run stopped");
        assertThat(enabledControls()).containsExactly("translating-new-run");
    }
}
