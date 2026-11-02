package ua.bookloom.ui.control;

import java.time.Instant;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepEntry;

/**
 * The requests of the selected stage, one line each, so what is happening now and what happened before can both be
 * read. The line of the request being followed is selected; choosing another is the person's and turns follow off.
 * Nothing here logs: rows change up to ten times a second.
 */
final class ProgressRequestList extends VBox {

    private static final double SPACING = 4;
    private static final double ROW_HEIGHT = 28;
    private static final double LIST_BORDER = 2;
    private static final int VISIBLE_ROWS = 5;

    private final ProgressDialogModel model;
    private final RequestWords words;
    private final ListView<StepEntry> list = new ListView<>();
    private boolean personGesture;
    private Instant now = Instant.EPOCH;

    ProgressRequestList(final ProgressDialogModel model, final Messages messages) {
        super(SPACING);
        this.model = Objects.requireNonNull(model, "model");
        this.words = new RequestWords(Objects.requireNonNull(messages, "messages"));
        setId("progress-requests-pane");
        final Label heading = new Label(messages.get(MessageKey.PROGRESS_REQUESTS_HEADING));
        heading.getStyleClass().add("progress-heading");
        list.setId("progress-requests");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setItems(model.requestRows());
        list.setCellFactory(view -> new RequestCell());
        final var height = Bindings.min(VISIBLE_ROWS, Bindings.size(list.getItems()))
                .multiply(ROW_HEIGHT)
                .add(LIST_BORDER);
        list.minHeightProperty().bind(height);
        list.prefHeightProperty().bind(height);
        list.maxHeightProperty().bind(height);
        Tips.install(messages, list, MessageKey.PROGRESS_REQUESTS_TIP);
        getChildren().addAll(heading, list);
        managedProperty().bind(visibleProperty());
        visibleProperty().bind(Bindings.isNotEmpty(model.requestRows()));
        wire();
    }

    private void wire() {
        list.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> choosing());
        list.addEventFilter(KeyEvent.KEY_PRESSED, event -> choosing());
        list.getSelectionModel().selectedItemProperty().addListener((observed, was, chosen) -> {
            if (personGesture && chosen != null) {
                model.chooseRequest(chosen);
            }
        });
        model.focusedRequest().addListener((observed, was, followed) -> show(followed));
        model.requestRows().addListener((ListChangeListener<StepEntry>) change -> {
            show(model.focusedRequest().get());
            if (model.follow().get()) {
                list.scrollTo(model.requestRows().size() - 1);
            }
        });
        model.activity().addListener((observed, was, activity) -> list.refresh());
        show(model.focusedRequest().get());
    }

    // The flag spans the dispatch of the press, during which the list's own handlers select the row.
    private void choosing() {
        personGesture = true;
        Platform.runLater(() -> personGesture = false);
    }

    private void show(final @Nullable StepEntry followed) {
        if (followed == null) {
            list.getSelectionModel().clearSelection();
        } else if (!followed.equals(list.getSelectionModel().getSelectedItem())) {
            list.getSelectionModel().select(followed);
        }
    }

    /**
     * Moves the clock of a waiting request.
     *
     * @param at the moment the clocks read
     */
    void tick(final Instant at) {
        now = Objects.requireNonNull(at, "at");
        list.refresh();
    }

    // The stage's count is told only once it has ended: before that the work cannot say how many requests it will send.
    private String totalOfStage() {
        final StepEntry stage = model.selected().get();
        return stage == null || stage.isRunning() || stage.state() == null
                ? RequestWords.NONE
                : Integer.toString(stage.requests());
    }

    private final class RequestCell extends ListCell<StepEntry> {

        @Override
        protected void updateItem(final @Nullable StepEntry request, final boolean empty) {
            super.updateItem(request, empty);
            if (empty || request == null) {
                setText(null);
                return;
            }
            final CallSnapshot call = model.callOf(request);
            setText(words.line(getIndex() + 1, totalOfStage(), request, call, now));
        }
    }
}
