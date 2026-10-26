package ua.bookloom.ui.control;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.RowMarker;
import ua.bookloom.ui.state.RunRow;

/**
 * The list of the live card: one fixed-height row per segment the run has started, the chunk being translated last and
 * earlier chunks above it, each with its marker, its locator and one line of its text, the whole text on hover.
 *
 * <p>It follows the newly translated segment: when rows arrive, the first one that has just become translated (or to
 * review, or translated again alone) is selected, and with none decided yet the first one being translated. A person's
 * pick (a click, an arrow key) stops the following until {@link #followAgain()}; a pick that leaves the list (its row
 * dropped as the oldest) returns to following. New rows are applied in place — changed rows replaced, new ones added at
 * the end, dropped ones removed from the top — and the list scrolls only when the selection moves, so a person can
 * scroll back through the history. The cells log nothing: they are redrawn on every scroll.
 */
@Slf4j
public final class LiveRowList extends ListView<LiveRow> {

    /** One row's height, so the list never measures its rows. */
    public static final double ROW_HEIGHT = 32;

    private static final double PART_SPACING = 8;
    private static final Pattern MARK = Pattern.compile("⟦[^⟧]*⟧");
    private static final Set<RowMarker> DECIDED = Set.of(RowMarker.TRANSLATED, RowMarker.ALONE, RowMarker.TO_REVIEW);
    private static final Set<RowMarker> IN_FLIGHT = Set.of(RowMarker.TRANSLATING, RowMarker.DRAFTED);

    private final ObservableList<LiveRow> rows = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper following = new ReadOnlyBooleanWrapper(true);
    private final BooleanProperty followSwitch = new SimpleBooleanProperty(true);
    private Map<String, RowMarker> shownMarkers = Map.of();
    private RunRow run = RunRow.READY;
    private boolean selectingByCode;

