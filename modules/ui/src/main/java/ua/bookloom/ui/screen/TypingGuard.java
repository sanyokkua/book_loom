package ua.bookloom.ui.screen;

import javafx.application.Platform;
import javafx.scene.control.TableView;

/**
 * Holds a table's redraw back while a person is typing in one of its cells: a redraw makes the cells again, which
 * would take the field they are typing in away from them. The redraw that was asked for meanwhile happens once, when
 * they stop.
 */
final class TypingGuard {

    private static final String KEY = "term-table-typing-guard";

    private final TableView<?> table;
    private boolean typing;
    private boolean waiting;

    private TypingGuard(final TableView<?> table) {
        this.table = table;
    }

    static TypingGuard of(final TableView<?> table) {
        return (TypingGuard) table.getProperties().computeIfAbsent(KEY, _ -> new TypingGuard(table));
    }

    /** Called on every redraw trigger: redraws now, or when the typing ends. */
    void redraw() {
        if (typing) {
            waiting = true;
        } else {
            table.refresh();
        }
    }

    /** A field of the table has text the person typed and not yet written ({@code true}), or has written it. */
    void typing(final boolean now) {
        typing = now;
        if (!now && waiting) {
            waiting = false;
            Platform.runLater(table::refresh);
        }
    }
}
