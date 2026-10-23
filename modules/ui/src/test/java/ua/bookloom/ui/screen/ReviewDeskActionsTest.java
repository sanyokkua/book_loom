package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;

/** The desk's main button, its shortcuts and its More menu, each seen reaching the recording desk. */
class ReviewDeskActionsTest extends ReviewDeskTestBase {

    private static final String LOW_SCORE_ID = "ch05.xhtml:11";
    private static final String TYPED = "Він пішов геть.";

    private void selectRow(final int index, final String locator) throws Exception {
        onFx(() -> rowList().getSelectionModel().select(index));
        awaitFx(() -> locator.equals(selectedLocator()));
    }

    // IF the button never changed, THEN pressing Accept after typing would throw the typing away; once the text is
    // back to the model's it says Accept again.
    @Test
    void mainButton_typedThenRestored_flipsBetweenAcceptAndSaveMyEdit() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");
        assertThat(main().getText()).isEqualTo("✓ Accept & next");

        type(TYPED);
        assertThat(main().getText()).isEqualTo("✓ Save my edit & next");
        type(ReviewFixtures.MASKED_TARGET);

        assertThat(main().getText()).isEqualTo("✓ Accept & next");
    }

    @Test
    void ctrlEnter_onTheList_runsAcceptAndSelectsTheNextSegment() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");

        key(required("review-list"), KeyCode.ENTER, true, false, false);

        awaitFx(() -> desk.calls().contains("accept(" + projectId() + ", " + LOW_SCORE_ID + ")"));
        awaitFx(() -> "ch7 · p40".equals(selectedLocator()));
    }

    // IF ⌘+Enter did not work, THEN a Mac user would have no shortcut; it saves what was typed.
    @Test
    void metaEnter_afterTyping_runsSaveMyEdit() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");
        type(TYPED);

        key(target(), KeyCode.ENTER, false, false, true);

        awaitFx(() -> desk.calls().contains("saveEdit(" + projectId() + ", " + LOW_SCORE_ID + ", " + TYPED + ")"));
    }

    // IF a plain Enter on a focused row accepted a segment, THEN one stray key would decide it unread.
    @Test
    void plainEnter_onTheList_sendsNothingToTheDesk() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");

        key(required("review-list"), KeyCode.ENTER, false, false, false);

        assertThat(actionCalls()).isEmpty();
    }

    @Test
    void ctrlAltDownAndUp_moveToTheNextAndPreviousRow() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");

        key(required("review-list"), KeyCode.DOWN, true, true, false);
        awaitFx(() -> "ch7 · p40".equals(selectedLocator()));
        key(required("review-list"), KeyCode.UP, true, true, false);

        awaitFx(() -> "ch5 · p12".equals(selectedLocator()));
    }

    // IF Alt+↑ in the editor moved rows, THEN a person editing would be thrown to another segment.
    @Test
    void altUp_inTheEditor_keepsTheSelectedSegment() throws Exception {
        openDesk();
        selectRow(3, "ch7 · p40");

        key(target(), KeyCode.UP, false, true, false);

        assertThat(selectedLocator()).isEqualTo("ch7 · p40");
    }

    // IF Undo were offered with no edit, THEN it would promise to bring back what was never changed.
    @Test
    void more_uneditedAcceptedSegment_hasUndoDisabledAndTheRestEnabled() throws Exception {
        openDeskWith(List.of(), ReviewDeskFixtures.worthASecondLook());
        chooseFilter(ReviewFilter.SUSPICIOUS);
        awaitFx(() -> "ch2 · p04".equals(selectedLocator()));

        assertThat(fx(() -> moreItem("review-more-undo").isDisable())).isTrue();
        assertThat(fx(() -> List.of("review-more-later", "review-more-retry", "review-more-note").stream()
                        .map(id -> moreItem(id).isDisable())
                        .toList()))
                .containsOnly(false);
    }

    @Test
    void more_segmentWithTheirEdit_hasUndoEnabled() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withUserEdit()), List.of());

        assertThat(fx(() -> moreItem("review-more-undo").isDisable())).isFalse();
    }

    @Test
    void more_leaveForLater_sendsNothingMarksItLaterAndMovesOn() throws Exception {
        openDesk();

        chooseMore("review-more-later");

        assertThat(actionCalls()).isEmpty();
        assertThat(markerWords().getFirst()).isEqualTo("later");
        awaitFx(() -> "ch3 · p01".equals(selectedLocator()));
    }

    @Test
    void more_tryAgain_asksTheDeskWithNoNoteAndNoLowerTemperature() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());

        chooseMore("review-more-retry");

        awaitFx(() ->
                desk.calls().contains("retry(" + projectId() + ", ch07.xhtml:39, note=null, lowerTemperature=false)"));
    }

    // IF the note were asked in a second dialog, THEN it would replace the desk and the segment would be out of sight.
    @Test
    void more_tryAgainWithANote_asksInsideTheDeskAndSendsTheNoteAndTheCheckBox() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());

        chooseMore("review-more-note");
        final boolean askedInsideTheDesk = isShown("review-note-text") && isShown(DESK);
        onFx(() -> ((TextArea) required("review-note-text")).setText("keep it more formal"));
        onFx(() -> ((CheckBox) required("review-note-lower")).setSelected(true));
        onFx(() -> button("review-note-confirm").fire());

        assertThat(askedInsideTheDesk).isTrue();
        awaitFx(() -> desk.calls()
                .contains(
                        "retry(" + projectId() + ", ch07.xhtml:39, note=keep it more formal, lowerTemperature=true)"));
    }

    @Test
    void noteCancelled_closesThePanelAndRetriesNothing() throws Exception {
        openDeskWith(List.of(ReviewFixtures.nameIssue()), List.of());
        chooseMore("review-more-note");

        onFx(() -> button("review-note-cancel").fire());

        assertThat(isShown("review-note-text")).isFalse();
        assertThat(isShown(DESK)).isTrue();
        assertThat(actionCalls()).isEmpty();
    }
}
