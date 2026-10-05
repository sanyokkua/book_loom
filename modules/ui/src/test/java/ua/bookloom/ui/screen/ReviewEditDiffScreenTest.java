package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/** The review panel's findings list shows an edit the reviewer made as a highlighted diff with its criterion. */
class ReviewEditDiffScreenTest extends TranslatingScreenTestBase {

    private static final String REVIEW = "translating-review-flagged";

    private void openPanelWith(final SegmentView view) throws Exception {
        readyToStart();
        desk.willAnswerQueue(List.of(view));
        desk.willAnswerSegment(view);
        desk.willAnswerCounts(new ReviewCounts(100, 99, 0, 1, 0, 0, 0, 0, 0));
        showTranslating();
        publish(RunState.PAUSED);
        mirror().live().publishFlaggedQueue(List.of(new FlaggedRow(view.segmentId(), view.locator(), List.of(), null)));
        WaitForAsyncUtils.waitForFxEvents();
        awaitFx(() -> button(REVIEW).getText().equals("Review flagged (1)"));
        onFx(() -> button(REVIEW).fire());
        WaitForAsyncUtils.waitForFxEvents();
        selectFirstRow();
    }

    @SuppressWarnings("unchecked")
    private void selectFirstRow() throws TimeoutException {
        final ListView<ReviewRow> rows = (ListView<ReviewRow>) required("review-list");
        awaitFx(() -> !rows.getItems().isEmpty());
        onFx(() -> rows.getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    private List<String> labelsOf(final String id) {
        return required("review-findings").lookupAll("#" + id).stream()
                .map(node -> ((Label) node).getText())
                .toList();
    }

    // IF an applied edit showed as a raw note, THEN a person would have to compare two texts by eye.
    @Test
    void findings_appliedEdit_showsItsCriterionAndTheRemovedAndAddedText() throws Exception {
        openPanelWith(ReviewFixtures.withAppliedEdit());
        assertThat(labelText("review-edit-criterion")).isEqualTo("Edit applied · gender");
        assertThat(labelText("review-edit-removed")).isEqualTo("− Вона втомився");
        assertThat(labelText("review-edit-added")).isEqualTo("+ Вона втомилася");
    }

    @Test
    void findings_appliedEdit_isNotListedAsACheckWithRawText() throws Exception {
        openPanelWith(ReviewFixtures.withAppliedEdit());
        assertThat(textOf("review-findings")).doesNotContain("reviewer-edit").doesNotContain("\u241E");
    }

    // IF a deleted word showed an empty line, THEN the person could not tell the edit from a rendering fault.
    @Test
    void findings_editThatOnlyDeleted_saysRemovedOnTheAddedLine() throws Exception {
        openPanelWith(ReviewFixtures.withAppliedEdit());
        assertThat(labelsOf("review-edit-added")).contains("+ (removed)");
    }
}
