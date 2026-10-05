package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/**
 * The review panel's readable view: formatting tokens as chips instead of raw {@code ⟦gN⟧}, and the text a finding
 * quotes marked inside the target, beside the finding's kind badge.
 */
class ReviewEvidenceScreenTest extends TranslatingScreenTestBase {

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

    private List<String> textsOf(final String flowId, final String styleClass) {
        return required(flowId).lookupAll("." + styleClass).stream()
                .map(node -> ((Label) node).getText())
                .toList();
    }

    private List<String> plainTexts(final String flowId) {
        return required(flowId).lookupAll(".flow-text").stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .toList();
    }

    // IF the finding's kind were not a badge, THEN a person would read the note to learn what sort of defect it is.
    @Test
    void findings_namedKind_showsItsBadge() throws Exception {
        openPanelWith(ReviewFixtures.withEvidence());

        assertThat(labelText("review-finding-badge")).isEqualTo("name");
    }

    // IF the quoted words were not marked in the target, THEN a person would hunt for them in the paragraph.
    @Test
    void readableView_findingQuotesAWord_marksThatWordInTheTarget() throws Exception {
        openPanelWith(ReviewFixtures.withEvidence());

        assertThat(textsOf("review-target-preview", "evidence-mark")).containsExactly("відчинив");
    }

    // IF raw tokens stayed in the readable text, THEN a person would read ⟦g0⟧ as if it were part of the book.
    @Test
    void readableView_targetAndSourceWithTokens_showEachTokenAsAChipNotAsRawText() throws Exception {
        openPanelWith(ReviewFixtures.withEvidence());

        assertThat(textsOf("review-target-preview", "placeholder-chip")).containsExactly("g0", "g1");
        assertThat(textsOf("review-source-preview", "placeholder-chip")).containsExactly("g0", "g1");
        assertThat(String.join("", plainTexts("review-target-preview"))).doesNotContain("⟦");
        assertThat(String.join("", plainTexts("review-source-preview"))).doesNotContain("⟦");
    }

    // The readable view keeps the book's words in order around the chips.
    @Test
    void readableView_target_keepsTheTextAroundTheChipsInOrder() throws Exception {
        openPanelWith(ReviewFixtures.withEvidence());

        final Node flow = required("review-target-preview");
        assertThat(String.join("", plainTexts("review-target-preview"))
                        + textsOf("review-target-preview", "evidence-mark"))
                .contains("Гейл", "старі", "двері.");
        assertThat(flow.isManaged()).isTrue();
    }
}