    /**
     * Builds an empty list.
     *
     * @param messages the catalogue the markers are worded from
     */
    public LiveRowList(final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        setItems(rows);
        setId("translating-live-rows");
        getStyleClass().add("live-rows");
        setFixedCellSize(ROW_HEIGHT);
        setCellFactory(view -> new RowCell(messages, this));
        final Label empty = new Label(messages.get(MessageKey.LIVE_CALL_NONE));
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        setPlaceholder(empty);
        getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> picked(now));
        followSwitch.addListener((observed, was, on) -> {
            log.debug("live list: show each segment when translated turned {}", on ? "on" : "off");
            if (on) {
                followAgain();
            }
        });
    }

    /** {@return the switch "Show each segment when translated"; FX thread only} */
    public BooleanProperty followSwitchProperty() {
        return followSwitch;
    }

    /** {@return whether the list follows the translation; a person's pick turns it off; FX thread only} */
    public ReadOnlyBooleanProperty followingProperty() {
        return following.getReadOnlyProperty();
    }

    /**
     * Shows the rows as the run is now. Called on every publication of the live calls, so it logs nothing.
     *
     * @param next the chunk's rows in reading order
     * @param now what the run is doing, which decides the markers
     */
    public void show(final List<LiveRow> next, final RunRow now) {
        Objects.requireNonNull(next, "next");
        final boolean runChanged = run != Objects.requireNonNull(now, "now");
        run = now;
        if (next.equals(rows)) {
            if (runChanged) {
                refresh();
                shownMarkers = markersOf(next);
            }
            return;
        }
        final String picked = selectedId();
        final Map<String, RowMarker> before = shownMarkers;
        shownMarkers = markersOf(next);
        selectingByCode = true;
        try {
            update(next);
            select(chooseRow(next, before, picked), picked);
        } finally {
            selectingByCode = false;
        }
    }

    /** Follows the translation again and selects the newest translated row. FX thread only. */
    public void followAgain() {
        log.debug("live list: following again from {} rows", rows.size());
        following.set(true);
        selectingByCode = true;
        try {
            final int newest = newestDecided(rows);
            select(newest >= 0 ? newest : firstInFlight(rows), null);
        } finally {
            selectingByCode = false;
        }
    }

    /**
     * The marker of a row as the list draws it now.
     *
     * @param row the row
     * @return its marker for the run's current state
     */
    public RowMarker markerOf(final LiveRow row) {
        return RowMarker.of(row, run);
    }

    private Map<String, RowMarker> markersOf(final List<LiveRow> next) {
        final Map<String, RowMarker> markers = new HashMap<>();
        next.forEach(row -> markers.put(row.segmentId(), RowMarker.of(row, run)));
        return markers;
    }

    private int chooseRow(
            final List<LiveRow> next, final Map<String, RowMarker> before, final @Nullable String picked) {
        final int kept = indexOf(next, picked);
        if (!following.get()) {
            if (kept >= 0) {
                return kept;
            }
            following.set(true);
        }
        if (followSwitch.get()) {
            // A list shown afresh has nothing it showed before: it opens on the newest translation.
            final int fresh = before.isEmpty() ? newestDecided(next) : firstNewlyDecided(next, before);
            if (fresh >= 0) {
                return fresh;
            }
        }
        return kept >= 0 ? kept : firstInFlight(next);
    }

    // The run only adds rows at the end, changes them in place and drops the oldest, so most updates are that diff;
    // anything else (a decided row started again moves to the end) is applied whole.
    private void update(final List<LiveRow> next) {
        final int start = next.isEmpty() ? -1 : indexOf(rows, next.getFirst().segmentId());
        if (start < 0 || !sameIdsFrom(start, next)) {
            rows.setAll(next);
            return;
        }
        if (start > 0) {
            rows.remove(0, start);
        }
        for (int index = 0; index < rows.size(); index++) {
            if (!rows.get(index).equals(next.get(index))) {
                rows.set(index, next.get(index));
            }
        }
        rows.addAll(next.subList(rows.size(), next.size()));
    }

    private boolean sameIdsFrom(final int start, final List<LiveRow> next) {
        if (rows.size() - start > next.size()) {
            return false;
        }
        for (int index = start; index < rows.size(); index++) {
            if (!rows.get(index).segmentId().equals(next.get(index - start).segmentId())) {
                return false;
            }
        }
        return true;
    }

    private int firstNewlyDecided(final List<LiveRow> next, final Map<String, RowMarker> before) {
        for (int index = 0; index < next.size(); index++) {
            final LiveRow row = next.get(index);
            if (DECIDED.contains(RowMarker.of(row, run))
                    && !DECIDED.contains(before.getOrDefault(row.segmentId(), RowMarker.WAITING))) {
                return index;
            }
        }
        return -1;
    }

    // With the history above it, the row to show when nothing is in flight is the first undecided one, else the newest.
    private int firstInFlight(final List<LiveRow> next) {
        int undecided = -1;
        for (int index = 0; index < next.size(); index++) {
            final LiveRow row = next.get(index);
            if (IN_FLIGHT.contains(RowMarker.of(row, run))) {
                return index;
            }
            if (undecided < 0 && !row.stage().isDecided()) {
                undecided = index;
            }
        }
        return undecided >= 0 ? undecided : next.size() - 1;
    }

    private int newestDecided(final List<LiveRow> next) {
        for (int index = next.size() - 1; index >= 0; index--) {
            if (DECIDED.contains(RowMarker.of(next.get(index), run))) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOf(final List<LiveRow> next, final @Nullable String segmentId) {
        if (segmentId == null) {
            return -1;
        }
        for (int index = 0; index < next.size(); index++) {
            if (next.get(index).segmentId().equals(segmentId)) {
                return index;
            }
        }
        return -1;
    }

    // Scrolls only when the selected row changes, so an update that keeps it leaves the person's scroll alone.
    private void select(final int index, final @Nullable String before) {
        if (index < 0) {
            getSelectionModel().clearSelection();
            return;
        }
        getSelectionModel().select(index);
        if (!rows.get(index).segmentId().equals(before)) {
            scrollTo(Math.max(0, index - 1));
        }
    }

    private @Nullable String selectedId() {
        final LiveRow row = getSelectionModel().getSelectedItem();
        return row == null ? null : row.segmentId();
    }

    // Only a selection the list did not make itself is a person's pick.
    private void picked(final @Nullable LiveRow row) {
        if (selectingByCode || row == null) {
            return;
        }
        log.debug("live list: the person picked {}, following stops", row.locator());
        following.set(false);
    }

    /** A row: its locator and one line of its text, then its marker at the right edge; the whole source on hover. */
    private static final class RowCell extends ListCell<LiveRow> {

        private final Messages messages;
        private final LiveRowList list;
        private final Label locator = new Label();
        private final Label text = new Label();
        private final HBox row = new HBox(PART_SPACING);
        private final Tooltip whole = Tips.tooltip("");
        private @Nullable RowMarker shownMarker;
        private @Nullable String shownCode;

        RowCell(final Messages messages, final LiveRowList list) {
            this.messages = messages;
            this.list = list;
            locator.getStyleClass().addAll("live-row-locator", "muted");
            locator.setMinWidth(Label.USE_PREF_SIZE);
            text.getStyleClass().add("live-row-text");
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(final @Nullable LiveRow item, final boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            final RowMarker marker = list.markerOf(item);
            if (marker != shownMarker
                    || !Objects.equals(item.fallbackCode(), shownCode)
                    || row.getChildren().isEmpty()) {
                shownMarker = marker;
                shownCode = item.fallbackCode();
                row.getChildren().setAll(locator, text, markerNode(marker, item));
            }
            locator.setText(item.locator());
            text.setText(oneLine(item.displaySource()));
            whole.setText(item.displaySource());
            setTooltip(whole);
            setGraphic(row);
        }

        // The row reads as words: the inline marks are left out and line breaks become spaces; the hover keeps both.
        private static String oneLine(final String source) {
            return MARK.matcher(source).replaceAll("").replaceAll("\\s+", " ").strip();
        }

        private StateMarker markerNode(final RowMarker marker, final LiveRow item) {
            final StateMarker node = StateMarker.of(marker.meaning(), messages.get(marker.word()));
            final String code = item.fallbackCode();
            if (marker == RowMarker.ALONE && code != null) {
                Tips.install(node, messages.get(MessageKey.LIVE_MARKER_ALONE_TIP, code.toLowerCase(Locale.ROOT)));
            }
            return node;
        }
    }
}
