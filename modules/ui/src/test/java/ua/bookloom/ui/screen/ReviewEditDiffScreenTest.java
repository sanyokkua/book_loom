package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;

/** The review desk's findings list shows an edit the reviewer made as a highlighted diff with its criterion. */
class ReviewEditDiffScreenTest extends ReviewDeskTestBase {

    private void openPanelWith(final SegmentView view) throws Exception {
        openDeskWith(List.of(view), List.of());
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
