package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeOrigin;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;
import ua.bookloom.ui.state.ChangeSection;
import ua.bookloom.ui.state.EditableTarget;
import ua.bookloom.ui.state.TargetEdits;

/**
 * The lines of the results list and the cells that draw them: a heading per kind of change with its count, then that
 * kind's rows. The Why column exists only when some row has a reason, and a row without one shows a dash. Cells log
 * nothing, since they refresh on every scroll.
 */
final class ResultRows {

    static final double ROW_HEIGHT = 40;

    private static final double TERM_WIDTH = 150;
    private static final double BADGE_WIDTH = 100;
    private static final double VALUE_WIDTH = 200;
    private static final double WHY_WIDTH = 170;
    private static final double ACTION_WIDTH = 100;
    private static final double SPACING = 8;
    private static final double TARGET_MIN_WIDTH = 80;
    private static final double REST_GAP = 4;
    // A text column gives up to this share of its width in a narrow window; its whole text is its tooltip.
    private static final double MIN_SHARE = 0.6;
    private static final String NO_REASON = "—";
    private static final String REVERTED_CHIP = "chip-neutral";
    private static final String REVERTED_ROW = "results-row-reverted";

    /** A heading line of the list: the key of its words, whose argument is the count. */
    record Heading(MessageKey title, int count) {}

    private final Messages messages;
    private final ChangeResults results;
    private final TargetEdits edits;
    private final boolean hasReasons;
    // What the person typed, by row, so a cell scrolled away and back shows it again.
    private final Map<Long, String> typed = new HashMap<>();

