package ua.bookloom.ui.screen;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review panel's left side: the four filter chips, the All segments chip when it is offered, and the list of
 * segments with a badge for each one's main finding.
 *
 * <p>The list and the chips only follow the view model: picking a row selects that segment, and a segment the view
 * model selects (after a Skip, say) is picked in the list. The view model outlives this node, so what it is observed
 * with is held weakly and kept alive by the fields below. The list's cells log nothing.
 */
@Slf4j
final class ReviewListPane extends VBox {

    private static final double SPACING = 8;
    private static final double CHIP_GAP = 6;

    private final ReviewViewModel viewModel;
    private final Messages messages;
    private final ListView<ReviewRow> list = new ListView<>();
    private final ToggleGroup chips = new ToggleGroup();
    private final Map<ReviewFilter, ToggleButton> chipOf = new EnumMap<>(ReviewFilter.class);
    // True while the view model's filter is written into the chips, so that the write is not read as a press.
    private boolean applying;
    private final ChangeListener<ReviewRow> onPicked = (observed, was, now) -> picked(now);
    private final ChangeListener<@Nullable SegmentView> onSelected = (observed, was, now) -> syncSelection();
    private final ChangeListener<ReviewFilter> onFilter = (observed, was, now) -> showChip(now);
    private final ListChangeListener<ReviewRow> onRows = change -> Platform.runLater(this::syncSelection);

    ReviewListPane(final ReviewViewModel viewModel, final Messages messages) {
        super(SPACING);
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        final FlowPane chipRow = new FlowPane(CHIP_GAP, CHIP_GAP);
        addChip(chipRow, "review-chip-all", ReviewFilter.ALL_FLAGGED, MessageKey.REVIEW_CHIP_ALL);
        addChip(chipRow, "review-chip-names", ReviewFilter.NAMES, MessageKey.REVIEW_CHIP_NAMES);
        addChip(chipRow, "review-chip-omissions", ReviewFilter.OMISSIONS, MessageKey.REVIEW_CHIP_OMISSIONS);
        addChip(chipRow, "review-chip-foreign", ReviewFilter.FOREIGN_KEPT, MessageKey.REVIEW_CHIP_FOREIGN_KEPT);
        final ToggleButton browse = addChip(
                chipRow, "review-chip-all-segments", ReviewFilter.ALL_SEGMENTS, MessageKey.REVIEW_CHIP_ALL_SEGMENTS);
        browse.visibleProperty().bind(viewModel.allSegmentsOffered());
        browse.managedProperty().bind(browse.visibleProperty());
        list.setId("review-list");
        list.getStyleClass().add("review-list");
        list.setItems(viewModel.rows());
        list.setCellFactory(view -> new RowCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(chipRow, list);
        showChip(viewModel.filter().get());
        chips.selectedToggleProperty().addListener((observed, was, now) -> onChipPressed(now));
        list.getSelectionModel().selectedItemProperty().addListener(new WeakChangeListener<>(onPicked));
        viewModel.selected().addListener(new WeakChangeListener<>(onSelected));
        viewModel.filter().addListener(new WeakChangeListener<>(onFilter));
        viewModel.rows().addListener(new WeakListChangeListener<>(onRows));
    }

    ListView<ReviewRow> list() {
        return list;
    }

    private ToggleButton addChip(
            final FlowPane row, final String id, final ReviewFilter filter, final MessageKey label) {
        final ToggleButton chip = new ToggleButton(messages.get(label));
        chip.setId(id);
        chip.getStyleClass().add("review-chip");
        chip.setToggleGroup(chips);
        chip.setUserData(filter);
        chipOf.put(filter, chip);
        row.getChildren().add(chip);
        return chip;
    }

    private void showChip(final ReviewFilter filter) {
        applying = true;
        try {
            Objects.requireNonNull(chipOf.get(filter), "chip").setSelected(true);
        } finally {
            applying = false;
        }
    }

    private void onChipPressed(final @Nullable Object now) {
        if (applying) {
            return;
        }
        if (now == null) {
            showChip(viewModel.filter().get());
            return;
        }
        final ReviewFilter chosen = (ReviewFilter) ((ToggleButton) now).getUserData();
        log.debug("review chip {} pressed", chosen);
        viewModel.selectFilter(chosen);
        showChip(viewModel.filter().get());
    }

    private void picked(final @Nullable ReviewRow row) {
        final SegmentView selected = viewModel.selected().get();
        if (row != null && (selected == null || !selected.segmentId().equals(row.segmentId()))) {
            log.debug("review row {} picked", row.segmentId());
            viewModel.select(row.segmentId());
        }
    }

    private void syncSelection() {
        final SegmentView selected = viewModel.selected().get();
        if (selected == null) {
            return;
        }
        final ReviewRow current = list.getSelectionModel().getSelectedItem();
        if (current != null && current.segmentId().equals(selected.segmentId())) {
            return;
        }
        viewModel.rows().stream()
                .filter(row -> row.segmentId().equals(selected.segmentId()))
                .findFirst()
                .ifPresent(list.getSelectionModel()::select);
    }

    /** A row: its locator, then the badge of its main finding and the mark of a record kept as source. */
    private final class RowCell extends ListCell<ReviewRow> {

        @Override
        protected void updateItem(final @Nullable ReviewRow row, final boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            final HBox line = new HBox(CHIP_GAP, new Label(row.locator()));
            if (row.badge() != null) {
                line.getChildren().add(chip(messages.get(row.badge().label()), "chip-warn"));
            }
            if (row.keptAsSource()) {
                line.getChildren().add(chip(messages.get(MessageKey.REVIEW_KEPT_AS_SOURCE), "chip-neutral"));
            }
            setGraphic(line);
        }

        private static Label chip(final String text, final String role) {
            final Label label = new Label(text);
            label.getStyleClass().addAll("chip", role);
            return label;
        }
    }
}
