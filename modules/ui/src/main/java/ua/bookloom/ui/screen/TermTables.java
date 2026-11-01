package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;

/** The table of a {@link TermTableCard}: its rows filtered by the search and the changed filter, then sorted. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TermTables {

    private static final double ROW_HEIGHT = 46;
    private static final PseudoClass CHANGED = PseudoClass.getPseudoClass("changed");
    private static final String LISTENER_KEY = "term-table-card-listener-";
    private static final double SCROLL_BAR_ROOM = 16;
    private static final String START_WIDTH_KEY = "term-table-start-width";

    static <R> TableView<R> of(
            final Messages messages,
            final String id,
            final TextField search,
            final TermTableCard.Texts texts,
            final TermTableCard.Rows<R> rows,
            final EmptyState.@Nullable Actions emptyActions) {
        final FilteredList<R> filtered = new FilteredList<>(rows.items());
        filtered.predicateProperty()
                .bind(Bindings.createObjectBinding(
                        () -> matching(search.getText(), rows.searchable()).and(changedOnly(rows)),
                        search.textProperty(),
                        rows.marks().onlyChanged(),
                        rows.marks().revision()));
        final SortedList<R> sorted = new SortedList<>(filtered);
        final TableView<R> table = new TableView<>(sorted);
        sorted.comparatorProperty()
                .bind(Bindings.createObjectBinding(
                        () -> tieBroken(table.getComparator(), rows.tieBreak()), sortInputs(table, rows)));
        // The sorted list follows the table's comparator through the binding above, so a sort always succeeds.
        table.setSortPolicy(_ -> true);
        table.setId(id + "-table");
        table.getStyleClass().add("glossary-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(_ -> new MarkedRow<>(messages, rows.marks(), rows.markKey()));
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(EmptyState.of(messages, id, texts, search.textProperty(), emptyActions));
        table.setFocusTraversable(false);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().addAll(rows.columns());
        scrollAcrossWhenNarrow(table);
        refreshWhenChanged(table, rows);
        return table;
    }

    // The fitting policy never shows a horizontal scroll bar: on a table narrower than its columns' minimum widths it
    // keeps every column at its minimum and leaves the last ones (the glossary's Gender and remove cross) out of reach.
    // Below that width the table scrolls across instead, its columns at their minimum widths so the scroll is short,
    // and every column stays reachable; with room again each column gets back its starting width and the table fits.
    // Runs on every resize: no logging.
    private static void scrollAcrossWhenNarrow(final TableView<?> table) {
        final ChangeListener<Number> onWidth = (observed, was, now) -> {
            final boolean fits = fitsWithoutScrolling(table);
            final boolean fitting = !TableView.UNCONSTRAINED_RESIZE_POLICY.equals(table.getColumnResizePolicy());
            if (fits != fitting) {
                table.getVisibleLeafColumns().forEach(column -> startOrMinimum(column, fits));
                table.setColumnResizePolicy(
                        fits
                                ? TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
                                : TableView.UNCONSTRAINED_RESIZE_POLICY);
            }
        };
        table.widthProperty().addListener(onWidth);
    }

    private static void startOrMinimum(final TableColumnBase<?, ?> column, final boolean starting) {
        final Object start = column.getProperties().computeIfAbsent(START_WIDTH_KEY, _ -> column.getPrefWidth());
        column.setPrefWidth(starting ? (Double) start : column.getMinWidth());
    }

    // The room a vertical scroll bar takes is kept free, so a long list does not push the last column out of view.
    private static boolean fitsWithoutScrolling(final TableView<?> table) {
        final double needed = table.getVisibleLeafColumns().stream()
                .mapToDouble(TableColumnBase::getMinWidth)
                .sum();
        final double room = table.getWidth() - table.snappedLeftInset() - table.snappedRightInset() - SCROLL_BAR_ROOM;
        return needed <= room;
    }

    /** A row the last operation changed wears the {@code :changed} pseudo-class, which the theme draws as a left edge. */
    private static final class MarkedRow<R> extends TableRow<R> {

        private final ChangeMarks.Side marks;
        private final Function<R, String> markKey;
        private final String word;
        private final Tooltip explanation;

        MarkedRow(final Messages messages, final ChangeMarks.Side marks, final Function<R, String> markKey) {
            this.marks = marks;
            this.markKey = markKey;
            word = messages.get(MessageKey.CHANGED_MARKER);
            // Only a changed row carries the explanation, so the one Tips builds is taken off again and put back
            // when the row is marked.
            Tips.install(messages, this, MessageKey.CHANGED_MARKER_TIP);
            explanation = Objects.requireNonNull(getTooltip());
            setTooltip(null);
            setAccessibleHelp(null);
        }

        // Runs for every row on every scroll: no logging.
        @Override
        protected void updateItem(final @Nullable R item, final boolean empty) {
            super.updateItem(item, empty);
            final boolean changed = !empty && item != null && marks.isMarked(markKey.apply(item));
            pseudoClassStateChanged(CHANGED, changed);
            setTooltip(changed ? explanation : null);
            setAccessibleText(changed ? word : null);
        }
    }

    private static <R> Predicate<R> matching(
            final @Nullable String query, final Function<R, List<@Nullable String>> in) {
        final String wanted = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return wanted.isEmpty()
                ? row -> true
                : row -> in.apply(row).stream()
                        .anyMatch(text ->
                                text != null && text.toLowerCase(Locale.ROOT).contains(wanted));
    }

    // The comparator is made again when what a column sorts by arrives (the Used counts load after the person chose the
    // sort), so the rows are put in order again; a comparator that stayed the same object would leave them as they
    // were.
    private static <R> Observable[] sortInputs(final TableView<R> table, final TermTableCard.Rows<R> rows) {
        final List<Observable> inputs = new ArrayList<>(rows.refreshOn());
        inputs.add(table.comparatorProperty());
        return inputs.toArray(new Observable[0]);
    }

    private static <R> @Nullable Comparator<R> tieBroken(
            final @Nullable Comparator<R> chosen, final Comparator<R> tieBreak) {
        return chosen == null ? null : chosen.thenComparing(tieBreak);
    }

    private static <R> Predicate<R> changedOnly(final TermTableCard.Rows<R> rows) {
        return row -> !rows.marks().onlyChanged().get()
                || rows.marks().isMarked(rows.markKey().apply(row));
    }

    // The table is shorter-lived than the view models, so what redraws it does so through weak listeners whose strong
    // references the table holds.
    private static <R> void refreshWhenChanged(final TableView<R> table, final TermTableCard.Rows<R> rows) {
        final List<ObservableValue<? extends Number>> triggers = new ArrayList<>(rows.refreshOn());
        triggers.add(rows.marks().revision());
        for (int at = 0; at < triggers.size(); at++) {
            final ChangeListener<Number> onChange = (observed, was, now) -> table.refresh();
            table.getProperties().put(LISTENER_KEY + at, onChange);
            triggers.get(at).addListener(new WeakChangeListener<>(onChange));
        }
    }
}
