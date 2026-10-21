package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
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

    static <R> TableView<R> of(
            final Messages messages,
            final String id,
            final TextField search,
            final TermTableCard.Texts texts,
            final TermTableCard.Rows<R> rows) {
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
                        () -> tieBroken(table.getComparator(), rows.tieBreak()), table.comparatorProperty()));
        // The sorted list follows the table's comparator through the binding above, so a sort always succeeds.
        table.setSortPolicy(_ -> true);
        table.setId(id + "-table");
        table.getStyleClass().add("glossary-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(_ -> new MarkedRow<>(messages, rows.marks(), rows.markKey()));
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(emptyLabel(messages, texts, search.textProperty()));
        table.setFocusTraversable(false);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().addAll(rows.columns());
        refreshWhenChanged(table, rows);
        return table;
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

    private static <R> @Nullable Comparator<R> tieBroken(
            final @Nullable Comparator<R> chosen, final Comparator<R> tieBreak) {
        return chosen == null ? null : chosen.thenComparing(tieBreak);
    }

    private static <R> Predicate<R> changedOnly(final TermTableCard.Rows<R> rows) {
        return row -> !rows.marks().onlyChanged().get()
                || rows.marks().isMarked(rows.markKey().apply(row));
    }

    private static Label emptyLabel(
            final Messages messages, final TermTableCard.Texts texts, final StringProperty query) {
        final Label empty = new Label();
        empty.getStyleClass().add("glossary-empty");
        empty.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                query.get() == null || query.get().isBlank() ? texts.empty() : texts.emptyFiltered()),
                        query));
        return empty;
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
