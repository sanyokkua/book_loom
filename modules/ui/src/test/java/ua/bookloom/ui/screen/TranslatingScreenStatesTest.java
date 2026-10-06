package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.Throughput;

/**
 * The Translating screen's ordinary states as the person sees them: the ready card, the running figures, the paused,
 * stopped, completed and failed wording, and the buttons each one offers.
 */
class TranslatingScreenStatesTest extends TranslatingScreenTestBase {

    private static final String KEPT_UNTIL_CLOSE = "Progress is kept until the application closes.";

    private void bookReadyWithPending(final int pending) throws TimeoutException {
        readyToStart();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(SettingsViewModel.class).model().set("gemma4:26b");
            return null;
        });
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 0, 0, pending, 0, 0, 0));
    }

    private void publishRunning(final int auto, final int repaired, final int flagged, final int pending) {
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(ProgressFixtures.detailed(7, 11, auto, repaired, flagged, pending, 41, 66));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void publishFlaggedQueue(final int count) {
        mirror().live()
                .publishFlaggedQueue(java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void completeWith(final int auto, final int repaired, final int flagged, final int kept) {
        publishRunning(auto, repaired, flagged, 0);
        mirror().live().publishSourceKept(kept);
        publishFlaggedQueue(flagged);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the ready card left out a fact the start depends on, THEN a person would start a run blind to its settings.
    @Test
    void readyCard_bookOpenNoRun_namesBookModelModeDialAndPendingWithStartAndNoOtherControl() throws Exception {
        bookReadyWithPending(1240);

        showTranslating();
        awaitFx(() -> "1,240".equals(labelText("translating-ready-pending")));

        assertThat(labelText("translating-ready-book")).isEqualTo("Frankenstein.epub");
        assertThat(labelText("translating-ready-model")).isEqualTo("gemma4:26b");
        assertThat(labelText("translating-ready-review")).isEqualTo("Unattended");
        assertThat(labelText("translating-ready-dial")).isEqualTo("Balanced");
        assertThat(enabledControls()).containsExactly("translating-start");
        assertThat(isShown("translating-review-flagged")).isFalse();
    }

    // IF the subtitle kept promising a saved run, THEN a person would close the window expecting to resume.
    @Test
    void subtitle_anyState_saysProgressLastsUntilTheApplicationCloses() {
        showTranslating();

        assertThat(labelText("translating-subtitle"))
                .isEqualTo("A stopped run resumes where it left off — until the application closes.");
    }

    // IF the tiles mixed repaired with auto-accepted, THEN the repair rate would be invisible.
    @Test
    void running_decisionsPublished_showTheFourTilesAndTheProgressLine() {
        showTranslating();

        publishRunning(700, 68, 3, 469);

        assertThat(labelText("translating-count-accepted")).isEqualTo("700");
        assertThat(labelText("translating-count-repaired")).isEqualTo("68");
        assertThat(labelText("translating-count-flagged")).isEqualTo("3");
        assertThat(labelText("translating-count-remaining")).isEqualTo("469");
        assertThat(labelText("translating-progress-text")).isEqualTo("62% · Chapter 7 of 11 · chunk 41/66");
    }

    // IF a part were shown while unknown, THEN the line would read "0 of 0" or "~ left" before there is anything to
    // say.
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "time and rate|24.0|false|80|~1h 20m left · 24 tok/s",
                "estimated rate|24.0|true|80|~1h 20m left · ~24 tok/s",
                "rate only|24.0|false|-1|24 tok/s",
                "time only|-1|false|80|~1h 20m left"
            })
    void pace_figuresPublished_omitEachPartWhileUnknown(
            final String name, final double rate, final boolean estimated, final long minutes, final String expected) {
        showTranslating();
        publishRunning(700, 68, 3, 469);

        mirror().live()
                .publishThroughput(new Throughput(
                        rate < 0 ? null : rate,
                        estimated,
                        minutes < 0 ? null : Duration.ofMinutes(minutes),
                        Duration.ofMinutes(5)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-pace-text")).isEqualTo(expected);
    }

    // IF a paused banner promised the run was saved, THEN a person would quit and lose it.
    @Test
    void paused_inChunk41Of66_saysProgressIsKeptOnlyUntilCloseAndOffersResumeAndNoStart() {
        showTranslating();
        publishRunning(700, 68, 3, 469);

        publish(RunState.PAUSED);

        assertThat(labelText("translating-banner-title")).isEqualTo("Paused");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo(KEPT_UNTIL_CLOSE + " Resume any time — it continues at chunk 41/66.");
        assertThat(enabledControls()).containsExactlyInAnyOrder("translating-resume", "translating-stop");
    }

    // IF a stop kept its terminal wording, THEN a person would not know Resume re-enters at the first pending segment.
    @Test
    void stopped_runReturnsCancelled_saysWhereResumeReentersAndShowsNoErrorToast() {
        showTranslating();
        publishRunning(700, 68, 3, 469);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-banner-title")).isEqualTo("Run stopped");
        assertThat(labelText("translating-banner-text"))
                .isEqualTo(KEPT_UNTIL_CLOSE
                        + " Resume any time — it re-enters at the first pending segment;"
                        + " flagged segments wait in the review panel.");
        assertThat(enabledControls()).containsExactly("translating-resume");
        assertThat(scene.getRoot().lookupAll(".toast-err")).isEmpty();
    }

    // IF Review flagged showed no count, THEN a person could not tell whether anything waits for them.
    @Test
    void reviewFlagged_threeFlaggedInTheQueue_readsWithTheCountWhileRunning() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 3, 0, 0, 0, 0, 0));
        showTranslating();
        publishRunning(700, 68, 3, 469);

        publishFlaggedQueue(3);

        awaitFx(() ->
                "Review flagged (3)".equals(button("translating-review-flagged").getText()));
    }

    // IF the button counted only the flagged while the outcome showed the audit's doubts, THEN the two figures
    // disagreed.
    @Test
    void reviewButton_flaggedAndSuspicious_namesBothCounts() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 47, 0, 0, 0, 0, 0, 45));
        showTranslating();
        publishRunning(1180, 45, 47, 0);
        publishFlaggedQueue(47);
        mirror().live().publishSuspicious(45);

        awaitFx(() -> "Review (47 flagged · 45 suspicious)"
                .equals(button("translating-review-flagged").getText()));
    }

    // IF a completed run showed only counts, THEN a person would have nowhere to go next.
    @Test
    void completed_noPending_showsTheOutcomeAndContinueToExportAndNoStartPauseOrResume() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 3, 0, 0, 0, 0, 0));
        showTranslating();

        completeWith(1180, 45, 3, 12);

        assertThat(labelText("translating-outcome-accepted")).isEqualTo("1,180");
        assertThat(labelText("translating-outcome-repaired")).isEqualTo("45");
        assertThat(labelText("translating-outcome-flagged")).isEqualTo("3");
        assertThat(labelText("translating-outcome-kept")).isEqualTo("12");
        assertThat(button("translating-review-flagged").getText()).isEqualTo("Review flagged (3)");
        assertThat(enabledControls()).isEmpty();
        assertThat(isShown("translating-continue")).isTrue();
    }

    // IF the audit's doubts were not on the outcome card, THEN a silent leak in an accepted segment would pass unseen.
    @Test
    void completed_auditDoubtsThreeSegments_showsTheSuspiciousTileWithItsHover() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 3, 0, 0, 0, 0, 0, 3));
        showTranslating();
        publishRunning(1180, 45, 3, 0);
        mirror().live().publishSuspicious(3);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-outcome-suspicious")).isEqualTo("3");
        assertThat(ua.bookloom.ui.TooltipProbe.tipText(required("translating-outcome-tile-suspicious")))
                .startsWith("Accepted segments that a last check");
    }

    // IF chapter numbers kept as they are were counted as kept by choice, THEN the outcome would misreport the book.
    @Test
    void completed_twoKeptAsIs_showsThemApartFromKeptAsSource() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(40, 0, 0, 0, 0, 0, 0, 0, 2));
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 40, 0, 0, 1, 1, 38, 0, 2));
        mirror().live().publishSourceKept(1);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-outcome-accepted")).isEqualTo("38");
        assertThat(labelText("translating-outcome-verbatim")).isEqualTo("2");
        assertThat(labelText("translating-outcome-kept")).isEqualTo("1");
    }

    // IF Continue to Export did not lead to the export step, THEN the finished book could not be found.
    @Test
    void continueToExport_completedRun_showsTheExportScreen() throws Exception {
        bookReadyWithPending(0);
        showTranslating();
        completeWith(1180, 45, 3, 12);

        onFx(() -> button("translating-continue").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(optional("export-card")).isNotNull();
        assertThat(optional("translating-screen")).isNull();
    }

    // IF a completed run with segments still pending offered no Start, THEN they could never be translated.
    @Test
    void completed_pendingRemain_offersStartBesideContinueAndStartAsksForANewJobOnTheSameProject() throws Exception {
        bookReadyWithPending(3);
        showTranslating();
        awaitFx(() -> "3".equals(labelText("translating-ready-pending")));

        completeWith(1180, 45, 3, 12);
        awaitFx(() -> isShown("translating-start"));
        assertThat(isShown("translating-continue")).isTrue();

        onFx(() -> button("translating-start").fire());
        awaitFx(() -> !engine.requests().isEmpty());

        assertThat(engine.requests()).hasSize(1);
        assertThat(engine.requests().get(0).projectId())
                .isEqualTo(injector.getInstance(ua.bookloom.ui.state.CurrentProject.class)
                        .book()
                        .get()
                        .projectId());
    }

    // IF a failed run promised saved progress or dressed as a provider pause, THEN it would mislead; it opens the
    // blocking dialog with the honest line and then shows the outcome so far with Start.
    @Test
    void failed_internalErrorAfter412Decisions_opensTheRunFailureDialogThenTheOutcomeWithStart() throws Exception {
        bookReadyWithPending(828);
        showTranslating();
        publishRunning(412, 0, 0, 828);

        mirror().publishOutcome(
                        RunState.FAILED,
                        null,
                        AppError.of(ErrorCode.internal, "Unexpected error", "The run stopped unexpectedly."));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("error-note")).isEqualTo("Decided segments are kept until the application closes.");
        assertThat(labelText("translating-banner-title")).doesNotContain("Provider error");
        assertThat(labelText("translating-outcome-accepted")).isEqualTo("412");
        assertThat(enabledControls()).containsExactly("translating-start");
    }
}
