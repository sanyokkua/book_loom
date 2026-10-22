package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepEntry;

/**
 * The progress dialog's left side: the follow switch over the list of steps, each with a marker and one result line.
 *
 * <p>A selection counts as the person's only when it happens while their own click or navigation key on the list is
 * being handled; the selection the work moves, and the one the list restores after its rows change, never turn follow
 * off. Tab is no choice. Nothing here logs: rows change up to ten times a second.
 */
final class ProgressStepList extends VBox {

    private static final double SPACING = 8;
    private static final double ROW_HEIGHT = 44;
    private static final double LIST_BORDER = 2;
    private static final Set<KeyCode> CHOOSING_KEYS = Set.of(
            KeyCode.UP, KeyCode.DOWN, KeyCode.HOME, KeyCode.END, KeyCode.PAGE_UP, KeyCode.PAGE_DOWN, KeyCode.SPACE);

    private final ProgressDialogModel model;
    private final ListView<StepEntry> list = new ListView<>();
    private boolean personGesture;

    ProgressStepList(final ProgressDialogModel model, final Messages messages) {
        super(SPACING);
        this.model = Objects.requireNonNull(model, "model");
        Objects.requireNonNull(messages, "messages");
        getStyleClass().add("progress-steps-pane");
        final ToggleSwitch follow = Tips.install(
                messages, new ToggleSwitch(messages.get(MessageKey.PROGRESS_FOLLOW)), MessageKey.PROGRESS_FOLLOW_TIP);
        follow.setId("progress-follow");
        follow.selectedProperty().bindBidirectional(model.follow());
        list.setId("progress-steps");
        list.getStyleClass().add("progress-steps");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setItems(model.rows());
        list.setCellFactory(view -> new StepCell(messages));
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(follow, list);
        wireSelection();
    }

    /**
     * Makes the list as tall as its rows while another group shares the column, so every step is shown and the column
     * scrolls instead; restores the list that fills the column and scrolls itself otherwise.
     *
     * @param own {@code true} to size the list to its rows
     */
    void sizeToRows(final boolean own) {
        if (own) {
            list.minHeightProperty()
                    .bind(Bindings.size(model.rows()).multiply(ROW_HEIGHT).add(LIST_BORDER));
        } else {
            list.minHeightProperty().unbind();
            list.setMinHeight(Region.USE_COMPUTED_SIZE);
        }
    }

    private void wireSelection() {
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> personChoosing());
        list.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (CHOOSING_KEYS.contains(event.getCode())) {
                personChoosing();
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> {
            if (personGesture && now != null) {
                model.choose(now);
            }
        });
        model.selected().addListener((observed, was, now) -> show(now));
        model.rows().addListener((ListChangeListener<StepEntry>)
                change -> show(model.selected().get()));
        show(model.selected().get());
    }

    // The flag spans the dispatch of the press, during which the list's own handlers select the row.
    private void personChoosing() {
        personGesture = true;
        Platform.runLater(() -> personGesture = false);
    }

    private void show(final @Nullable StepEntry selected) {
        if (selected == null) {
            list.getSelectionModel().clearSelection();
            return;
        }
        if (!selected.equals(list.getSelectionModel().getSelectedItem())) {
            list.getSelectionModel().select(selected);
            if (model.follow().get()) {
                list.scrollTo(selected);
            }
        }
    }

    /** One step: its marker, its name and its one result line. */
    private static final class StepCell extends ListCell<StepEntry> {

        private final Messages messages;
        private final Label marker = new Label();
        private final Label name = new Label();
        private final Label result = new Label();
        private final HBox row;

        StepCell(final Messages messages) {
            this.messages = messages;
            name.getStyleClass().add("progress-step-name");
            result.getStyleClass().add("progress-step-result");
            marker.getStyleClass().add("progress-marker");
            row = new HBox(marker, new VBox(name, result));
            row.getStyleClass().add("progress-step");
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(final @Nullable StepEntry item, final boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setId(null);
                setGraphic(null);
                return;
            }
            setId("progress-step-" + getIndex());
            final StepWords.Look look = StepWords.Look.of(item);
            marker.setText(look.glyph());
            marker.getStyleClass().removeIf(style -> style.startsWith("progress-marker-"));
            marker.getStyleClass().add("progress-marker-" + look.token());
            name.setText(StepWords.name(messages, item));
            result.setText(StepWords.result(messages, item));
            setGraphic(row);
        }
    }
}