    ResultRows(final Messages messages, final ChangeResults results, final TargetEdits edits) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.results = Objects.requireNonNull(results, "results");
        this.edits = Objects.requireNonNull(edits, "edits");
        this.hasReasons = results.rows().stream().anyMatch(row -> row.reason() != null);
    }

    List<Object> items() {
        final List<Object> items = results.operation().isSectioned() ? bySection() : byKind();
        if (!results.untargeted().isEmpty()) {
            items.add(new Heading(
                    MessageKey.RESULTS_UNTARGETED, results.untargeted().size()));
            items.addAll(results.untargeted());
        }
        return items;
    }

    // One operation changed both tables: a heading per table, its rows below (added, removed, then changed).
    private List<Object> bySection() {
        final List<Object> items = new ArrayList<>();
        for (final ChangeSection section : ChangeSection.values()) {
            final List<ChangeRow> ofSection = results.rows().stream()
                    .filter(row -> row.section() == section)
                    .toList();
            if (!ofSection.isEmpty()) {
                items.add(new Heading(section.heading(), ofSection.size()));
                items.addAll(ofSection);
            }
        }
        return items;
    }

    private List<Object> byKind() {
        final List<Object> items = new ArrayList<>();
        for (final ChangeKind kind : ChangeKind.values()) {
            final List<ChangeRow> ofKind =
                    results.rows().stream().filter(row -> row.kind() == kind).toList();
            if (kind == ChangeKind.ADDED && hasTextOrigin(ofKind)) {
                group(items, MessageKey.RESULTS_FOUND_IN_TEXT, ofKind, ChangeOrigin.TEXT);
                group(items, MessageKey.RESULTS_ADDED_BY_MODEL, ofKind, ChangeOrigin.MODEL);
            } else if (!ofKind.isEmpty()) {
                items.add(new Heading(headingKey(kind), ofKind.size()));
                items.addAll(ofKind);
            }
        }
        return items;
    }

    // The rows found in the text are told apart from the model's only when both groups exist.
    private static boolean hasTextOrigin(final List<ChangeRow> added) {
        return added.stream().anyMatch(row -> row.origin() == ChangeOrigin.TEXT);
    }

    private static void group(
            final List<Object> items, final MessageKey title, final List<ChangeRow> added, final ChangeOrigin origin) {
        final List<ChangeRow> ofOrigin =
                added.stream().filter(row -> row.origin() == origin).toList();
        if (!ofOrigin.isEmpty()) {
            items.add(new Heading(title, ofOrigin.size()));
            items.addAll(ofOrigin);
        }
    }

    private static MessageKey headingKey(final ChangeKind kind) {
        return switch (kind) {
            case ADDED -> MessageKey.RESULTS_ADDED;
            case REMOVED -> MessageKey.RESULTS_REMOVED;
            case CHANGED -> MessageKey.RESULTS_CHANGED;
            case LEFT -> MessageKey.RESULTS_LEFT;
            case UNTARGETED -> MessageKey.RESULTS_UNTARGETED;
        };
    }

    private static MessageKey badgeKey(final ChangeKind kind) {
        return switch (kind) {
            case ADDED -> MessageKey.PROGRESS_RESULTS_BADGE_ADDED;
            case REMOVED -> MessageKey.PROGRESS_RESULTS_BADGE_REMOVED;
            case CHANGED -> MessageKey.PROGRESS_RESULTS_BADGE_CHANGED;
            case LEFT -> MessageKey.PROGRESS_RESULTS_BADGE_LEFT;
            case UNTARGETED -> MessageKey.PROGRESS_RESULTS_BADGE_UNTARGETED;
        };
    }

    private static String badgeStyle(final ChangeKind kind) {
        return switch (kind) {
            case ADDED -> "chip-ok";
            case REMOVED -> "chip-err";
            case CHANGED -> "chip-info";
            case LEFT, UNTARGETED -> "chip-warn";
        };
    }

    Node headings() {
        final HBox heads = new HBox(SPACING);
        heads.setId("results-columns");
        heads.setPadding(new Insets(0, SPACING, 0, SPACING));
        heads.getChildren()
                .add(flexible(text(messages.get(MessageKey.RESULTS_COLUMN_TERM), TERM_WIDTH, "results-head")));
        final Label change = text(messages.get(MessageKey.PROGRESS_RESULTS_COLUMN_CHANGE), BADGE_WIDTH, "results-head");
        change.setMinWidth(BADGE_WIDTH);
        heads.getChildren().add(change);
        heads.getChildren()
                .add(flexible(text(messages.get(MessageKey.RESULTS_COLUMN_BEFORE), VALUE_WIDTH, "results-head")));
        heads.getChildren()
                .add(flexible(text(messages.get(MessageKey.RESULTS_COLUMN_AFTER), VALUE_WIDTH, "results-head")));
        if (hasReasons) {
            heads.getChildren()
                    .add(flexible(text(messages.get(MessageKey.RESULTS_COLUMN_WHY), WHY_WIDTH, "results-head")));
        }
        // The same action room as a row, so headings and rows share the width alike and stay aligned.
        final Region action = new Region();
        action.setPrefWidth(ACTION_WIDTH);
        action.setMinWidth(Region.USE_PREF_SIZE);
        heads.getChildren().add(action);
        return heads;
    }

    ListCell<Object> cell() {
        return new ResultCell();
    }

    private static Label text(final String text, final double width, final String styleClass) {
        final Label label = new Label(text);
        label.setPrefWidth(width);
        label.setMinWidth(width * MIN_SHARE);
        label.setMaxWidth(width);
        label.getStyleClass().add(styleClass);
        return label;
    }

    // A text column keeps its starting width as the least it asks for and takes its share of any room left.
    private static <T extends Region> T flexible(final T column) {
        column.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private static void show(final Label label, final String text) {
        label.setText(text);
        Tips.install(label, text);
    }

    private final class ResultCell extends ListCell<Object> {

        private final Label heading = new Label();
        private final Label term = flexible(text("", TERM_WIDTH, "glossary-term"));
        private final Label badge = new Label();
        private final HBox badgeBox = new HBox(badge);
        private final Label before = flexible(text("", VALUE_WIDTH, "kv-value"));
        private final Label after = flexible(text("", VALUE_WIDTH, "kv-value"));
        private final TextField target = new TextField();
        private final Label rest = new Label();
        private final HBox afterBox = new HBox(REST_GAP, after, target, rest);
        private final Label why = flexible(text("", WHY_WIDTH, "muted"));
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
            buildTarget();
            final HBox action = new HBox(revert, reverted);
            action.setAlignment(Pos.CENTER_RIGHT);
            action.setPrefWidth(ACTION_WIDTH);
            action.setMinWidth(Region.USE_PREF_SIZE);
            row.setAlignment(Pos.CENTER_LEFT);
            badge.getStyleClass().add("chip");
            badgeBox.setPrefWidth(BADGE_WIDTH);
            badgeBox.setMinWidth(BADGE_WIDTH);
            badgeBox.setMaxWidth(BADGE_WIDTH);
            row.getChildren().addAll(term, badgeBox, before, afterBox);
            if (hasReasons) {
                row.getChildren().add(why);
            }
            row.getChildren().add(action);
            row.setPadding(new Insets(0, SPACING, 0, SPACING));
            setText(null);
        }

        // The After column is one box: the value as words, or, for a row that has a target to give, the field with
        // the row's other words beside it.
        private void buildTarget() {
            afterBox.setAlignment(Pos.CENTER_LEFT);
            afterBox.setPrefWidth(VALUE_WIDTH);
            afterBox.setMinWidth(VALUE_WIDTH * MIN_SHARE);
            flexible(afterBox);
            HBox.setHgrow(after, Priority.ALWAYS);
            HBox.setHgrow(target, Priority.ALWAYS);
            target.setId("results-target");
            target.setPromptText(messages.get(MessageKey.RESULTS_TARGET));
            target.setMinWidth(TARGET_MIN_WIDTH);
            Tips.install(messages, target, MessageKey.RESULTS_TARGET_TIP);
            rest.getStyleClass().add("muted");
            target.setOnAction(event -> {
                commit();
                event.consume();
            });
            target.focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    commit();
                }
            });
        }

        // Only a changed text is written, and only to the row the cell holds now.
        private void commit() {
            if (!(getItem() instanceof ChangeRow shown) || shown.editable() == null) {
                return;
            }
            final EditableTarget edit = shown.editable();
            final String text = target.getText().strip();
            if (text.equals(typed.getOrDefault(shown.id(), edit.target()))) {
                return;
            }
            typed.put(shown.id(), text);
            edits.set(shown.section(), edit.key(), text);
        }

        @Override
        protected void updateItem(final @Nullable Object item, final boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else if (item instanceof Heading head) {
                heading.setText(messages.get(head.title(), head.count()));
                setGraphic(heading);
            } else if (item instanceof ChangeRow shown) {
                fill(shown);
                setGraphic(row);
            }
        }

        // A reverted row reads as put back: a "reverted" chip, the value it now has again under After, its words
        // muted.
        private void fill(final ChangeRow shown) {
            final boolean done = results.reverted().contains(shown.id());
            show(term, shown.term());
            badge.setText(messages.get(done ? MessageKey.PROGRESS_RESULTS_BADGE_REVERTED : badgeKey(shown.kind())));
            badge.getStyleClass().removeIf(name -> name.startsWith("chip-"));
            badge.getStyleClass().add(done ? REVERTED_CHIP : badgeStyle(shown.kind()));
            show(before, shown.before());
            showAfter(shown, done);
            show(why, shown.reason() == null ? NO_REASON : shown.reason());
            row.getStyleClass().remove(REVERTED_ROW);
            if (done) {
                row.getStyleClass().add(REVERTED_ROW);
            }
            reverted.setVisible(done);
            reverted.setManaged(done);
            // A row the operation left as it was has nothing to put back.
            final boolean revertible = shown.kind().isRevertible();
            revert.setVisible(!done && revertible);
            revert.setManaged(!done && revertible);
            revert.setDisable(results.working().get());
            revert.setAccessibleText(messages.get(MessageKey.RESULTS_REVERT_FOR, shown.term()));
        }

        private void showAfter(final ChangeRow shown, final boolean done) {
            final EditableTarget edit = done ? null : shown.editable();
            setEditing(edit != null);
            if (edit == null) {
                show(after, done ? shown.before() : shown.after());
                return;
            }
            target.setText(typed.getOrDefault(shown.id(), edit.target()));
            rest.setText(edit.rest().isEmpty() ? "" : "· " + edit.rest());
            rest.setVisible(!edit.rest().isEmpty());
            rest.setManaged(rest.isVisible());
        }

        private void setEditing(final boolean editing) {
            after.setVisible(!editing);
            after.setManaged(!editing);
            target.setVisible(editing);
            target.setManaged(editing);
            rest.setVisible(editing);
            rest.setManaged(editing);
        }
    }
}
