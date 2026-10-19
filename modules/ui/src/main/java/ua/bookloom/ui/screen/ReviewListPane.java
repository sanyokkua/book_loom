package ua.bookloom.ui.screen;

import java.util.EnumMap;
import java.util.List;
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
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.FindingBadge;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review panel's left side: the five filter chips, the All segments chip when it is offered, and the list of
 * segments with a badge for each one's main finding.
 *
 * <p>The list and the chips only follow the view model: picking a row selects that segment, and a segment the view
 * model selects (after a Skip, say) is picked in the list and scrolled into view, however it was picked. The list is
 * tall enough for at least {@value #MIN_ROWS} rows. The view model outlives this node, so what it is observed with is
 * held weakly and kept alive by the fields below. The list's cells log nothing.
 */
@Slf4j
final class ReviewListPane extends VBox {

    private static final double SPACING = 8;
    private static final double CHIP_GAP = 6;
    /** The rows' fixed height in the stylesheet ({@code .review-list}), plus the list's two border pixels. */
    private static final double ROW_HEIGHT = 34;

    private static final double FRAME = 2;
    static final int MIN_ROWS = 10;
    private static final int PREFERRED_ROWS = 12;

    private record Chip(String id, ReviewFilter filter, MessageKey label, MessageKey tip) {}

    private static final List<Chip> CHIPS = List.of(
            new Chip(
                    "review-chip-all",
                    ReviewFilter.ALL_FLAGGED,
                    MessageKey.REVIEW_CHIP_ALL,
                    MessageKey.REVIEW_CHIP_ALL_TIP),
            new Chip(
                    "review-chip-names",
                    ReviewFilter.NAMES,
                    MessageKey.REVIEW_CHIP_NAMES,
                    MessageKey.REVIEW_CHIP_NAMES_TIP),
            new Chip(
                    "review-chip-omissions",
                    ReviewFilter.OMISSIONS,
                    MessageKey.REVIEW_CHIP_OMISSIONS,
                    MessageKey.REVIEW_CHIP_OMISSIONS_TIP),
            new Chip(
                    "review-chip-foreign",
                    ReviewFilter.FOREIGN_KEPT,
                    MessageKey.REVIEW_CHIP_FOREIGN_KEPT,
                    MessageKey.REVIEW_CHIP_FOREIGN_KEPT_TIP),
            new Chip(
                    "review-chip-suspicious",
                    ReviewFilter.SUSPICIOUS,
                    MessageKey.REVIEW_CHIP_SUSPICIOUS,
                    MessageKey.REVIEW_CHIP_SUSPICIOUS_TIP),
            new Chip(
                    "review-chip-all-segments",
                    ReviewFilter.ALL_SEGMENTS,
                    MessageKey.REVIEW_CHIP_ALL_SEGMENTS,
                    MessageKey.REVIEW_CHIP_ALL_SEGMENTS_TIP));

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
        final FlowPane chipRow = chipRow();
        list.setId("review-list");
        list.getStyleClass().add("review-list");
        list.setItems(viewModel.rows());
        list.setCellFactory(view -> new RowCell());
        list.setMinHeight(MIN_ROWS * ROW_HEIGHT + FRAME);
        list.setPrefHeight(PREFERRED_ROWS * ROW_HEIGHT + FRAME);
        list.getSelectionModel().selectedIndexProperty().addListener((observed, was, now) -> reveal(now.intValue()));
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

    private FlowPane chipRow() {
        final FlowPane chipRow = new FlowPane(CHIP_GAP, CHIP_GAP);
        CHIPS.forEach(chip -> addChip(chipRow, chip));
        final ToggleButton browse =
                Objects.requireNonNull(chipOf.get(ReviewFilter.ALL_SEGMENTS), "the All segments chip");
        browse.visibleProperty().bind(viewModel.allSegmentsOffered());
        browse.managedProperty().bind(browse.visibleProperty());
        return chipRow;
    }

    private void addChip(final FlowPane row, final Chip spec) {
        final ToggleButton chip = Tips.install(messages, new ToggleButton(messages.get(spec.label())), spec.tip());
        chip.setId(spec.id());
        chip.getStyleClass().add("review-chip");
        chip.setToggleGroup(chips);
        chip.setUserData(spec.filter());
        chipOf.put(spec.filter(), chip);
        row.getChildren().add(chip);
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

    // The list's own keys scroll as they move; a selection made in code (a refreshed list, a pick by the view model)
    // does not, so every selection is brought fully into view here. Not logged: it follows each arrow key.
    private void reveal(final int index) {
        if (index >= 0 && list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            flow.scrollTo(index);
        }
    }

    /**
     * A row: its locator, then the badge of its main finding and the mark of a record kept as source. Its nodes are made
     * once per cell and only refilled as the cell is reused for another row while the list scrolls.
     */
    private final class RowCell extends ListCell<ReviewRow> {

        private final Label locator = new Label();
        private final Label badge = chip("chip-warn");
        private final Label kept = chip("chip-neutral");
        private final HBox line = new HBox(CHIP_GAP, locator, badge, kept);
        private final String keptText = messages.get(MessageKey.REVIEW_KEPT_AS_SOURCE);

        RowCell() {
            locator.getStyleClass().add("review-locator");
        }

        @Override
        protected void updateItem(final @Nullable ReviewRow row, final boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            locator.setText(row.locator());
            final FindingBadge finding = row.badge();
            badge.setText(finding == null ? "" : messages.get(finding.label()));
            shown(badge, finding != null);
            kept.setText(row.keptAsSource() ? keptText : "");
            shown(kept, row.keptAsSource());
            setGraphic(line);
        }

        private static Label chip(final String role) {
            final Label label = new Label();
            label.getStyleClass().addAll("chip", role);
            return label;
        }

        private static void shown(final Label label, final boolean visible) {
            label.setVisible(visible);
            label.setManaged(visible);
        }
    }
}
