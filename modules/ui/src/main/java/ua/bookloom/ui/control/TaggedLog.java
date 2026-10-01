package ua.bookloom.ui.control;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.StatusRole;

/**
 * The activity log: each entry shows the time it happened, its kind's tag and mark in the kind's role colour, its
 * catalogue text in the ordinary text colour so it reads on both themes, and {@code ×n} when it repeated.
 *
 * <p>The errors-only switch filters the mirror's entries without copying them. The cell logs nothing: it is refreshed
 * on every scroll and only changes its texts and role class as it is reused; its nodes are made once per cell.
 */
public final class TaggedLog extends ListView<LogEntry> {

    private static final int TAG_WIDTH = 5;
    private static final double PART_SPACING = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final List<String> ROLE_CLASSES =
            Arrays.stream(StatusRole.values()).map(StatusRole::styleClass).toList();

    private final BooleanProperty errorsOnly = new SimpleBooleanProperty(false);

    /**
     * Builds the list over the mirror's entries.
     *
     * @param entries the mirror's activity log; the list follows it
     * @param messages the catalogue the entries are worded from
     */
    public TaggedLog(final ObservableList<LogEntry> entries, final Messages messages) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(messages, "messages");
        final FilteredList<LogEntry> shown = new FilteredList<>(entries);
        errorsOnly.addListener((observed, was, now) ->
                shown.setPredicate(now ? entry -> entry.kind().isTrouble() : null));
        setItems(shown);
        getStyleClass().add("activity-log");
        setFocusTraversable(false);
        final Label empty = new Label(messages.get(MessageKey.TRANSLATING_LOG_EMPTY));
        empty.getStyleClass().add("muted");
        setPlaceholder(empty);
        setCellFactory(view -> new EntryCell(messages));
    }

    /**
     * Whether only the lines about something that went wrong are shown.
     *
     * @return the switch; FX thread only
     */
    public BooleanProperty errorsOnlyProperty() {
        return errorsOnly;
    }

    /**
     * The line an entry reads as, tag and mark first: what the cell shows and what a screen reader is given.
     *
     * @param entry the non-null entry
     * @param messages the catalogue it is worded from
     * @return the tag padded to one column, the mark and the catalogue text
     */
    public static String lineOf(final LogEntry entry, final Messages messages) {
        final LogKind kind = entry.kind();
        return String.format(Locale.ROOT, "%-" + TAG_WIDTH + "s %s ", kind.tag(), kind.mark())
                + textOf(entry, messages);
    }

    private static String textOf(final LogEntry entry, final Messages messages) {
        final Object[] args = entry.args().toArray();
        if (entry.kind().namesACall() && args.length > 0) {
            args[0] = messages.get(MessageKey.RUN_CALL_KIND, args[0]);
        }
        return messages.get(entry.messageKey(), args);
    }

    private static final class EntryCell extends ListCell<LogEntry> {

        private final Messages messages;
        private final Label time = part("log-time");
        private final Label tag = part("log-tag");
        private final Label body = part("log-text");
        private final Label repeats = part("log-repeats");
        private final Tooltip repeatTip = new Tooltip();
        private final HBox row = new HBox(PART_SPACING, time, tag, body, repeats);

        EntryCell(final Messages messages) {
            this.messages = messages;
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(body, Priority.ALWAYS);
            body.setMaxWidth(Double.MAX_VALUE);
            body.setMinWidth(0);
            repeats.setTooltip(repeatTip);
        }

        private static Label part(final String styleClass) {
            final Label label = new Label();
            label.getStyleClass().add(styleClass);
            label.setMinWidth(Label.USE_PREF_SIZE);
            return label;
        }

        @Override
        protected void updateItem(final @Nullable LogEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll(ROLE_CLASSES);
            tag.getStyleClass().removeAll(ROLE_CLASSES);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            final LogKind kind = entry.kind();
            time.setText(entry.time() == null ? "" : TIME.format(entry.time()));
            tag.setText(String.format(Locale.ROOT, "%-" + TAG_WIDTH + "s %s", kind.tag(), kind.mark()));
            tag.getStyleClass().add(entry.role().styleClass());
            body.setText(textOf(entry, messages));
            final boolean repeated = entry.repeats() > 1;
            repeats.setText(repeated ? "×" + entry.repeats() : "");
            repeats.setVisible(repeated);
            repeats.setManaged(repeated);
            repeatTip.setText(messages.get(MessageKey.TRANSLATING_LOG_REPEATS, entry.repeats()));
            getStyleClass().add(entry.role().styleClass());
            setGraphic(row);
            setAccessibleText(lineOf(entry, messages));
        }
    }
}
