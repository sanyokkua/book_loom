package ua.bookloom.ui.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;

/**
 * The lines of the results list and the cells that draw them: a heading per kind of change with its count, then that
 * kind's rows. The Why column exists only when some row has a reason, and a row without one shows a dash. Cells log
 * nothing, since they refresh on every scroll.
 */
final class ResultRows {

    static final double ROW_HEIGHT = 40;
    static final double WIDTH = 820;

    private static final double TERM_WIDTH = 150;
    private static final double VALUE_WIDTH = 200;
    private static final double WHY_WIDTH = 170;
    private static final double ACTION_WIDTH = 100;
    private static final double SPACING = 8;
    private static final String NO_REASON = "—";

    /** A heading line of the list. */
    record Heading(ChangeKind kind, int count) {}

    private final Messages messages;
    private final ChangeResults results;
    private final boolean hasReasons;

    ResultRows(final Messages messages, final ChangeResults results) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.results = Objects.requireNonNull(results, "results");
        this.hasReasons = results.rows().stream().anyMatch(row -> row.reason() != null);
    }

    List<Object> items() {
        final List<Object> items = new ArrayList<>();
        for (final ChangeKind kind : ChangeKind.values()) {
            final List<ChangeRow> ofKind =
                    results.rows().stream().filter(row -> row.kind() == kind).toList();
            if (!ofKind.isEmpty()) {
                items.add(new Heading(kind, ofKind.size()));
                items.addAll(ofKind);
            }
        }
        return items;
    }

    Node headings() {
        final HBox heads = new HBox(SPACING);
        heads.setId("results-columns");
        heads.setPadding(new Insets(0, SPACING, 0, SPACING));
        heads.getChildren().add(text(messages.get(MessageKey.RESULTS_COLUMN_TERM), TERM_WIDTH, "kv-key"));
        heads.getChildren().add(text(messages.get(MessageKey.RESULTS_COLUMN_BEFORE), VALUE_WIDTH, "kv-key"));
        heads.getChildren().add(text(messages.get(MessageKey.RESULTS_COLUMN_AFTER), VALUE_WIDTH, "kv-key"));
        if (hasReasons) {
            heads.getChildren().add(text(messages.get(MessageKey.RESULTS_COLUMN_WHY), WHY_WIDTH, "kv-key"));
        }
        return heads;
    }

    ListCell<Object> cell() {
        return new ResultCell();
    }

    private static Label text(final String text, final double width, final String styleClass) {
        final Label label = new Label(text);
        label.setPrefWidth(width);
        label.setMinWidth(width);
        label.setMaxWidth(width);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static void show(final Label label, final String text) {
        label.setText(text);
        label.setTooltip(new Tooltip(text));
    }

    private final class ResultCell extends ListCell<Object> {

        private final Label heading = new Label();
        private final Label term = text("", TERM_WIDTH, "glossary-term");
        private final Label before = text("", VALUE_WIDTH, "kv-value");
        private final Label after = text("", VALUE_WIDTH, "kv-value");
        private final Label why = text("", WHY_WIDTH, "muted");
        private final Label reverted = new Label(messages.get(MessageKey.RESULTS_REVERTED));
        private final Button revert = Tips.installOnHover(
                messages, new Button(messages.get(MessageKey.RESULTS_REVERT)), MessageKey.RESULTS_REVERT_TIP);
        private final HBox row = new HBox(SPACING);

        ResultCell() {
            heading.getStyleClass().add("card-title");
            reverted.getStyleClass().add("muted");
            revert.getStyleClass().add("btn-ghost");
            revert.setMinWidth(Region.USE_PREF_SIZE);
            revert.setOnAction(event -> {
                if (getItem() instanceof ChangeRow shown) {
                    results.revert(shown.id());
                }
            });
            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            final HBox action = new HBox(revert, reverted);
            action.setAlignment(Pos.CENTER_RIGHT);
            action.setPrefWidth(ACTION_WIDTH);
            action.setMinWidth(Region.USE_PREF_SIZE);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(term, before, after);
            if (hasReasons) {
                row.getChildren().add(why);
            }
            row.getChildren().addAll(spacer, action);
            row.setPadding(new Insets(0, SPACING, 0, SPACING));
            setText(null);
        }

        @Override
        protected void updateItem(final @Nullable Object item, final boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else if (item instanceof Heading head) {
                heading.setText(headingText(head));
                setGraphic(heading);
            } else if (item instanceof ChangeRow shown) {
                fill(shown);
                setGraphic(row);
            }
        }

        private String headingText(final Heading head) {
            return messages.get(
                    switch (head.kind()) {
                        case ADDED -> MessageKey.RESULTS_ADDED;
                        case REMOVED -> MessageKey.RESULTS_REMOVED;
                        case CHANGED -> MessageKey.RESULTS_CHANGED;
                    },
                    head.count());
        }

        private void fill(final ChangeRow shown) {
            show(term, shown.term());
            show(before, shown.before());
            show(after, shown.after());
            show(why, shown.reason() == null ? NO_REASON : shown.reason());
            final boolean done = results.reverted().contains(shown.id());
            reverted.setVisible(done);
            reverted.setManaged(done);
            revert.setVisible(!done);
            revert.setManaged(!done);
            revert.setDisable(results.working().get());
            revert.setAccessibleText(messages.get(MessageKey.RESULTS_REVERT_FOR, shown.term()));
        }
    }
}
