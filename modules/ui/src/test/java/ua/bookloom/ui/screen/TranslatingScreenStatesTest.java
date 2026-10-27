package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
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
        assertThat(labelText("translating-ready-line")).isEqualTo("Nothing translated yet.");
        assertThat(isShown("translating-live-card")).isFalse();
        assertThat(isShown("translating-strip")).isTrue();
    }

    // IF the card only said "not chosen", THEN a first-time person would press Start to learn what to do; the hint says
    // where a model is chosen, and goes once one is.
    @Test
    void readyCard_noModelChosen_saysWhereToChooseOneUntilOneIs() throws Exception {
        bookReadyWithPending(1240);
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set(""));

        showTranslating();
        final boolean shownWithout = isShown("translating-ready-model-hint");
        final String words = labelText("translating-ready-model-hint");
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("gemma4:26b"));

        assertThat(shownWithout).isTrue();
        assertThat(words).isEqualTo("No model is chosen yet. Choose an AI model in Settings, then start.");
        assertThat(isShown("translating-ready-model-hint")).isFalse();
    }

    // IF the subtitle still said progress lasts only until the window closes, THEN a person would keep the window open
    // for hours for nothing: every decision is saved as it is made.
    @Test
    void subtitle_anyState_saysProgressIsSavedAsYouGo() {
        showTranslating();

        assertThat(labelText("translating-subtitle"))
                .isEqualTo("Progress is saved as you go. Continue translation picks up where it left off, also after a"
                        + " restart.");
    }

    // IF the counts mixed repaired with auto-accepted, THEN the repair rate would be invisible.
    @Test
    void running_decisionsPublished_showTheCountsAndTheFactsLine() {
        showTranslating();

        publishRunning(700, 68, 3, 469);

        assertThat(labelText("translating-count-translated")).isEqualTo("700 translated");
        assertThat(labelText("translating-count-fixed")).isEqualTo("68 fixed automatically");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-strip-line")).isEqualTo("771 of 1,240 segments done · Chapter 7 of 11");
    }

    private void publishTimeLeft(final Duration left) {
        publishRunning(700, 68, 3, 469);
        publish(RunState.RUNNING);
        mirror().live().publishThroughput(new Throughput(24.0, false, left, Duration.ofMinutes(5)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the strip did not lead with the time left once it is measured, THEN the first question has no answer.
    @Test
    void bigLine_timeLeftMeasured_saysAboutHowLong() {
        showTranslating();

        publishTimeLeft(Duration.ofMinutes(80));

        assertThat(labelText("translating-strip-big")).isEqualTo("about 1 h 20 min left");
    }

    // IF a time left were shown before it is measured, THEN the strip would invent a finishing time.
    @Test
    void bigLine_timeLeftNotMeasured_doesNotSayLeft() {
        showTranslating();

        publishRunning(700, 68, 3, 469);
        publish(RunState.RUNNING);

        assertThat(textOf("translating-strip")).doesNotContain("left");
    }

    // IF the paused line did not say what resume redoes, THEN a person would wonder whether the segments in flight are
    // lost; a pause by the person has no banner (README "Run states").
    @Test
    void paused_byThePerson_saysTheSegmentsInFlightAreTranslatedAgainAndOffersResumeAndNoStart() {
        showTranslating();
        publishRunning(700, 68, 3, 469);

        publish(RunState.PAUSED);

        assertThat(labelText("translating-live-line"))
                .isEqualTo("Paused. The segments in progress are translated again when you resume.");
        assertThat(optional("translating-banner")).isNull();
        assertThat(enabledControls()).containsExactlyInAnyOrder("translating-resume", "translating-stop");
    }

    // IF the paused line named no segments when the call in flight is known, THEN the person could not tell which are
    // done again.
    @Test
    void paused_callOfFourInFlight_namesItsSegments() {
        showTranslating();
        publishRunning(700, 68, 3, 469);
        mirror().live()
                .publishCalls(ua.bookloom.ui.LiveCallFixtures.calls(
                        ua.bookloom.ui.LiveCallFixtures.waiting(
                                1, 4, "Text.", ua.bookloom.ui.LiveCallFixtures.smallPrompt()),
                        null));

        publish(RunState.PAUSED);

        assertThat(labelText("translating-live-line"))
                .isEqualTo("Paused. Segments ch7 · p101–ch7 · p104 are translated again when you resume.");
    }

    // IF a stop kept its terminal wording, THEN a person would not know Continue translation re-enters at the first
    // pending segment.
    @Test
    void stopped_runReturnsCancelled_saysWhereResumeReentersAndShowsNoErrorToast() {
        showTranslating();
        publishRunning(700, 68, 3, 469);

        mirror().publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-live-line"))
                .isEqualTo("Stopped. Continue translation picks up at the first segment not done.");
        assertThat(optional("translating-banner")).isNull();
        assertThat(enabledControls()).containsExactly("translating-resume");
        assertThat(button("translating-resume").getText()).isEqualTo("Continue translation");
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

        awaitFx(() -> "Review (3)".equals(button("translating-review-flagged").getText()));
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

        awaitFx(() -> "Review (92)".equals(button("translating-review-flagged").getText()));
    }

    // IF a completed run showed only counts, THEN a person would have nowhere to go next.
    @Test
    void completed_noPending_showsTheOutcomeAndContinueToExportAndNoStartPauseOrResume() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 3, 0, 0, 0, 0, 0));
        showTranslating();

        completeWith(1180, 45, 3, 12);

        assertThat(labelText("translating-count-translated")).isEqualTo("1,180 translated");
        assertThat(labelText("translating-count-fixed")).isEqualTo("45 fixed automatically");
        assertThat(labelText("translating-count-to-review")).isEqualTo("3 to review");
        assertThat(labelText("translating-count-kept")).isEqualTo("12 kept as is");
        assertThat(button("translating-review-flagged").getText()).isEqualTo("Review (3)");
        assertThat(enabledControls()).isEmpty();
        assertThat(isShown("translating-continue")).isTrue();
    }

    // IF the audit's doubts were not on the outcome card, THEN a silent leak in an accepted segment would pass unseen.
    @Test
    void completed_auditDoubtsThreeSegments_showsTheSecondLookCountWithItsHover() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 3, 0, 0, 0, 0, 0, 3));
        showTranslating();
        publishRunning(1180, 45, 3, 0);
        mirror().live().publishSuspicious(3);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-count-second-look")).isEqualTo("3 worth a second look");
        assertThat(ua.bookloom.ui.TooltipProbe.tipText(required("translating-count-second-look")))
                .startsWith("Accepted segments that a last check");
    }

    // IF text kept verbatim and text kept as the source were counted apart, THEN "kept as is" would under-report.
    @Test
    void completed_twoKeptVerbatimAndOneKeptAsSource_countsThreeKeptAsIs() throws Exception {
        bookReadyWithPending(0);
        desk.willAnswerCounts(new ReviewCounts(40, 0, 0, 0, 0, 0, 0, 0, 2));
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 40, 0, 0, 1, 1, 38, 0, 2));
        mirror().live().publishSourceKept(1);
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(labelText("translating-count-translated")).isEqualTo("38 translated");
        assertThat(labelText("translating-count-kept")).isEqualTo("3 kept as is");
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

    // IF a failed run dressed as a provider pause or opened a dialog by itself, THEN it would mislead or interrupt; its
    // banner says what happened, Show details opens the failure, and the outcome so far offers Continue translation.
    @Test
    void failed_internalErrorAfter412Decisions_showsTheFailedBannerAndOpensTheFailureOnlyOnShowDetails()
            throws Exception {
        bookReadyWithPending(828);
        showTranslating();
        publishRunning(412, 0, 0, 828);

        mirror().publishOutcome(
                        RunState.FAILED,
                        null,
                        AppError.of(ErrorCode.internal, "Unexpected error", "The run stopped unexpectedly."));
        WaitForAsyncUtils.waitForFxEvents();
        final boolean dialogBeforeShowDetails = optional("error-note") != null;
        onFx(() -> button("translating-show-details").fire());

        assertThat(dialogBeforeShowDetails).isFalse();
        assertThat(labelText("translating-banner-title"))
                .isEqualTo("The translation stopped because of an error. Finished segments are saved.");
        assertThat(labelText("error-note"))
                .isEqualTo("Finished segments are saved. Continue translation picks up where the run stopped.");
        assertThat(labelText("translating-count-translated")).isEqualTo("412 translated");
        assertThat(enabledControls()).containsExactly("translating-start");
    }
}
