package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ReviewFixtures;

/** The questions the desk asks inside itself, so no dialog ever replaces it: Undo, and typing that is not saved. */
class ReviewDeskQuestionsTest extends ReviewDeskTestBase {

    private static final String TYPED = "Він пішов геть.";

    private String revertCall() {
        return "revert(" + projectId() + ", ch07.xhtml:39)";
    }

    // IF Undo threw the edit away unasked, THEN one stray click would lose the person's work; it asks inside the desk.
    @Test
    void undo_segmentWithTheirEdit_asksInsideTheDeskAndRevertsOnlyWhenConfirmed() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withUserEdit()), List.of());

        chooseMore("review-more-undo");
        final boolean askedInside = isShown("review-question") && isShown(DESK) && !isShown("confirm-card");
        final boolean revertedWhileAsking = desk.calls().contains(revertCall());
        onFx(() -> button("review-question-confirm").fire());

        assertThat(askedInside).isTrue();
        assertThat(revertedWhileAsking).isFalse();
        awaitFx(() -> desk.calls().contains(revertCall()));
    }

    @Test
    void undo_keepMyEdit_closesTheQuestionAndRevertsNothing() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withUserEdit()), List.of());
        chooseMore("review-more-undo");

        onFx(() -> button("review-question-keep").fire());

        assertThat(isShown("review-question")).isFalse();
        assertThat(desk.calls()).doesNotContain(revertCall());
    }

    // IF moving to another segment dropped the typing, THEN a stray click on › would lose it; the desk asks first and
    // Keep editing leaves the text and the segment alone.
    @Test
    void next_withUnsavedTyping_asksAndKeepEditingKeepsTheText() throws Exception {
        openDesk();
        type(TYPED);

        onFx(() -> button("review-next").fire());
        final boolean asked = isShown("review-question");
        onFx(() -> button("review-question-keep").fire());

        assertThat(asked).isTrue();
        assertThat(target().getText()).isEqualTo(TYPED);
        assertThat(selectedLocator()).isEqualTo("ch2 · p05");
    }

    // IF the question outlived the typing, THEN "Your edit … is not saved." would stand beside a footer saying Saved
    // (G1-1): answering it with Save my edit & next, or typing the text back, closes it.
    @Test
    void unsavedQuestion_savedWithTheMainButton_closes() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");
        type("Він пішов ⟦0⟧геть⟦1⟧.");
        onFx(() -> button("review-next").fire());
        final boolean asked = isShown("review-question");

        onFx(() -> main().fire());

        assertThat(asked).isTrue();
        awaitFx(() -> desk.calls().stream().anyMatch(call -> call.startsWith("saveEdit(")));
        awaitFx(() -> !isShown("review-question"));
    }

    @Test
    void unsavedQuestion_typingUndoneByHand_closes() throws Exception {
        openDesk();
        final String before = fx(() -> target().getText());
        type(TYPED);
        onFx(() -> button("review-next").fire());

        type(before);

        assertThat(isShown("review-question")).isFalse();
    }

    @Test
    void undoQuestion_nothingTyped_staysUntilAnswered() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withUserEdit()), List.of());
        final String before = fx(() -> target().getText());

        chooseMore("review-more-undo");
        type(TYPED);
        type(before);

        assertThat(isShown("review-question")).isTrue();
    }

    // IF a question outlived its segment, THEN Undo asked about one segment would revert the one picked after it.
    @Test
    void undoQuestion_anotherSegmentPicked_closes() throws Exception {
        openDeskWith(List.of(ReviewFixtures.withUserEdit(), ReviewFixtures.lowScore()), List.of());
        chooseMore("review-more-undo");
        final boolean asked = isShown("review-question");

        onFx(() -> button("review-next").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(asked).isTrue();
        assertThat(isShown("review-question")).isFalse();
    }

    @Test
    void next_withUnsavedTypingAndDiscard_movesOnWithoutSaving() throws Exception {
        openDesk();
        type(TYPED);
        onFx(() -> button("review-next").fire());

        onFx(() -> button("review-question-confirm").fire());

        awaitFx(() -> "ch3 · p01".equals(selectedLocator()));
        assertThat(actionCalls()).isEmpty();
    }

    // IF Escape closed the desk over typing, THEN the typing would be gone; it asks instead of closing.
    @Test
    void escape_withUnsavedTyping_asksInsteadOfClosing() throws Exception {
        openDesk();
        type(TYPED);

        key(required(DESK), KeyCode.ESCAPE, false, false, false);

        assertThat(isShown("review-question")).isTrue();
        assertThat(isShown(DESK)).isTrue();
    }

    @Test
    void escape_nothingTyped_closesTheDesk() throws Exception {
        openDeskWith(ReviewDeskFixtures.fourToReview(), List.of());

        key(required(DESK), KeyCode.ESCAPE, false, false, false);

        assertThat(isShown(DESK)).isFalse();
    }

    @Test
    void close_footerButton_closesTheDesk() throws Exception {
        openDesk();

        onFx(() -> button("review-close").fire());

        assertThat(isShown(DESK)).isFalse();
    }

    // IF a retry or Leave for later replaced the text unasked, THEN a menu click would lose the typing; both ask first
    // and send nothing to the desk until the person goes ahead.
    @ParameterizedTest
    @ValueSource(strings = {"review-more-retry", "review-more-later"})
    void moreMenu_replacingActionWithUnsavedTyping_asksFirstAndSendsNothing(final String item) throws Exception {
        openDesk();
        type(TYPED);

        chooseMore(item);

        assertThat(isShown("review-question")).isTrue();
        assertThat(target().getText()).isEqualTo(TYPED);
        assertThat(actionCalls()).isEmpty();
    }
}
