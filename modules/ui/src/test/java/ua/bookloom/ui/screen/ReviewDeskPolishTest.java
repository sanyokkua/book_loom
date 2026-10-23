package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.VirtualFlow;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/** The desk's honesty about what a person can do, and the opening choices a lone count makes. */
class ReviewDeskPolishTest extends ReviewDeskTestBase {

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

    // IF a selection made in code stayed out of view, THEN the detail would change to a segment the list does not show.
    @Test
    @SuppressWarnings("unchecked")
    void list_selectionFarDown_isScrolledIntoView() throws Exception {
        openDeskWith(ReviewDeskFixtures.acceptedRows(30), List.of());
        final ListView<ReviewRow> rows = rowList();

        onFx(() -> rows.getSelectionModel().select(25));

        final VirtualFlow<?> flow = (VirtualFlow<?>) rows.lookup(".virtual-flow");
        assertThat(fx(() -> flow.getLastVisibleCell().getIndex())).isGreaterThanOrEqualTo(25);
    }

    // IF a segment with no translation could be accepted, THEN its source would count as reviewed by the person.
    @Test
    void mainButton_segmentKeptNoTranslation_isOffAndTheNoteSaysWhy() throws Exception {
        openDeskWith(List.of(sourceOnly()), List.of());

        assertThat(main().isDisabled()).isTrue();
        assertThat(labelText("review-accept-note"))
                .isEqualTo("Write or retry a translation first: this segment has no translation to accept.");
    }

    // IF a flagged segment with no finding showed an empty list, THEN the person could not tell why it is listed.
    @Test
    void findings_flaggedWithNone_sayWhyItIsListed() throws Exception {
        openDeskWith(List.of(sourceOnly()), List.of());

        assertThat(labelText("review-findings-none")).isEqualTo("Flagged by the run — no detail recorded.");
    }

    // IF Review (n) opened the empty flagged list when only doubted segments exist, THEN the number on the control
    // and the list behind it would disagree.
    @Test
    void reviewButton_noFlaggedButSuspicious_opensOnWorthASecondLook() throws Exception {
        pausedWith(List.of(), ReviewDeskFixtures.worthASecondLook());
        publish(RunState.COMPLETED);

        onFx(() -> button(REVIEW).fire());
        awaitFx(() -> isShown(DESK) && !rowList().getItems().isEmpty());

        assertThat(desk.calls()).contains("queue(" + projectId() + ", " + ReviewFilter.SUSPICIOUS + ")");
        assertThat(rowTexts()).containsExactly("ch2 · p04", "ch2 · p08", "ch4 · p02");
    }
}
