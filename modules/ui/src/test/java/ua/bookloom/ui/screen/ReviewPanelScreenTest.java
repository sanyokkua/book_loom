package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/** The review panel inside Translating, read from the real scene with the recording desk behind it. */
class ReviewPanelScreenTest extends TranslatingScreenTestBase {

    private static final String REVIEW = "translating-review-flagged";

    private void script(final int flagged, final SegmentView... views) {
        desk.willAnswerQueue(List.of(views));
        for (final SegmentView view : views) {
            desk.willAnswerSegment(view);
        }
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, flagged, 0, 0, 0, 0, 0));
    }

    private void publishFlagged(final int count) {
        mirror().live()
                .publishFlaggedQueue(java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openPanelWith(final RunState state, final int flagged, final SegmentView... views) throws Exception {
        readyToStart();
        script(flagged, views);
        showTranslating();
        publish(state);
        publishFlagged(flagged);
        awaitFx(() -> button(REVIEW).getText().equals("Review flagged (" + flagged + ")"));
        onFx(() -> button(REVIEW).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private ListView<ReviewRow> rowList() {
        return (ListView<ReviewRow>) required("review-list");
    }

    private void selectFirstRow() throws TimeoutException {
        awaitFx(() -> !rowList().getItems().isEmpty());
        onFx(() -> rowList().getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    private List<String> rowTexts() {
        return rowList().lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(cell -> cell.getItem() != null)
                .map(cell -> String.join(
                        " ",
                        ((javafx.scene.Parent) cell.getGraphic())
                                .getChildrenUnmodifiable().stream()
                                        .filter(javafx.scene.control.Label.class::isInstance)
                                        .map(child -> ((javafx.scene.control.Label) child).getText())
                                        .filter(text -> !text.isEmpty())
                                        .toList()))
                .toList();
    }

    // IF the panel showed before the person asked, THEN it would push the run's figures off the screen.
    @Test
    void panel_beforeReviewFlaggedIsPressed_isNotShown() {
        showTranslating();
        publish(RunState.PAUSED);

        assertThat(isShown("review-panel")).isFalse();
    }

    // IF a clean run showed an empty list with no words, THEN a person would think the panel was broken.
    @Test
    void emptyState_cleanRun_showsTheTextAndBackToProgress() throws Exception {
        openPanelWith(RunState.COMPLETED, 0);

        assertThat(isShown("review-panel")).isTrue();
        assertThat(labelText("review-empty-text")).isEqualTo("Nothing flagged — every chunk cleared the checks");
        assertThat(button("review-back").getText()).isEqualTo("Back to progress");
    }

    @Test
    void reviewFlagged_threeFlagged_opensThePanelAndBackToProgressClosesIt() throws Exception {
        openPanelWith(
                RunState.PAUSED,
                3,
                ReviewFixtures.lowScore(),
                ReviewFixtures.nameIssue(),
                ReviewFixtures.wrongLanguage());

        assertThat(isShown("review-panel")).isTrue();
        onFx(() -> button("review-back").fire());

        assertThat(isShown("review-panel")).isFalse();
    }

    @Test
    void list_threeFlagged_readsLocatorAndBadgeOfEachRow() throws Exception {
        openPanelWith(
                RunState.PAUSED,
                3,
                ReviewFixtures.lowScore(),
                ReviewFixtures.nameIssue(),
                ReviewFixtures.wrongLanguage());
        awaitFx(() -> rowList().getItems().size() == 3);

        assertThat(rowTexts()).containsExactly("ch5 · p12 low score", "ch7 · p40 name", "ch9 · p03 wrong lang?");
    }

    // IF the list measured every row as it scrolled, THEN a long review list would stutter; its rows share one height.
    @Test
    void list_oneFlagged_rowsShareOneFixedHeight() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.lowScore());
        awaitFx(() -> rowList().getItems().size() == 1);

        assertThat(rowList().getFixedCellSize()).isEqualTo(34.0);
    }

    // IF All segments did not open a decided segment, THEN an Unattended run could never be spot-checked.
    @Test
    void allSegments_afterAnUnattendedRun_opensAnAcceptedSegmentInTheCompare() throws Exception {
        openPanelWith(RunState.COMPLETED, 0, ReviewFixtures.accepted());

        onFx(() -> ((javafx.scene.control.ToggleButton) required("review-chip-all-segments")).fire());
        selectFirstRow();

        assertThat(((TextArea) required("review-target")).getText()).isEqualTo(ReviewFixtures.MASKED_TARGET);
        assertThat(desk.calls()).contains("queue(" + projectIdOfOpenBook() + ", ALL_SEGMENTS)");
    }

    private String projectIdOfOpenBook() {
        return ThemeTestSupport.onFx(() -> injector.getInstance(ua.bookloom.ui.state.CurrentProject.class)
                .book()
                .get()
                .projectId());
    }

    // IF the source could be typed into, THEN a person could corrupt what the model is asked to translate.
    @Test
    void compare_machineTargetWithTokens_showsItAsTypedAndOnlyTheTargetIsEditable() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.machineTargetOnly());
        selectFirstRow();

        assertThat(((TextArea) required("review-source")).isEditable()).isFalse();
        assertThat(((TextArea) required("review-source")).getText()).isEqualTo("Gale opened the ⟦g0⟧old⟦g1⟧ door.");
        assertThat(((TextArea) required("review-target")).isEditable()).isTrue();
        assertThat(((TextArea) required("review-target")).getText()).isEqualTo("Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(labelText("review-editable-mark")).isEqualTo("EDITABLE");
    }

    @Test
    void compare_judgedSegmentWithContext_showsTheJudgeBadgeAndTheContextLine() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.lowScore());
        selectFirstRow();

        assertThat(labelText("review-judge")).isEqualTo("judge 0.58");
        assertThat(((TitledPane) required("review-context")).getText())
                .isEqualTo("Context · brief · glossary(2) · previous paragraph · summary");
    }

    // IF the review's context stayed a one-line list of part names, THEN a reviewer could not read what the model was
    // given; opened, it shows the earlier translation, the summary and the names at their full height.
    @Test
    void compare_contextOpened_showsWhatTheDraftWasGivenAtItsHeight() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.lowScore());
        selectFirstRow();

        onFx(() -> ((TitledPane) required("review-context")).setExpanded(true));
        onFx(() -> {});

        final ScrollPane body = (ScrollPane) required("review-context-body");
        assertThat(body.lookupAll(".context-quote").stream().map(node -> ((Label) node).getText()))
                .containsExactly("Раніше в тексті.");
        assertThat(body.lookupAll(".context-lock")).hasSize(1);
        final double natural = ThemeTestSupport.onFx(() -> {
            final Region sections = (Region) body.getContent();
            return sections.prefHeight(sections.getWidth());
        });
        assertThat(ThemeTestSupport.onFx(body::getHeight)).isGreaterThanOrEqualTo(natural - 1);
    }

    // IF a Fast-dial segment showed a judge badge, THEN it would claim a score nothing produced.
    @Test
    void compare_segmentWithoutJudgeScore_showsNoJudgeBadge() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        assertThat(isShown("review-judge")).isFalse();
    }

    @Test
    void findings_scriptFinding_listsWhoRaisedIt() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.wrongLanguage());
        selectFirstRow();

        assertThat(textOf("review-findings"))
                .contains("language")
                .contains("Looks Russian")
                .contains("script");
    }

    // IF an action stayed live while the model translated, THEN it would race the run for the same segment.
    @Test
    void actions_runIsRunning_areAllUnavailable() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();
        assertThat(button("review-accept").isDisabled()).isFalse();

        publish(RunState.RUNNING);

        assertThat(List.of("review-accept", "review-save", "review-revert", "review-skip"))
                .allSatisfy(id -> assertThat(button(id).isDisabled()).as(id).isTrue());
    }

    // IF typing were allowed while Save stays off, THEN the person would type into a box that cannot keep it.
    @Test
    void target_runIsRunning_isReadOnlyAndSaysWhenReviewIsAvailable() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        publish(RunState.RUNNING);

        assertThat(((TextArea) required("review-target")).isEditable()).isFalse();
        assertThat(isShown("review-locked")).isTrue();
        assertThat(labelText("review-locked")).startsWith("Review is available when the run pauses.");
    }

    // A refused save names the missing tokens as chips; a chip puts its token back at the cursor.
    @Test
    void saveEdit_refusedForATokenAndChipPressed_insertsTheTokenAtTheCursor() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();
        final TextArea target = (TextArea) required("review-target");
        onFx(() -> target.setText("Без токенів."));
        desk.willAnswer(Result.err(AppError.of(ErrorCode.validation, "Edit refused", "Placeholders differ.")));

        onFx(() -> button("review-save").fire());
        awaitFx(() -> isShown("review-token-banner"));
        final Button chip = (Button) ((javafx.scene.layout.FlowPane) required("review-token-chips"))
                .getChildren()
                .getFirst();
        onFx(() -> {
            target.positionCaret(0);
            chip.fire();
        });

        assertThat(target.getText()).isEqualTo(chip.getText() + "Без токенів.");
    }

    @Test
    void editing_targetTyped_disablesAcceptAndShowsTheHint() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        onFx(() -> ((TextArea) required("review-target")).setText("Гейл відчинив двері."));

        assertThat(button("review-accept").isDisabled()).isTrue();
        assertThat(labelText("review-hint")).isEqualTo("Editing disables Accept until you Save or Revert.");
    }

    // IF a long segment made a pane wider than the window, THEN the person would scroll sideways to read a finding.
    @Test
    void panel_longSegmentAtMinimumWidth_needsNoSidewaysScrolling() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.longSegment());
        awaitFx(() -> !rowList().getItems().isEmpty());
        onFx(() -> rowList().getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());

        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);

        final javafx.scene.control.ScrollPane pane = (javafx.scene.control.ScrollPane) required("shell-content-scroll");
        assertThat(pane.getContent().getLayoutBounds().getWidth())
                .isLessThanOrEqualTo(pane.getViewportBounds().getWidth());
    }

    // IF the count were read only when the panel opened, THEN the button on a first visit would read zero flagged.
    @Test
    void reviewFlagged_firstVisit_namesTheCountTheDeskHolds() throws Exception {
        readyToStart();
        script(4, ReviewFixtures.nameIssue());
        showTranslating();
        publish(RunState.PAUSED);

        awaitFx(() -> button(REVIEW).getText().equals("Review flagged (4)"));
    }

    // IF Resume stayed live during a retry, THEN the run and the retry would race for the model.
    @Test
    void retryInFlight_pausedRun_disablesResumeOnTheScreenAndInTheTitleBar() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        mirror().publishRunStarted("Frankenstein.epub");
        publish(RunState.PAUSED);
        assertThat(button("translating-resume").isDisabled()).isFalse();
        assertThat(button("shell-run-control").isDisabled()).isFalse();

        mirror().review().publishRetryInFlight(true);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(button("translating-resume").isDisabled()).isTrue();
        assertThat(button("shell-run-control").isDisabled()).isTrue();
    }

    @Test
    void retry_plainButton_asksTheDeskWithNoNoteAndNoLowerTemperature() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        onFx(() -> button("review-retry").fire());
        WaitForAsyncUtils.waitForFxEvents();

        awaitFx(() -> desk.calls()
                .contains("retry(" + projectIdOfOpenBook() + ", ch07.xhtml:39, note=null, lowerTemperature=false)"));
    }

    @Test
    void retryWithNote_dialogRetry_asksTheDeskWithTheTypedNoteAndTheCheckBox() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        onFx(() -> button("review-retry-note").fire());
        onFx(() -> ((TextArea) required("retry-note-text")).setText("keep it more formal"));
        onFx(() -> ((javafx.scene.control.CheckBox) required("retry-note-lower")).setSelected(true));
        onFx(() -> button("retry-note-confirm").fire());

        awaitFx(() -> desk.calls()
                .contains("retry("
                        + projectIdOfOpenBook()
                        + ", ch07.xhtml:39, note=keep it more formal, lowerTemperature=true)"));
    }

    // IF a proposal applied itself, THEN the person's text would change before they had read it.
    @Test
    void acceptProposal_segmentWithProposal_isShownBesideTheTextAndAppliedOnlyWhenPressed() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.withProposal());
        selectFirstRow();
        final String call = "acceptProposal(" + projectIdOfOpenBook() + ", ch03.xhtml:2)";

        assertThat(textOf("review-proposal")).contains("Він пішов.");
        assertThat(desk.calls()).doesNotContain(call);

        onFx(() -> button("review-accept-proposal").fire());

        awaitFx(() -> desk.calls().contains(call));
    }

    @Test
    void acceptProposal_segmentWithoutProposal_isNotShown() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.nameIssue());
        selectFirstRow();

        assertThat(isShown("review-proposal")).isFalse();
    }
}
