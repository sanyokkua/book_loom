package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.state.TechnicalDetails;

/** The detail's tabs: what may be wrong, what the model saw, the suggestion. */
class ReviewDeskTabsTest extends ReviewDeskTestBase {

    private void openTab(final int index) {
        onFx(() -> ((TabPane) required("review-tabs")).getSelectionModel().select(index));
    }

    private void selectLocator(final String locator, final int row) throws Exception {
        onFx(() -> rowList().getSelectionModel().select(row));
        awaitFx(() -> locator.equals(selectedLocator()));
    }

    private static SegmentView omission() {
        return ReviewFixtures.view(
                "ch04.xhtml:1",
                "ch4 · p01",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("omission", Severity.MEDIUM, "length ratio 0.29, expected 0.35–3.6", "length")),
                null,
                null);
    }

    // IF "What the model saw" rebuilt the context from the glossary of today, THEN a batch-adopted segment would show
    // names its request never carried; it shows the sections that batch request really sent.
    @Test
    void whatTheModelSaw_batchAdoptedSegment_showsTheSectionsTheBatchSent() throws Exception {
        openDesk();
        selectLocator("ch2 · p06", 5);

        openTab(1);

        assertThat(textOf("review-sent"))
                // The glossary section is drawn as a table of term and rendering, as the live card draws it.
                .contains("Velk", "Велк")
                .contains("one line → перший раніший рядок")
                .contains("two lines → другий раніший рядок");
        assertThat(textOf("review-context-intro"))
                .contains("This segment was translated with others in one request; this is what that request sent.");
    }

    // IF a segment drafted alone showed a batch prompt, THEN it would claim a request that never existed.
    @Test
    void whatTheModelSaw_segmentDraftedAlone_showsTheContextSectionNotAPrompt() throws Exception {
        openDesk();
        selectLocator("ch3 · p01", 1);

        openTab(1);

        assertThat(isShown("review-sent")).isFalse();
        assertThat(isShown("review-context")).isTrue();
        assertThat(textOf("review-context-intro"))
                .contains("Exactly what the model was sent when it translated this segment.");
    }

    // IF a finding showed a badge and a second label, THEN it would read twice; it is one label.
    @Test
    void findings_glossaryFinding_showsExactlyOneLabelAndNoBadge() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());

        assertThat(textOf("review-findings")).contains("name · medium · ");
        assertThat(isShown("review-finding-badge")).isFalse();
        assertThat(textOf("review-findings"))
                .contains("a name may not match the glossary — if it reads well, press Accept");
    }

    // IF rule codes and figures were always shown, THEN a person would read numbers they cannot use; they sit behind
    // "Technical details" (the shared technical-details switch, whose property the desk follows and offers).
    @Test
    void findings_omission_showsTheFiguresOnlyWithTechnicalDetailsOn() throws Exception {
        openDeskWith(List.of(omission()), List.of());
        assertThat(textOf("review-findings"))
                .contains("may be missing words — if it reads well, press Accept")
                .doesNotContain("length ratio 0.29");

        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));

        assertThat(textOf("review-findings")).contains("omission · length ratio 0.29, expected 0.35–3.6");
    }

    @Test
    void suggestion_segmentWithoutAProposal_saysThereIsNone() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());

        openTab(2);

        assertThat(textOf("review-suggestion")).contains("No suggestion for this segment.");
    }
}
