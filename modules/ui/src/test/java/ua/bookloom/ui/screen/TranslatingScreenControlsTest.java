package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.TooltipProbe;

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
        assertThat(job.calls()).containsExactly("pauseAt", "recoverWith", "subscribe", "run");
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
        job.emit(new Paused(PauseReason.REQUESTED, null, ProgressFixtures.progress(1, 2, 5, 0, 5)));
        awaitBanner("Paused");

        onFx(() -> button("translating-resume").fire());

        assertThat(job.calls()).contains("resume");
    }

    // IF the stop control were not wired, THEN a run could not be ended from the screen; it asks first, and only the
    // confirming button reaches the job.
    @Test
    void stopControl_runningAndConfirmed_asksTheJobToCancelAndTheReturnedRunReadsAsStopped() throws Exception {
        startRun();

        onFx(() -> button("translating-stop").fire());
        final List<String> whileAsking = List.copyOf(job.calls());
        onFx(() -> button("confirm-yes").fire());

        assertThat(whileAsking).doesNotContain("cancel");
        assertThat(job.calls()).contains("cancel");
        awaitBanner("Stopping");
        job.finish(Result.ok(cancelledReport()));
        awaitBanner("Run stopped");
        assertThat(enabledControls()).containsExactly("translating-resume");
    }

    // IF Cancel stopped the run anyway, THEN a stray press would end hours of work.
    @Test
    void stopQuestion_cancelPressed_leavesTheRunTranslating() throws Exception {
        startRun();
        onFx(() -> button("translating-stop").fire());

        onFx(() -> button("confirm-cancel").fire());

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(scene.getRoot().lookup("#confirm-card")).isNull();
        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
    }

    // IF Escape stopped the run, THEN backing out of the question by reflex would end it.
    @Test
    void stopQuestion_escapePressed_closesItAndLeavesTheRunTranslating() throws Exception {
        startRun();
        onFx(() -> button("translating-stop").fire());

        onFx(() -> scene.getRoot()
                .lookup("#confirm-card")
                .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(job.calls()).doesNotContain("cancel");
        assertThat(scene.getRoot().lookup("#confirm-card")).isNull();
        assertThat(labelText("translating-banner-title")).isEqualTo("Translating");
    }

    // IF Enter confirmed the stop, THEN the dangerous answer would be the easy one.
    @Test
    void stopQuestion_asked_makesCancelTheDefaultButtonWithTooltipsOnBoth() throws Exception {
        startRun();

        onFx(() -> button("translating-stop").fire());

        assertThat(button("confirm-cancel").isDefaultButton()).isTrue();
        assertThat(button("confirm-yes").isDefaultButton()).isFalse();
        assertThat(TooltipProbe.tipText(button("confirm-yes"))).isNotBlank();
        assertThat(TooltipProbe.tipText(button("confirm-cancel"))).isNotBlank();
    }
}
