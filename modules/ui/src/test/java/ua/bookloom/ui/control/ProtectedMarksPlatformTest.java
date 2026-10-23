package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;

/**
 * Documents how JavaFX 26 hands programmatic changes to a {@link TextFormatter} filter, which design D7 relies on:
 * what {@code setText} sends, what a one-character delete sends, and whether undo and redo reach the filter.
 */
@SuppressWarnings("NullAway.Init")
class ProtectedMarksPlatformTest extends FxTestBase {

    private final List<String> seen = new ArrayList<>();

    @Override
    public void start(final Stage stage) {
        // Built off-screen.
    }

    private static String text(final TextArea area) {
        return onFx(() -> area.getText());
    }

    private TextArea area(final String initial) {
        return onFx(() -> {
            final TextArea area = new TextArea(initial);
            area.setTextFormatter(new TextFormatter<String>(change -> {
                seen.add(change.getRangeStart() + ".." + change.getRangeEnd() + ":" + change.getText());
                return change;
            }));
            return area;
        });
    }

    // IF setText did not arrive as a whole-text replacement, THEN the filter could not tell a load from typing.
    @Test
    void setText_afterConstruction_arrivesAsAWholeTextReplacement() {
        final TextArea area = area("abc");
        seen.clear();

        onFx(() -> {
            area.setText("xy");
            return null;
        });

        assertThat(seen).contains("0..3:xy");
    }

    @Test
    void deletePreviousChar_midText_arrivesAsAOneCharacterRange() {
        final TextArea area = area("abc");
        onFx(() -> {
            area.positionCaret(2);
            return null;
        });
        seen.clear();

        onFx(() -> {
            area.deletePreviousChar();
            return null;
        });

        assertThat(seen).contains("1..2:");
    }

    // Real behaviour on JavaFX 26: undo and redo skip the filter (design D7 holds), so neither can be widened or
    // refused.
    @Test
    void undoAndRedo_afterATypedDelete_skipTheFilter() {
        final TextArea area = area("abc");
        onFx(() -> {
            area.positionCaret(2);
            area.deletePreviousChar();
            return null;
        });
        seen.clear();

        onFx(() -> {
            area.undo();
            return null;
        });
        final List<String> afterUndo = List.copyOf(seen);
        assertThat(text(area)).isEqualTo("abc");
        onFx(() -> {
            area.redo();
            return null;
        });

        assertThat(text(area)).isEqualTo("ac");
        assertThat(afterUndo).isEmpty();
        assertThat(seen).isEmpty();
    }

    @Test
    void replaceText_pasteLikeRange_arrivesWithTheInsertedText() {
        final TextArea area = area("abc");
        seen.clear();

        onFx(() -> {
            area.replaceText(1, 1, "⟦1⟧");
            return null;
        });

        assertThat(seen).contains("1..1:⟦1⟧");
        assertThat(text(area)).isEqualTo("a⟦1⟧bc");
    }
}
