package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.i18n.MessageKey;

/** The editor shows the stored marks by number, maps them back on save and holds Save while one is missing or doubled. */
class ReviewEditorMarksTest extends ReviewViewModelTestBase {

    private static final String ID = "ch05.xhtml:11";
    private static final String DISPLAYED = "Він відчинив ⟦0⟧старі⟦1⟧ двері.";

    private void type(final String text) {
        onFx(() -> {
            review.editorText().set(text);
            return null;
        });
    }

    private ReviewMainAction main() {
        return onFx(() -> review.mainAction().get());
    }

    private MarkStatus marks() {
        return onFx(() -> review.editor().marks().getValue());
    }

    private void selectedWithMarks() {
        buildReview();
        select(ID);
    }

    // IF the stored ⟦g0⟧ form reached the person, THEN they would edit a code they cannot read as a number.
    @Test
    void select_storedMarks_showAsNumbersCleanAndStoreBackUnchanged() {
        selectedWithMarks();

        assertThat(onFx(() -> review.editorText().get())).isEqualTo(DISPLAYED);
        assertThat(onFx(() -> review.editor().dirty().get())).isFalse();
        assertThat(onFx(() -> review.editor().storedText())).isEqualTo(MASKED_MACHINE);
        assertThat(marks().isComplete()).isTrue();
    }

    // The editor's source side is the same number form, so the filter and the strip compare like with like.
    @Test
    void sourceDisplay_selectedSegment_isTheSourceInNumberForm() {
        selectedWithMarks();

        assertThat(onFx(() -> review.editor().sourceDisplay().get())).isEqualTo("He opened the ⟦0⟧old⟦1⟧ door.");
    }

    // IF the number form reached the desk, THEN the stored text would hold marks the pipeline never wrote.
    @Test
    void saveEdit_wordsAddedBesideTheMarks_sendsTheStoredForm() {
        selectedWithMarks();
        type("Він рвучко відчинив ⟦0⟧старі⟦1⟧ двері.");

        press(review::saveEdit);

        assertThat(desk.calls())
                .contains("saveEdit(" + projectId + ", " + ID + ", Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.)");
    }

    // IF a broken edit could be sent, THEN a lost mark would cost the book its bold or its link.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Він відчинив ⟦0⟧старі двері.",
                "Він відчинив старі⟦1⟧ двері.",
                "Він відчинив старі двері.",
                "Він відчинив ⟦0⟧старі⟦1⟧⟦1⟧ двері."
            })
    void mainAction_aMarkMissingOrDoubled_isSaveMyEditDisabledAndSaveSendsNothing(final String edit) {
        selectedWithMarks();

        type(edit);
        press(review::saveEdit);

        assertThat(main().key()).isEqualTo(MessageKey.REVIEW_MAIN_SAVE);
        assertThat(main().enabled()).isFalse();
        assertThat(marks().isComplete()).isFalse();
        assertThat(desk.calls()).noneMatch(call -> call.startsWith("saveEdit("));
    }

    @Test
    void marks_oneMissingAndOneDoubled_namesEach() {
        selectedWithMarks();

        type("Він ⟦0⟧⟦0⟧ відчинив двері.");

        assertThat(marks().missing()).containsExactly("⟦1⟧");
        assertThat(marks().doubled()).containsExactly("⟦0⟧");
    }

    // IF pasting the mark back left the button off, THEN the person could never save.
    @Test
    void mainAction_theMissingMarkTypedBack_isSaveMyEditAndEnabled() {
        selectedWithMarks();
        type("Він відчинив ⟦0⟧старі двері.");

        type("Він рвучко відчинив ⟦0⟧старі⟦1⟧ двері.");

        assertThat(main().key()).isEqualTo(MessageKey.REVIEW_MAIN_SAVE);
        assertThat(main().enabled()).isTrue();
        assertThat(marks().isComplete()).isTrue();
    }

    // IF a list re-read reset the editor, THEN a count refresh would lose the typing and its marks status.
    @Test
    void select_sameSegmentAgainWhileBroken_keepsTheTypingAndItsMarkStatus() {
        selectedWithMarks();
        type("Він відчинив старі⟦1⟧ двері.");

        select(ID);

        assertThat(onFx(() -> review.editorText().get())).isEqualTo("Він відчинив старі⟦1⟧ двері.");
        assertThat(marks().missing()).containsExactly("⟦0⟧");
    }

    // IF a refused reply with a broken mark could be saved as it stands, THEN the old repair would be back.
    @Test
    void savable_rejectedReplyWithABrokenMark_isNotOffered() {
        buildReview();
        desk.willAnswerSegment(withRejected(lowScore(), "Він відчинив ⟦g0⟧старі двері."));

        select(ID);

        assertThat(onFx(() -> review.editor().changed().getValue())).isTrue();
        assertThat(onFx(() -> review.editor().savable().get())).isFalse();
        assertThat(marks().missing()).containsExactly("⟦1⟧");
    }
}
