package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.function.Consumer;
import java.util.stream.Stream;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.state.MarkStatus;

/**
 * The translation editor's filter: each formatting mark is one unit to delete, replace or paste, and a mark the text
 * already holds is never pasted a second time. Pastes and cuts are driven through {@code replaceText}, which is what
 * the clipboard actions end in.
 */
@SuppressWarnings("NullAway.Init")
class ProtectedMarksTest extends FxTestBase {

    private static final String TWO = "Він відчинив ⟦0⟧старі⟦1⟧ двері.";
    private static final int ZERO = TWO.indexOf("⟦0⟧");
    private static final int ONE = TWO.indexOf("⟦1⟧");
    private static final int END = TWO.indexOf(" двері");
    private static final String THREE = "а ⟦0⟧б⟦1⟧в⟦2⟧";

    @Override
    public void start(final Stage stage) {
        // Built off-screen.
    }

    private static TextArea area(final String text, final String source) {
        return onFx(() -> {
            final TextArea area = new TextArea();
            ProtectedMarks.install(area, () -> source);
            area.setText(text);
            return area;
        });
    }

    private static void act(final TextArea area, final Consumer<TextArea> action) {
        onFx(() -> {
            action.accept(area);
            return null;
        });
    }

    private static String text(final TextArea area) {
        return onFx(() -> area.getText());
    }

    private static Arguments edit(
            final String name,
            final String text,
            final String source,
            final Consumer<TextArea> action,
            final String expected) {
        return Arguments.of(name, text, source, action, expected);
    }

    static Stream<Arguments> deletions() {
        return Stream.of(
                edit(
                        "backspace inside the mark",
                        TWO,
                        TWO,
                        a -> {
                            a.positionCaret(ONE + 2);
                            a.deletePreviousChar();
                        },
                        "Він відчинив ⟦0⟧старі двері."),
                edit(
                        "backspace right after the mark",
                        TWO,
                        TWO,
                        a -> {
                            a.positionCaret(ZERO + 3);
                            a.deletePreviousChar();
                        },
                        "Він відчинив старі⟦1⟧ двері."),
                edit(
                        "delete before the digit",
                        TWO,
                        TWO,
                        a -> {
                            a.positionCaret(ZERO + 1);
                            a.deleteNextChar();
                        },
                        "Він відчинив старі⟦1⟧ двері."));
    }

    static Stream<Arguments> replacements() {
        return Stream.of(
                edit(
                        "replacing half a mark replaces all of it",
                        TWO,
                        TWO,
                        a -> a.replaceText(ONE + 1, ONE + 2, "x"),
                        "Він відчинив ⟦0⟧старіx двері."),
                edit(
                        "typing inside a mark lands after it",
                        TWO,
                        TWO,
                        a -> a.replaceText(ONE + 1, ONE + 1, "x"),
                        "Він відчинив ⟦0⟧старі⟦1⟧x двері."));
    }

    static Stream<Arguments> cutAndPaste() {
        return Stream.of(
                edit(
                        "cut then paste elsewhere",
                        TWO,
                        TWO,
                        a -> {
                            a.replaceText(ONE, ONE + 3, "");
                            a.replaceText(END + 3, END + 3, "⟦1⟧");
                        },
                        "Він відчинив ⟦0⟧старі двері⟦1⟧."),
                edit(
                        "paste brings back only the missing marks",
                        "Він відчинив ⟦1⟧ двері.",
                        TWO,
                        a -> a.replaceText(13, 13, "⟦0⟧ ⟦1⟧ старі"),
                        "Він відчинив ⟦0⟧ старі⟦1⟧ двері."));
    }

    static Stream<Arguments> pastes() {
        return Stream.of(
                edit(
                        "paste of two marks when the second is present",
                        "а ⟦0⟧б⟦2⟧",
                        THREE,
                        a -> a.replaceText(6, 6, "⟦1⟧⟦2⟧"),
                        "а ⟦0⟧б⟦1⟧⟦2⟧"),
                edit("paste of a mark the source lacks", TWO, TWO, a -> a.replaceText(0, 0, "⟦9⟧"), TWO),
                edit("paste of a mark already present", TWO, TWO, a -> a.replaceText(0, 0, "⟦0⟧"), TWO));
    }

    // IF a mark could be cut in half, THEN the formatting it stands for would be lost without the person seeing it.
    @ParameterizedTest(name = "{0}")
    @MethodSource({"deletions", "replacements", "cutAndPaste", "pastes"})
    void edit_markedText_keepsEveryMarkWholeAndSingle(
            final String name,
            final String text,
            final String source,
            final Consumer<TextArea> action,
            final String expected) {
        final TextArea area = area(text, source);

        act(area, action);

        assertThat(text(area)).isEqualTo(expected);
    }

    // IF the caret stayed inside the deleted mark, THEN the next keystroke would land in the wrong place.
    @Test
    void backspace_insideAMark_leavesTheCaretWhereTheMarkWas() {
        final TextArea area = area(TWO, TWO);

        act(area, a -> {
            a.positionCaret(TWO.indexOf("⟦1⟧") + 2);
            a.deletePreviousChar();
        });

        assertThat(onFx(() -> area.getCaretPosition())).isEqualTo(TWO.indexOf("⟦1⟧"));
    }

    // IF the filter touched whole-text writes, THEN loading a segment would be refused or repaired.
    @ParameterizedTest
    @MethodSource("loads")
    void setText_anyText_passesUnchanged(final String loaded) {
        final TextArea area = area(TWO, TWO);

        act(area, a -> a.setText(loaded));

        assertThat(text(area)).isEqualTo(loaded);
    }

    static Stream<String> loads() {
        return Stream.of("⟦1⟧ ⟦1⟧", "", "no marks", "⟦9⟧ ⟦0⟧⟦1⟧", "⟦0⟧");
    }

    // IF undo could restore half a mark, THEN a person could not get a deleted mark back; both directions are whole.
    @Test
    void undoAndRedo_afterAWholeMarkDelete_restoreAndRemoveTheWholeMark() {
        final TextArea area = area(TWO, TWO);
        act(area, a -> {
            a.positionCaret(TWO.indexOf("⟦0⟧") + 2);
            a.deletePreviousChar();
        });
        assertThat(text(area)).isEqualTo("Він відчинив старі⟦1⟧ двері.");

        act(area, TextArea::undo);
        assertThat(text(area)).isEqualTo(TWO);

        act(area, TextArea::redo);
        assertThat(text(area)).isEqualTo("Він відчинив старі⟦1⟧ двері.");
    }

    // IF a hand-typed mark were refused, THEN a person could not retype a mark; it is allowed and counted as doubled.
    @Test
    void typing_aMarkByHandBesideAPresentOne_isAllowedAndShowsAsDoubled() {
        final TextArea area = area("а ⟦1⟧", "а ⟦1⟧");

        act(area, a -> {
            a.replaceText(0, 0, "⟦");
            a.replaceText(1, 1, "1");
            a.replaceText(2, 2, "⟧");
        });

        assertThat(text(area)).isEqualTo("⟦1⟧а ⟦1⟧");
        assertThat(MarkStatus.of("а ⟦1⟧", text(area)).doubled()).containsExactly("⟦1⟧");
    }
}
