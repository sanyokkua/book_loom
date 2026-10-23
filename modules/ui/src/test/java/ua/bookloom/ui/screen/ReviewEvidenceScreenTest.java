package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;

/**
 * The review desk's readable view: formatting tokens as chips instead of raw {@code ⟦gN⟧}, and the text a finding
 * quotes marked inside the target, beside the finding's kind badge.
 */
class ReviewEvidenceScreenTest extends ReviewDeskTestBase {

    private void openPanelWith(final SegmentView view) throws Exception {
        openDeskWith(List.of(view), List.of());
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

    // IF the kind showed twice (a badge and a word), THEN every finding would read as two; one label names the kind,
    // the severity and who raised it.
    @Test
    void findings_namedKind_showsOneLabelAndNoSeparateBadge() throws Exception {
        openPanelWith(ReviewFixtures.withEvidence());

        assertThat(textOf("review-findings")).startsWith("name · medium · ");
        assertThat(isShown("review-finding-badge")).isFalse();
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
