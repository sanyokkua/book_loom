package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.DecisionRow;
import ua.bookloom.ui.state.ProgressDialogModel;

/**
 * The "What it decides" group of the fill-in dialog above its "Steps" group: one row per field the suggestion decides,
 * with the value, how many samples agree and a mark while the last step has not decided or when the samples did not
 * agree. The group shows only once there is a row. Selecting a row hands it to the dialog, which lists each sample's
 * answer beside its quote. Nothing here logs: the rows change up to ten times a second.
 *
 * <p>A row's text is on its cell, so a screen reader and a test read it as one line of text.
 */
final class DecisionPane extends VBox {

    private static final double SPACING = 6;
    private static final double ROW_HEIGHT = 50;
    private static final int ROWS = 4;
    private static final String PART_JOIN = " · ";
    private static final String NO_VALUE = "—";

    private final Messages messages;
    private final ListView<DecisionRow> list = new ListView<>();
    private final Label stepsHeading;
    private List<DecisionRow> shown = List.of();
    private boolean shownHeld;

    DecisionPane(final ProgressDialogModel model, final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(model, "model");
        getStyleClass().add("progress-decisions-pane");
        final Label heading = heading(MessageKey.BRIEF_DECIDES_GROUP, "progress-decides-heading");
        stepsHeading = heading(MessageKey.BRIEF_STEPS_GROUP, "progress-steps-heading");
        list.setId("progress-decisions");
        list.getStyleClass().add("progress-decisions");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setPrefHeight(ROW_HEIGHT * ROWS + 2);
        list.setMinHeight(ROW_HEIGHT * ROWS + 2);
        list.setCellFactory(view -> new RowCell());
        getChildren().addAll(heading, list);
        model.activity().addListener((observed, was, now) -> refresh(now));
        model.follow().addListener((observed, was, now) -> {
            if (!now) {
                list.getSelectionModel().clearSelection();
            }
        });
        refresh(model.activity().get());
    }

    /** {@return the row the person selected, or {@code null} when none is} */
    ReadOnlyObjectProperty<@Nullable DecisionRow> selected() {
        return list.getSelectionModel().selectedItemProperty();
    }

    /** {@return the heading that opens the steps, which sits under the rows and shows with them} */
    Label stepsHeading() {
        return stepsHeading;
    }

    private Label heading(final MessageKey key, final String id) {
        final Label label = new Label(messages.get(key));
        label.setId(id);
        label.getStyleClass().add("progress-heading");
        return label;
    }

    private void refresh(final Activity now) {
        final List<DecisionRow> rows = now.outcome().decisions();
        final boolean held = now.outcome().offer() != null;
        setVisible(!rows.isEmpty());
        setManaged(!rows.isEmpty());
        stepsHeading.setVisible(!rows.isEmpty());
        stepsHeading.setManaged(!rows.isEmpty());
        if (rows.equals(shown) && held == shownHeld) {
            return;
        }
        final int selection = list.getSelectionModel().getSelectedIndex();
        shown = rows;
        shownHeld = held;
        list.getItems().setAll(rows);
        if (selection >= 0 && selection < rows.size()) {
            list.getSelectionModel().select(selection);
        }
        list.refresh();
    }

    /** One row: the field's name and value, then its agreement and mark. */
    private final class RowCell extends ListCell<DecisionRow> {

        @Override
        protected void updateItem(final @Nullable DecisionRow item, final boolean empty) {
            super.updateItem(item, empty);
            setTextOverrun(OverrunStyle.ELLIPSIS);
            setPrefWidth(0);
            getStyleClass().remove("progress-decision-uncertain");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(DecisionWords.nameOf(messages, item.group()) + "\n" + valueLine(item));
            if (item.isUncertain()) {
                getStyleClass().add("progress-decision-uncertain");
            }
        }
    }

    private String valueOf(final DecisionRow row) {
        if (row.value().isEmpty()) {
            return shownHeld ? NO_VALUE : messages.get(MessageKey.BRIEF_DECIDES_PENDING);
        }
        return DecisionWords.value(messages, row);
    }

    // The value and, once a sample has answered, how many agree: "Casual — 3 of 4 samples agree · so far".
    private String valueLine(final DecisionRow row) {
        final String agreement = agreementOf(row);
        return agreement.isEmpty() ? valueOf(row) : valueOf(row) + " — " + agreement;
    }

    private String agreementOf(final DecisionRow row) {
        if (row.samples() == 0 || row.value().isEmpty()) {
            return "";
        }
        final StringBuilder line =
                new StringBuilder(messages.get(MessageKey.BRIEF_DECIDES_AGREE, row.agreeing(), row.samples()));
        if (!shownHeld) {
            line.append(PART_JOIN).append(messages.get(MessageKey.BRIEF_DECIDES_SO_FAR));
        }
        if (row.isUncertain()) {
            line.append(PART_JOIN).append(messages.get(MessageKey.BRIEF_DECIDES_UNCERTAIN));
        }
        return line.toString();
    }
}
