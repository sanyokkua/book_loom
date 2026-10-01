package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.RunState;

/** What a review pause does on the Translating screen: opens its segment and lets the person's decision continue. */
class ReviewPauseTest extends TranslatingScreenTestBase {

    private static final String ACCEPT = "review-accept";
    private static final String EDIT = "Чудовисько стріло мене опівночі.";

    private static SegmentView acceptedFirst() {
        return ReviewFixtures.view("ch01.xhtml:2", "ch1 · p03", SegmentStatus.ACCEPTED, List.of(), null, null);
    }

    private void startAndPause(final PauseReason reason, final SegmentView paused) throws Exception {
        readyToStart();
        desk.willAnswerQueue(reason == PauseReason.ON_FLAGGED ? List.of(paused) : List.of());
        desk.willAnswerSegment(paused);
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 1, 0, 0, 0, 0, 0));
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        job.emit(new Paused(reason, null, ProgressFixtures.progress(1, 2, 5, 1, 5), paused.segmentId()));
        awaitFx(() -> isShown("review-panel"));
        awaitFx(() -> !labelText("review-locator").isEmpty());
    }

    private void assistedPauseOnFlagged() throws Exception {
        launchWith(ReviewMode.ASSISTED);
        startAndPause(PauseReason.ON_FLAGGED, ReviewFixtures.lowScore());
    }

    private void editTo(final String text) throws Exception {
        onFx(() -> ((TextArea) required("review-target")).setText(text));
        awaitFx(() -> !button("review-save").isDisabled());
    }

    // IF the pause only stopped the run, THEN the person would have to hunt for the segment it names.
    @Test
    void assistedPause_flaggedSegment_opensThePanelOnItsLocator() throws Exception {
        assistedPauseOnFlagged();

        assertThat(labelText("review-locator")).isEqualTo("ch5 · p12");
        assertThat(button(ACCEPT).getText()).isEqualTo("Accept");
    }

    // IF Accept resumed before the desk confirmed it, THEN the run would read a segment still awaiting its decision.
    @Test
    void accept_deskHoldsItsAnswer_resumesOnlyAfterTheDeskAnswers() throws Exception {
        assistedPauseOnFlagged();
        desk.holdAccept();

        onFx(() -> button(ACCEPT).fire());
        desk.awaitAcceptCalled();
        WaitForAsyncUtils.waitForFxEvents();
        final List<String> whileHeld = job.calls();
        desk.releaseAccept();
        awaitFx(() -> job.calls().contains("resume"));

        assertThat(whileHeld).doesNotContain("resume");
        assertThat(job.calls()).last().isEqualTo("resume");
    }

    @Test
    void manualPause_afterAcceptedSegment_showsAcceptAndContinueWhichAcceptsAndResumes() throws Exception {
        launchWith(ReviewMode.MANUAL);
        startAndPause(PauseReason.AFTER_SEGMENT, acceptedFirst());

        assertThat(labelText("review-locator")).isEqualTo("ch1 · p03");
        assertThat(button(ACCEPT).getText()).isEqualTo("Accept & continue");
        onFx(() -> button(ACCEPT).fire());
        awaitFx(() -> job.calls().contains("resume"));

        assertThat(desk.calls()).anyMatch(call -> call.startsWith("accept(") && call.endsWith("ch01.xhtml:2)"));
    }

    @Test
    void saveEdit_inAPause_savesThenResumes() throws Exception {
        assistedPauseOnFlagged();
        editTo(EDIT);

        onFx(() -> button("review-save").fire());
        awaitFx(() -> job.calls().contains("resume"));

        assertThat(desk.calls()).anyMatch(call -> call.startsWith("saveEdit(") && call.endsWith(", " + EDIT + ")"));
    }

    // IF a refused save resumed the run, THEN the run would go on over an edit that was never stored.
    @Test
    void saveEdit_deskRefusesWithValidation_doesNotResume() throws Exception {
        assistedPauseOnFlagged();
        editTo(EDIT);
        desk.willAnswer(Result.err(AppError.of(ErrorCode.validation, "Refused", "The edit lost a placeholder.")));

        onFx(() -> button("review-save").fire());
        awaitFx(() -> isShown("review-problem"));

        assertThat(job.calls()).doesNotContain("resume");
    }

    @Test
    void skip_inAPause_leavesTheRunPaused() throws Exception {
        assistedPauseOnFlagged();

        onFx(() -> button("review-skip").fire());
        awaitFx(() -> desk.calls().stream().anyMatch(call -> call.startsWith("skip(")));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(job.calls()).doesNotContain("resume");
    }

    // IF Unattended opened the panel by itself, THEN it would cover the run the person chose not to watch.
    @Test
    void unattendedRun_fourFlagged_neverOpensThePanelAndTheControlCountsThem() throws Exception {
        readyToStart();
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 4, 0, 0, 0, 0, 0));
        showTranslating();
        publish(RunState.COMPLETED);
        mirror().live()
                .publishFlaggedQueue(java.util.stream.IntStream.range(0, 4)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        awaitFx(() -> button("translating-review-flagged").getText().equals("Review flagged (4)"));

        assertThat(isShown("review-panel")).isFalse();
    }
}
