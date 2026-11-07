package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Node;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.TooltipProbe;

/**
 * The title bar's stage chip follows the run's events (app-shell "Show the run in the title bar while one exists"):
 * Prepare, then Translate, then Proofread with its round at Max, and it is gone once the run has ended. The events
 * reach the chip the way a run's do: through the job's listener, the run session and the mirror.
 */
class RunStageChipTest extends TranslatingScreenTestBase {

    private static final String CHIP = "shell-run-stage";

    private void started() throws Exception {
        resizeScene(1440, 700);
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
    }

    private void stage(final JobStage stage) {
        job.emit(new StageStarted(stage, ProgressFixtures.progress(1, 3, 4, 0, 6)));
    }

    private String chip() {
        return textOf(CHIP);
    }

    private boolean chipShown() {
        final Node chip = required(CHIP);
        return chip.isVisible() && chip.isManaged();
    }

    // IF the chip did not move with the run, THEN a person on another screen would not know Proofread has begun.
    @Test
    void chip_followsTheStagesAndNamesMaxsRound_thenHidesAfterCompleted() throws Exception {
        started();

        stage(JobStage.PREP);
        awaitFx(() -> "Prepare".equals(chip()));
        job.emit(new ProofreadForecast(ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH, 3));
        stage(JobStage.TRANSLATE);
        awaitFx(() -> "Translate".equals(chip()));
        stage(JobStage.PROOFREAD);
        awaitFx(() -> "Proofread".equals(chip()));
        job.emit(new ProofreadRoundStarted(2, 2, 3, 3));
        awaitFx(() -> "Proofread · round 2 of 2: whole book".equals(chip()));
        assertThat(chipShown()).isTrue();
        assertThat(TooltipProbe.tipText(required(CHIP))).startsWith("The stage the run is in");

        job.finish(Result.ok(completedReport()));
        awaitFx(() -> !chipShown());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(chipShown()).isFalse();
    }

    // IF Fast's Proofread step looked like any other, THEN a person would wait for model calls that never come.
    @Test
    void chip_fastProofread_saysMarksForReviewAndNoModelCalls() throws Exception {
        started();

        job.emit(new ProofreadForecast(ProofreadCoverage.NONE, 0));
        stage(JobStage.PROOFREAD);

        awaitFx(() -> "Proofread · marks for review, no model calls".equals(chip()));
        releaseJob();

        assertThat(chip()).isEqualTo("Proofread · marks for review, no model calls");
    }
}
