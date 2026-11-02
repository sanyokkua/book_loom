package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;

/** Typing in the Names &amp; style tables edits the row the person chose and nothing else. */
class GlossaryTableEditingTest extends TermTableCardTestBase {

    private static final int TERM_COLUMN = 0;
    private static final int TARGET_COLUMN = 3;

    private TextField targetField(final String term) {
        final TableCell<?, ?> cell = glossaryCell(term, TARGET_COLUMN);
        return ThemeTestSupport.onFx(() -> (TextField) cell.getGraphic().lookup(".text-field"));
    }

    private static MouseEvent press() {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                5,
                5,
                5,
                5,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }

    // IF a refresh (a flag recount, a usage count arriving) re-filled the field being typed in, THEN the half-typed
    // target would vanish under the person's hands; the field keeps what was typed.
    @Test
    void targetField_refreshedWhileTyping_keepsWhatWasTyped() throws Exception {
        showScreen("en", 1400, 900, ROSALIND, HALE, JOHN);
        final TextField hale = targetField("Hale");
        onFx(hale::requestFocus);
        onFx(() -> hale.setText("Гейл"));
        onFx(this::redrawTheTable);

        final String typed = ThemeTestSupport.onFx(hale::getText);
        assertThat(typed).isEqualTo("Гейл");
    }

    // IF the pressed row did not take the keyboard from the field of the row typed in before, THEN the next letters
    // would still land in that field; a press on another row's term hands the keyboard to the table.
    @Test
    void rowPress_afterTypingInAnotherRowsField_movesTheKeyboardToTheTable() throws Exception {
        showScreen("en", 1400, 900, ROSALIND, HALE, JOHN);
        final TextField hale = targetField("Hale");
        final TableCell<?, ?> rosalindTerm = glossaryCell("Rosalind", TERM_COLUMN);
        onFx(hale::requestFocus);
        final boolean had = ThemeTestSupport.onFx(hale::isFocused);
        assertThat(had).isTrue();

        onFx(() -> rosalindTerm.fireEvent(press()));

        final boolean kept = ThemeTestSupport.onFx(hale::isFocused);
        assertThat(kept).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> table("names-style").isFocused()))
                .isTrue();
    }

    // IF Enter or F2 on a selected row did nothing, THEN a keyboard user could not reach the field of the row they
    // chose; the field of the selected row takes the keyboard.
    @Test
    void f2_onTheSelectedRow_focusesThatRowsTargetField() throws Exception {
        showScreen("en", 1400, 900, ROSALIND, HALE, JOHN);
        final TextField hale = targetField("Hale");
        final TableCell<?, ?> haleTerm = glossaryCell("Hale", TERM_COLUMN);
        onFx(() -> haleTerm.fireEvent(press()));
        onFx(() -> table("names-style").getSelectionModel().select(selectedIndexOf("Hale")));
        onFx(() -> table("names-style")
                .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F2, false, false, false, false)));

        final boolean focused = ThemeTestSupport.onFx(hale::isFocused);
        assertThat(focused).isTrue();
    }

    private int selectedIndexOf(final String term) {
        return ThemeTestSupport.onFx(() -> {
            final var items = table("names-style").getItems();
            return java.util.stream.IntStream.range(0, items.size())
                    .filter(at -> ((ua.bookloom.api.project.GlossaryEntry) items.get(at))
                            .term()
                            .equals(term))
                    .findFirst()
                    .orElseThrow();
        });
    }

    // What a flag recount or a usage count does to the table: the listener the table holds for its triggers.
    @SuppressWarnings("unchecked")
    private void redrawTheTable() {
        final var listener = (javafx.beans.value.ChangeListener<Number>) java.util.Objects.requireNonNull(
                table("names-style").getProperties().get("term-table-card-listener-0"));
        listener.changed(null, 0, 1);
    }
}
