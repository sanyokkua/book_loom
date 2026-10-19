package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.TraversalDirection;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes Tab leave a text area instead of typing a tab character into it. A JavaFX text area keeps Tab for itself, so a
 * keyboard user who Tabs into a note field can never Tab out of it again (only Ctrl+Tab, which nobody guesses). No
 * text BookLoom asks for — a voice note, a retry note, a translation — needs a tab character. Shift+Tab goes back.
 */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TabMovesFocus {

    /**
     * Installs the behaviour. FX thread only.
     *
     * @param area the text area; it keeps every other key
     * @return the same area, for chaining where it is built
     */
    public static TextArea install(final TextArea area) {
        Objects.requireNonNull(area, "area");
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.TAB || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
                return;
            }
            final TraversalDirection direction =
                    event.isShiftDown() ? TraversalDirection.PREVIOUS : TraversalDirection.NEXT;
            log.debug("tab leaves text area {} towards {}", area.getId(), direction);
            event.consume();
            area.requestFocusTraversal(direction);
        });
        return area;
    }
}
