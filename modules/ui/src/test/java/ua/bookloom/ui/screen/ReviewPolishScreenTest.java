package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.VirtualFlow;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/**
 * The review panel's honesty about what a person can do: the target's mark says read-only while it is, a segment that
 * kept no translation cannot be accepted and says so, and a flagged segment with no finding says why it is listed.
 */
class ReviewPolishScreenTest extends TranslatingScreenTestBase {

    private static final String REVIEW = "translating-review-flagged";

    /** A flagged segment with no finding and no translation kept, as a placeholder failure leaves one. */
    private static SegmentView sourceOnly() {
        final SegmentView base =
                ReviewFixtures.view("ch12.xhtml:1", "ch12 · p02", SegmentStatus.FLAGGED, List.of(), null, null);
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                null,
                null,
                null,
                base.findings(),
                null,
                base.path(),
                false,
                null,
                null);
    }

    private void openPanelOn(final RunState state, final SegmentView view) throws TimeoutException {
        openPanelOn(state, List.of(view));
    }

    private void openPanelOn(final RunState state, final List<SegmentView> views) throws TimeoutException {
        readyToStart();
        desk.willAnswerQueue(views);
        views.forEach(desk::willAnswerSegment);
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 1, 0, 0, 0, 1, 0));
        showTranslating();
        publish(state);
        onFx(() -> button(REVIEW).fire());
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        final ListView<ReviewRow> rows = (ListView<ReviewRow>) required("review-list");
        awaitFx(() -> !rows.getItems().isEmpty());
        onFx(() -> rows.getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    // IF a selection made in code (after a refresh, or by the view model) stayed out of view, THEN the person would see
    // the compare change to a segment the list does not show; the list scrolls the selected row into view.
    @Test
    void list_selectionFarDown_isScrolledIntoView() throws TimeoutException {
        final List<SegmentView> many = IntStream.range(0, 30)
                .mapToObj(i -> ReviewFixtures.view(
                        "ch01.xhtml:" + i, "ch1 · p" + i, SegmentStatus.FLAGGED, List.of(), null, null))
                .toList();
        openPanelOn(RunState.PAUSED, many);
        @SuppressWarnings("unchecked")
        final ListView<ReviewRow> rows = (ListView<ReviewRow>) required("review-list");

        onFx(() -> rows.getSelectionModel().select(25));

        final VirtualFlow<?> flow = (VirtualFlow<?>) rows.lookup(".virtual-flow");
        assertThat(ThemeTestSupport.onFx(() -> flow.getLastVisibleCell().getIndex()))
                .isGreaterThanOrEqualTo(25);
    }

    // IF the mark kept saying EDITABLE while a run locks the target, THEN the person would type into a box that takes
    // nothing.
    @Test
    void mark_whileTheRunTranslates_readsReadOnly() throws TimeoutException {
        openPanelOn(RunState.RUNNING, ReviewFixtures.lowScore());

        assertThat(((Label) required("review-editable-mark")).getText()).isEqualTo("READ-ONLY");
        assertThat(((TextArea) required("review-target")).isEditable()).isFalse();
    }

    // IF the mark said read-only while the target can be typed into, THEN the person would not try.
    @Test
    void mark_paused_readsEditable() throws TimeoutException {
        openPanelOn(RunState.PAUSED, ReviewFixtures.lowScore());

        assertThat(((Label) required("review-editable-mark")).getText()).isEqualTo("EDITABLE");
    }

    // IF a segment with no translation could be accepted, THEN its source would count as reviewed by the person.
    @Test
    void accept_segmentKeptNoTranslation_isOffAndSaysWhy() throws TimeoutException {
        openPanelOn(RunState.PAUSED, sourceOnly());

        assertThat(button("review-accept").isDisabled()).isTrue();
        assertThat(isShown("review-accept-note")).isTrue();
        assertThat(((Label) required("review-accept-note")).getText())
                .isEqualTo("Write or retry a translation first: this segment has no translation to accept.");
    }

    // IF a flagged segment with no finding showed an empty findings list, THEN the person could not tell why it is
    // listed.
    @Test
    void findings_flaggedWithNone_sayWhyItIsListed() throws TimeoutException {
        openPanelOn(RunState.PAUSED, sourceOnly());

        assertThat(((Label) required("review-findings-none")).getText())
                .isEqualTo("Flagged by the run — no detail recorded.");
    }

    /** A flagged segment with no stored translation whose refused reply the editor shows to be saved. */
    private static SegmentView refusedReplyOnly() {
        final SegmentView base = sourceOnly();
        return new SegmentView(
                base.segmentId(),
                base.locator(),
                base.kind(),
                base.status(),
                base.maskedSource(),
                base.displaySource(),
                null,
                null,
                null,
                base.findings(),
                null,
                base.path(),
                false,
                null,
                null,
                "Він пішов.");
    }

    // IF Accept were off with only "write or retry a translation", THEN a person looking at the model's refused reply
    // would not learn that Save edit is what keeps it.
    @Test
    void accept_editorHoldsTheRefusedReply_isOffAndPointsToSaveEdit() throws TimeoutException {
        openPanelOn(RunState.PAUSED, refusedReplyOnly());

        assertThat(button("review-accept").isDisabled()).isTrue();
        assertThat(button("review-save").isDisabled()).isFalse();
        assertThat(((Label) required("review-accept-note")).getText())
                .isEqualTo(
                        "Accept is off: the text shown is the model's refused reply, not a stored translation."
                                + " Save edit keeps it as your translation; Accept only confirms a translation already stored.");
    }
}
