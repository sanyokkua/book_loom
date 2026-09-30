package ua.bookloom.ui.control;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.StatusRole;

/**
 * The activity log: each entry starts with its kind's tag, padded to one column, then its mark and its catalogue text,
 * in a monospace face set by the {@code activity-log} style class.
 *
 * <p>The cell logs nothing: it is refreshed on every scroll and only changes its text and role class as it is reused.
 */
public final class TaggedLog extends ListView<LogEntry> {

    private static final int TAG_WIDTH = 5;
    private static final List<String> ROLE_CLASSES =
            Arrays.stream(StatusRole.values()).map(StatusRole::styleClass).toList();

    /**
     * Builds the list over the mirror's entries.
     *
     * @param entries the mirror's activity log; the list follows it
     * @param messages the catalogue the entries are worded from
     */
    public TaggedLog(final ObservableList<LogEntry> entries, final Messages messages) {
        super(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(messages, "messages");
        getStyleClass().add("activity-log");
        setFocusTraversable(false);
        final Label empty = new Label(messages.get(MessageKey.TRANSLATING_LOG_EMPTY));
        empty.getStyleClass().add("muted");
        setPlaceholder(empty);
        setCellFactory(view -> new EntryCell(messages));
    }

    private static final class EntryCell extends ListCell<LogEntry> {

        private final Messages messages;

        EntryCell(final Messages messages) {
            this.messages = messages;
        }

        @Override
        protected void updateItem(final @Nullable LogEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll(ROLE_CLASSES);
            if (empty || entry == null) {
                setText(null);
                return;
            }
            final LogKind kind = entry.kind();
            setText(String.format("%-" + TAG_WIDTH + "s %s ", kind.tag(), kind.mark())
                    + messages.get(entry.messageKey(), entry.args().toArray()));
            getStyleClass().add(entry.role().styleClass());
        }
    }
}
