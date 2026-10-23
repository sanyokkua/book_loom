package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ObservableIntegerValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.FindingBadge;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review desk's left side: the filter box (To review, Worth a second look, All segments, each with its count),
 * previous and next, the list of segments with a marker and one line each, and the shortcut line under it.
 *
 * <p>The list only follows the view model: picking a row selects that segment, and a segment the view model selects is
 * picked in the list and scrolled into view. A move that would drop unsaved typing goes through {@code guard}, which
 * asks first. The list's cells log nothing.
 */
@Slf4j
final class ReviewListPane extends VBox {

    /** Ctrl+Alt+↓, the next row; on macOS Control+Option+↓. */
    static final KeyCombination NEXT =
            new KeyCodeCombination(KeyCode.DOWN, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);
    /** Ctrl+Alt+↑, the previous row. */
    static final KeyCombination PREVIOUS =
            new KeyCodeCombination(KeyCode.UP, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);
    /** Ctrl/⌘+Enter, the main button. */
    static final KeyCombination MAIN = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);

    /** Runs a move that would replace the editor's text, at once or after asking about unsaved typing. */
    interface Guard {
        /**
         * Runs the move, or asks first.
         *
         * @param move what replaces the segment shown
         * @return {@code true} if it ran at once, {@code false} if the person is being asked
         */
        boolean run(Runnable move);
    }

    /**
     * The words of a previous or next button.
     *
     * @param name its accessible name
     * @param tip its hover explanation, naming its shortcut
     */
    private record Words(MessageKey name, MessageKey tip) {
        static final Words PREVIOUS = new Words(MessageKey.REVIEW_PREV, MessageKey.REVIEW_PREV_TIP);
        static final Words NEXT = new Words(MessageKey.REVIEW_NEXT, MessageKey.REVIEW_NEXT_TIP);
    }

    private static final double SPACING = 8;
    private static final double ROW_GAP = 2;
    private static final double LIST_MIN_HEIGHT = 120;
    private static final List<ReviewFilter> FILTERS =
            List.of(ReviewFilter.ALL_FLAGGED, ReviewFilter.SUSPICIOUS, ReviewFilter.ALL_SEGMENTS);

    private final ReviewViewModel viewModel;
    private final Messages messages;
    private final ObservableIntegerValue suspicious;
    private final Guard guard;
    private final ListView<ReviewRow> list = new ListView<>();
    private final ComboBox<ReviewFilter> filters = new ComboBox<>();
    private final FilterCell buttonCell = new FilterCell();
    // True while the view model's state is written into the controls, so that the write is not read as a pick.
    private boolean applying;

    ReviewListPane(
            final ReviewViewModel viewModel,
            final ObservableIntegerValue suspicious,
            final Guard guard,
            final Messages messages) {
        super(SPACING);
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.suspicious = Objects.requireNonNull(suspicious, "suspicious");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.messages = Objects.requireNonNull(messages, "messages");
        list.setId("review-list");
        list.getStyleClass().add("review-list");
        list.setItems(viewModel.rows());
        list.setCellFactory(view -> new RowCell());
        list.setMinHeight(LIST_MIN_HEIGHT);
        list.getSelectionModel().selectedIndexProperty().addListener((observed, was, now) -> reveal(now.intValue()));
        list.getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> picked(now));
        VBox.setVgrow(list, Priority.ALWAYS);
        final Label shortcuts =
                new Label(messages.get(MessageKey.REVIEW_SHORTCUTS, MAIN.getDisplayText(), NEXT.getDisplayText()));
        shortcuts.setId("review-shortcuts");
        shortcuts.getStyleClass().add("muted");
        shortcuts.setWrapText(true);
        getChildren().addAll(filterRow(), list, shortcuts);
        viewModel.selected().addListener((observed, was, now) -> syncSelection());
        viewModel.rows().addListener((ListChangeListener<ReviewRow>) change -> Platform.runLater(this::syncSelection));
        viewModel.filter().addListener((observed, was, now) -> showFilter(now));
        viewModel.flaggedCount().addListener((observed, was, now) -> buttonCell.reword());
        suspicious.addListener((observed, was, now) -> buttonCell.reword());
    }

    private HBox filterRow() {
        filters.setId("review-filter");
        filters.getItems().setAll(FILTERS);
        filters.setCellFactory(view -> new FilterCell());
        filters.setButtonCell(buttonCell);
        filters.setMaxWidth(Double.MAX_VALUE);
        Tips.install(messages, filters, MessageKey.REVIEW_FILTER_TIP);
        filters.setAccessibleText(messages.get(MessageKey.REVIEW_FILTER));
        HBox.setHgrow(filters, Priority.ALWAYS);
        showFilter(viewModel.filter().get());
        filters.valueProperty().addListener((observed, was, now) -> chosen(now));
        final Button previous = arrow("review-prev", Feather.CHEVRON_LEFT, Words.PREVIOUS, PREVIOUS, -1);
        final Button next = arrow("review-next", Feather.CHEVRON_RIGHT, Words.NEXT, NEXT, 1);
        final HBox row = new HBox(SPACING / 2, filters, previous, next);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button arrow(
            final String id, final Feather icon, final Words words, final KeyCombination keys, final int delta) {
        final Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
        Tips.install(button, messages.get(words.tip(), keys.getDisplayText()));
        button.setAccessibleText(messages.get(words.name()));
        button.setOnAction(event -> {
            log.debug("review {} pressed", id);
            guard.run(() -> viewModel.move(delta));
        });
        return button;
    }

    private void showFilter(final ReviewFilter filter) {
        applying = true;
        try {
            filters.setValue(filter);
        } finally {
            applying = false;
        }
    }

    private void chosen(final @Nullable ReviewFilter now) {
        if (applying || now == null || now == viewModel.filter().get()) {
            return;
        }
        log.debug("review filter {} chosen", now);
        if (!guard.run(() -> viewModel.selectFilter(now))) {
            // The guard asks first: the box shows the filter shown until the person answers.
            showFilter(viewModel.filter().get());
        }
    }

    private void picked(final @Nullable ReviewRow row) {
        final SegmentView selected = viewModel.selected().get();
        if (applying || row == null || (selected != null && selected.segmentId().equals(row.segmentId()))) {
            return;
        }
        log.debug("review row {} picked", row.segmentId());
        if (!guard.run(() -> viewModel.select(row.segmentId()))) {
            // The guard asks first: the list shows the segment shown until the person answers.
            Platform.runLater(this::syncSelection);
        }
    }

    private void syncSelection() {
        final SegmentView selected = viewModel.selected().get();
        final ReviewRow current = list.getSelectionModel().getSelectedItem();
        if (selected == null || (current != null && current.segmentId().equals(selected.segmentId()))) {
            return;
        }
        applying = true;
        try {
            viewModel.rows().stream()
                    .filter(row -> row.segmentId().equals(selected.segmentId()))
                    .findFirst()
                    .ifPresent(list.getSelectionModel()::select);
        } finally {
            applying = false;
        }
    }

    // A selection made in code does not scroll the list, so every selection is brought into view here. Not logged: it
    // follows each arrow key.
    private void reveal(final int index) {
        if (index >= 0 && list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            flow.scrollTo(index);
        }
    }

    private String wordOf(final ReviewFilter filter) {
        return switch (filter) {
            case SUSPICIOUS -> messages.get(MessageKey.REVIEW_FILTER_SECOND_LOOK, suspicious.get());
            case ALL_SEGMENTS -> messages.get(MessageKey.REVIEW_FILTER_ALL);
            default ->
                messages.get(
                        MessageKey.REVIEW_FILTER_TO_REVIEW,
                        viewModel.flaggedCount().get());
        };
    }

    private static MessageKey tipOf(final ReviewFilter filter) {
        return switch (filter) {
            case SUSPICIOUS -> MessageKey.REVIEW_FILTER_SECOND_LOOK_TIP;
            case ALL_SEGMENTS -> MessageKey.REVIEW_FILTER_ALL_TIP;
            default -> MessageKey.REVIEW_FILTER_TO_REVIEW_TIP;
        };
    }

    /** A filter's word with its count, and its explanation on hover. Logs nothing: it is redrawn with the counts. */
    private final class FilterCell extends ListCell<ReviewFilter> {

        private final Tooltip tip = new Tooltip();

        FilterCell() {
            tip.setWrapText(true);
        }

        @Override
        protected void updateItem(final @Nullable ReviewFilter item, final boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(wordOf(item));
            tip.setText(messages.get(tipOf(item)));
            setTooltip(tip);
        }

        void reword() {
            final ReviewFilter item = getItem();
            if (item != null) {
                setText(wordOf(item));
            }
        }
    }

    /**
     * A row: its locator with the marker of what became of it at the right edge, and its one line under them. A new marker is made per update, as the
     * live list does; the rest is refilled as the cell is reused while the list scrolls.
     */
    private final class RowCell extends ListCell<ReviewRow> {

        private final Label locator = new Label();
        private final Label line = new Label();
        private final Tooltip whole = new Tooltip();
        private final HBox top = new HBox(SPACING);
        private final VBox row = new VBox(ROW_GAP, top, line);
        private final Region spacer = new Region();

        RowCell() {
            // A cell that asks for no width, so a long line is cut with an ellipsis and never makes the list scroll.
            setPrefWidth(0);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            top.setAlignment(Pos.CENTER_LEFT);
            locator.getStyleClass().addAll("review-locator", "muted");
            locator.setMinWidth(Label.USE_PREF_SIZE);
            line.getStyleClass().add("review-row-line");
            line.setMinWidth(0);
            line.setMaxWidth(Double.MAX_VALUE);
            whole.setWrapText(true);
        }

        @Override
        protected void updateItem(final @Nullable ReviewRow item, final boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            locator.setText(item.locator());
            final FindingBadge badge = item.badge();
            line.setText(
                    badge == null
                            ? item.fullText()
                            : messages.get(MessageKey.REVIEW_ROW_LINE, messages.get(badge.label()), item.fullText()));
            whole.setText(item.fullText());
            setTooltip(whole);
            top.getChildren()
                    .setAll(
                            locator,
                            spacer,
                            StateMarker.of(
                                    item.marker().meaning(),
                                    messages.get(item.marker().word())));
            setGraphic(row);
        }
    }
}
