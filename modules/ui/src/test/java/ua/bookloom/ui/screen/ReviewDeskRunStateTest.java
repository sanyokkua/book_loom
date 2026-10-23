package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.RunState;

/** What the desk does while a run translates, while a retry is in flight, and when the desk refuses an action. */
class ReviewDeskRunStateTest extends ReviewDeskTestBase {

    private static final String BANNER = "review-readonly";

    private void openDuringARun() throws Exception {
        readyToStart();
        script(ReviewDeskFixtures.sixToReview(), List.of());
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        publish(RunState.RUNNING);
        onFx(() -> injector.getInstance(ReviewDeskLauncher.class).open(ReviewFilter.ALL_FLAGGED));
        awaitFx(() -> isShown(DESK) && !rowList().getItems().isEmpty());
        awaitFx(() -> !target().getText().isEmpty());
    }

    // IF the target could be typed in while the model translated, THEN the person's edit would race the run for the
    // same segment; the desk reads, says so, and offers Pause.
    @Test
    void desk_runIsTranslating_isReadOnlyWithTheHeaderTheBannerAndPause() throws Exception {
        openDuringARun();

        assertThat(target().isEditable()).isFalse();
        assertThat(main().isDisabled()).isTrue();
        assertThat(textOf("review-desk-left")).contains("read-only while translating");
        assertThat(textOf(BANNER)).contains("Pause the translation to edit, accept or try again.");
        assertThat(isShown("review-pause")).isTrue();
    }

    @Test
    void pause_pressedInTheBanner_pausesTheRunAndThenTheDeskIsEditable() throws Exception {
        openDuringARun();

        onFx(() -> button("review-pause").fire());
        awaitFx(() -> job.calls().contains("pause"));
        publish(RunState.PAUSED);

        assertThat(isShown(BANNER)).isFalse();
        assertThat(target().isEditable()).isTrue();
    }

    // IF a retry on a paused run showed "Pause the translation", THEN the person would be told to pause a run that
    // is already paused; the desk says the model is busy and offers no Pause.
    @Test
    void desk_retryInFlightOnAPausedRun_isReadOnlyWithItsOwnLineAndNoPause() throws Exception {
        openDesk();

        onFx(() -> mirror().review().publishRetryInFlight(true));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(target().isEditable()).isFalse();
        assertThat(textOf(BANNER)).contains("Trying again… the desk is available when the model answers.");
        assertThat(isShown("review-pause")).isFalse();
        assertThat(textOf("review-desk-left")).doesNotContain("read-only while translating");
    }

    // IF a retry went ahead while the project's own run was running, THEN two calls would race for the model; it is
    // refused with busy and the desk is never called.
    @Test
    void retry_whileTheRunIsRunning_isRefusedBusyAndTheDeskIsNeverCalled() throws Exception {
        openDuringARun();

        onFx(() ->
                injector.getInstance(ua.bookloom.ui.state.ReviewViewModel.class).retry(null, false));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(desk.calls()).noneMatch(call -> call.startsWith("retry("));
        assertThat(textsUnder(scene.getRoot())).anyMatch(text -> text.contains("run is translating"));
    }

    // IF a refused action were silent or cleared the row, THEN the person would not know the segment is unchanged.
    @Test
    void accept_deskRefusesWithValidation_showsItInPlaceAndTheRowKeepsItsMarker() throws Exception {
        openDesk();
        desk.willAnswer(Result.err(AppError.of(ErrorCode.validation, "Refused", "The segment changed.")));

        onFx(() -> main().fire());
        awaitFx(() -> isShown("review-problem") || isShown("modal-feedback"));

        assertThat(markerWords().getFirst()).isEqualTo("to review");
        assertThat(isShown(DESK)).isTrue();
    }

    // IF a backward-revision proposal applied itself, THEN the person's text would change before they read it.
    @Test
    void suggestion_proposalWaiting_isShownAndAppliedOnlyWhenTheSuggestionIsUsed() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withProposal()), List.of());
        final String call = "acceptProposal(" + projectId() + ", ch03.xhtml:2)";
        onFx(() -> ((javafx.scene.control.TabPane) required("review-tabs"))
                .getSelectionModel()
                .select(2));

        assertThat(textOf("review-proposal")).contains("Він пішов.");
        assertThat(desk.calls()).doesNotContain(call);
        onFx(() -> button("review-accept-proposal").fire());

        awaitFx(() -> desk.calls().contains(call));
    }

    // IF All segments needed an Unattended run, THEN a person on Assisted could never spot-check what was accepted.
    @Test
    void allSegments_afterAnAssistedRun_listsEveryDecidedSegment() throws Exception {
        launchWith(ReviewMode.ASSISTED);
        desk.willAnswerQueue(ReviewFilter.ALL_SEGMENTS, ReviewDeskFixtures.acceptedRows(3));
        openDesk();

        chooseFilter(ReviewFilter.ALL_SEGMENTS);
        awaitFx(() -> rowList().getItems().size() == 3);

        assertThat(rowTexts()).containsExactly("ch1 · p1", "ch1 · p2", "ch1 · p3");
    }

    // IF the count were read once, THEN the Review control would keep saying 6 after the run changed the number.
    @Test
    void reviewControl_runStateChanges_rereadsTheToReviewCount() throws Exception {
        pausedWith(ReviewDeskFixtures.sixToReview(), List.of());

        desk.willAnswerCounts(ReviewDeskFixtures.counts(4, 0));
        publish(RunState.RUNNING);
        publish(RunState.PAUSED);

        awaitFx(() -> button(REVIEW).getText().equals("Review (4)"));
    }
}
