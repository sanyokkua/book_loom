package ua.bookloom.ui.screen;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.GlossaryLabels;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * Builds the glossary table: Source term, Type, Target, Gender and Locked, each edited in its own cell, and the search
 * field that filters it.
 *
 * <p>Term, Type, Gender and Locked sort by a header click. Ties are always broken by the term, which no two rows share,
 * so a change to anything but the sorted column — above all a target being typed — never moves its row; a change to
 * the sorted column moves the row once the change is stored.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTable {

    private static final double ROW_HEIGHT = 46;
    private static final double SOURCE_WIDTH = 220;
    private static final double CHOICE_WIDTH = 140;
    private static final double TARGET_WIDTH = 260;
    private static final double LOCK_WIDTH = 100;

    private static final Comparator<GlossaryEntry> BY_TERM =
            Comparator.comparing(GlossaryEntry::term, Collator.getInstance());

    static TextField search(final Messages messages) {
        final TextField search = Tips.install(messages, new TextField(), MessageKey.NAMES_STYLE_SEARCH_TIP);
        search.setId("names-style-search");
        search.setPromptText(messages.get(MessageKey.NAMES_STYLE_SEARCH));
        search.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                search.clear();
                event.consume();
            }
        });
        return search;
    }

    static TableView<GlossaryEntry> build(
            final Messages messages, final NamesStyleViewModel model, final StringProperty query) {
        final FilteredList<GlossaryEntry> filtered = new FilteredList<>(model.rows());
        filtered.predicateProperty().bind(Bindings.createObjectBinding(() -> matching(query.get()), query));
        final SortedList<GlossaryEntry> sorted = new SortedList<>(filtered);
        final TableView<GlossaryEntry> table = new TableView<>(sorted);
        sorted.comparatorProperty()
                .bind(Bindings.createObjectBinding(() -> tieBroken(table.getComparator()), table.comparatorProperty()));
        // The sorted list follows the table's comparator through the binding above, so a sort always succeeds.
        table.setSortPolicy(_ -> true);
        table.setId("names-style-table");
        table.getStyleClass().add("glossary-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setFocusTraversable(false);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().addAll(columns(messages, model));
        return table;
    }

    private static Predicate<GlossaryEntry> matching(final @Nullable String query) {
        final String wanted = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return wanted.isEmpty()
                ? entry -> true
                : entry ->
                        contains(entry.term(), wanted) || (entry.target() != null && contains(entry.target(), wanted));
    }

    private static boolean contains(final String text, final String wanted) {
        return text.toLowerCase(Locale.ROOT).contains(wanted);
    }

    private static @Nullable Comparator<GlossaryEntry> tieBroken(final @Nullable Comparator<GlossaryEntry> chosen) {
        return chosen == null ? null : chosen.thenComparing(BY_TERM);
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> columns(
            final Messages messages, final NamesStyleViewModel model) {
        return Stream.concat(names(messages, model).stream(), attributes(messages, model).stream())
                .toList();
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> names(
            final Messages messages, final NamesStyleViewModel model) {
        return List.of(
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE,
                        MessageKey.NAMES_STYLE_COLUMN_SOURCE_TIP,
                        SOURCE_WIDTH,
                        BY_TERM,
                        () -> new GlossaryCells.SourceCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE,
                        MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                        CHOICE_WIDTH,
                        Comparator.comparing(GlossaryEntry::type),
                        () -> typeCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_TARGET,
                        MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP,
                        TARGET_WIDTH,
                        null,
                        () -> new GlossaryCells.TargetCell(messages, model)));
    }

    private static List<TableColumn<GlossaryEntry, GlossaryEntry>> attributes(
            final Messages messages, final NamesStyleViewModel model) {
        return List.of(
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_GENDER,
                        MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                        CHOICE_WIDTH,
                        Comparator.comparing(GlossaryEntry::gender),
                        () -> genderCell(messages, model)),
                column(
                        messages,
                        MessageKey.NAMES_STYLE_COLUMN_LOCKED,
                        MessageKey.NAMES_STYLE_COLUMN_LOCKED_TIP,
                        LOCK_WIDTH,
                        Comparator.comparing(GlossaryEntry::locked),
                        () -> new GlossaryCells.LockCell(messages, model)));
    }

    private static GlossaryCells.EntryCell<?> typeCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryCells.ChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_TYPE_TIP,
                TermType.values(),
                type -> GlossaryLabels.type(messages, type),
                GlossaryEntry::type,
                model::setType);
    }

    private static GlossaryCells.EntryCell<?> genderCell(final Messages messages, final NamesStyleViewModel model) {
        return new GlossaryCells.ChoiceCell<>(
                messages,
                MessageKey.NAMES_STYLE_COLUMN_GENDER_TIP,
                Gender.values(),
                gender -> GlossaryLabels.gender(messages, gender),
                GlossaryEntry::gender,
                model::setGender);
    }

    private static TableColumn<GlossaryEntry, GlossaryEntry> column(
            final Messages messages,
            final MessageKey caption,
            final MessageKey tip,
            final double width,
            final @Nullable Comparator<GlossaryEntry> order,
            final Supplier<GlossaryCells.EntryCell<?>> cell) {
        final TableColumn<GlossaryEntry, GlossaryEntry> column = new TableColumn<>(messages.get(caption));
        Tips.installOnHeader(messages, column, tip);
        column.setSortable(order != null);
        if (order != null) {
            column.setComparator(order);
        }
        column.setReorderable(false);
        column.setPrefWidth(width);
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(_ -> cell.get());
        return column;
    }
}
