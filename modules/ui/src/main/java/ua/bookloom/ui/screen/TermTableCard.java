package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.PriorityRow;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;
import ua.bookloom.ui.state.CsvFileChooser;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * One of the two equal cards of Names &amp; style, the Glossary and the Recurring terms: a title, the toolbar (search,
 * + Add, Scan, Review, Translate with Stop while one runs, Import, Export) and a table. Both are built from this class,
 * so the two lists look and behave the same and only their columns, words and services differ.
 *
 * <p>The toolbar is a {@link PriorityRow}: a card too narrow for all of it moves Import and Export into a three-dot
 * menu first and then narrows the search field, so it stays on one line and no caption is cut.
 *
 * @param <R> the row type of the table
 */
@Slf4j
final class TermTableCard<R> extends VBox {

    private static final double SPACING = 10;
    private static final double TOOLBAR_SPACING = 8;
    private static final double ROW_HEIGHT = 46;
    // About 35 characters: the search yields to nothing but the menu fold, and narrows only once Import and Export are
    // in it.
    private static final double SEARCH_WIDTH = 300;
    private static final double SEARCH_MAX_WIDTH = 420;
    private static final double BLANK_HEADER_WIDTH = 24;
    private static final String MORE_GLYPH = "⋯";
    private static final String LISTENER_KEY = "term-table-card-listener-";

    /**
     * A button's caption and its hover explanation.
     *
     * @param caption the catalogue entry of the caption
     * @param tip the catalogue entry of the explanation
     */
    record Phrase(MessageKey caption, MessageKey tip) {}

    /**
     * The words of a card.
     *
     * @param titleId the id of the title label
     * @param title the card's heading
     * @param note the line under the heading, or {@code null} for none
     * @param add + Add
     * @param importFile Import
     * @param exportFile Export
     * @param empty the table's placeholder while it holds no row
     * @param emptyFiltered the table's placeholder while the search matches no row
     */
    record Texts(
            String titleId,
            MessageKey title,
            @Nullable MessageKey note,
            Phrase add,
            Phrase importFile,
            Phrase exportFile,
            MessageKey empty,
            MessageKey emptyFiltered) {}

    /**
     * What the toolbar does.
     *
     * @param words the captions and hover explanations of Scan, Review and Translate
     * @param actions what those three do
     * @param add what + Add does
     * @param importFile reads the chosen file
     * @param exportFile writes the chosen file
     * @param exportName the file name the save dialog starts with
     * @param extra a button that only this card carries, shown before the changed filter, or {@code null}
     */
    record Toolbar(
            ModelActionBar.Words words,
            ModelActionBar.Actions actions,
            Runnable add,
            Consumer<Path> importFile,
            Consumer<Path> exportFile,
            String exportName,
            @Nullable Button extra) {}

    /**
     * What the table lists.
     *
     * @param items the rows
     * @param marks the marks of the last operation on these rows
     * @param markKey the key a row is marked under
     * @param searchable the texts of a row the search looks in; a {@code null} one is skipped
     * @param tieBreak the order of rows a sorted column leaves tied
     * @param columns the columns, left to right
     * @param refreshOn what redraws every cell when it changes, besides the marks
     * @param <R> the row type
     */
    record Rows<R>(
            ObservableList<R> items,
            ChangeMarks.Side marks,
            Function<R, String> markKey,
            Function<R, List<@Nullable String>> searchable,
            Comparator<R> tieBreak,
            List<TableColumn<R, R>> columns,
            List<ObservableValue<? extends Number>> refreshOn) {}

    private final Messages messages;
    private final NamesStyleViewModel names;
    private final CsvFileChooser files;
    private final String id;
    private final Toolbar bar;

    TermTableCard(
            final Messages messages,
            final NamesStyleViewModel names,
            final CsvFileChooser files,
            final String id,
            final Texts texts,
            final Toolbar toolbar,
            final Rows<R> rows) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        this.names = Objects.requireNonNull(names, "names");
        this.files = Objects.requireNonNull(files, "files");
        this.id = Objects.requireNonNull(id, "id");
        this.bar = Objects.requireNonNull(toolbar, "toolbar");
        log.debug("building the {} card", id);
        final TextField search = search();
        getChildren().add(title(texts));
        if (texts.note() != null) {
            getChildren().add(note(texts.note()));
        }
        getChildren().addAll(toolbar(search, texts, rows.marks()), blockedNote(), table(search, texts, rows));
        setId(id + "-card");
        getStyleClass().add("card");
    }

    /**
     * A column of either table.
     *
     * @param header the catalogue entry of the header text, which a blank-headed column keeps as its accessible name
     * @param shown {@code false} for a column whose header carries no text (the remove cross, "→ Glossary") and
     *     explains itself on hover only
     */
    static <R> TableColumn<R, R> column(
            final Messages messages,
            final MessageKey header,
            final boolean shown,
            final MessageKey tip,
            final double width,
            final boolean fixed,
            final @Nullable Comparator<R> order,
            final Supplier<TableCell<R, R>> cell) {
        final TableColumn<R, R> column = new TableColumn<>(shown ? messages.get(header) : "");
        Tips.installOnHeader(messages, column, tip);
        if (!shown && column.getGraphic() instanceof Label blank) {
            blank.setMinWidth(BLANK_HEADER_WIDTH);
            blank.setAccessibleText(messages.get(header));
        }
        column.setSortable(order != null);
        if (order != null) {
            column.setComparator(order);
        }
        column.setReorderable(false);
        column.setPrefWidth(width);
        if (fixed) {
            column.setMinWidth(width);
            column.setMaxWidth(width);
        }
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(_ -> cell.get());
        return column;
    }

    private Label title(final Texts texts) {
        final Label title = new Label(messages.get(texts.title()));
        title.setId(texts.titleId());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        return title;
    }

    private Label note(final MessageKey key) {
        final Label note = new Label(messages.get(key));
        note.setId(id + "-note");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        return note;
    }

    // Says why the model actions are off while other model work (a run, an export, a provider test) is under way.
    private Label blockedNote() {
        final Label note = new Label();
        note.setId(id + "-model-blocked");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.textProperty().bind(names.modelBlockedReason());
        note.visibleProperty().bind(names.modelBlockedReason().isNotEmpty());
        note.managedProperty().bind(note.visibleProperty());
        return note;
    }

    // Escape empties the field, as it does in every search of the app.
    private TextField search() {
        final TextField search = Tips.install(messages, new TextField(), MessageKey.NAMES_STYLE_SEARCH_TIP);
        search.setId(id + "-search");
        search.setPromptText(messages.get(MessageKey.NAMES_STYLE_SEARCH));
        search.setPrefWidth(SEARCH_WIDTH);
        search.setMinWidth(0);
        search.setMaxWidth(SEARCH_MAX_WIDTH);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                search.clear();
                event.consume();
            }
        });
        return search;
    }

    private PriorityRow toolbar(final TextField search, final Texts texts, final ChangeMarks.Side marks) {
        final PriorityRow row = new PriorityRow(TOOLBAR_SPACING);
        row.setId(id + "-toolbar");
        row.setAlignment(Pos.CENTER_LEFT);
        final Button importButton = action("import", texts.importFile(), this::chooseImport);
        final Button exportButton = action("export", texts.exportFile(), this::chooseExport);
        final MenuButton more = menu(texts);
        row.getChildren().addAll(search, action("add", texts.add(), bar.add()));
        row.getChildren().add(ModelActionBar.build(messages, id, bar.words(), bar.actions(), names));
        if (bar.extra() != null) {
            row.getChildren().add(bar.extra());
        }
        row.getChildren()
                .addAll(ChangedMarks.filter(messages, id + "-changed-filter", marks), importButton, exportButton, more);
        row.elastic(search, SEARCH_WIDTH);
        row.fold(more, importButton, exportButton);
        return row;
    }

    // Import and Export move here when the card is too narrow for them; the words are the buttons' own.
    private MenuButton menu(final Texts texts) {
        final MenuButton more = Tips.install(messages, new MenuButton(), MessageKey.NAMES_STYLE_MORE_TIP);
        more.setGraphic(new Label(MORE_GLYPH));
        more.setId(id + "-more");
        more.setAccessibleText(messages.get(MessageKey.NAMES_STYLE_MORE));
        more.getStyleClass().addAll("btn-ghost", "menu-more");
        more.setMinWidth(Region.USE_PREF_SIZE);
        more.setMaxHeight(Double.MAX_VALUE);
        final MenuItem importItem = new MenuItem(messages.get(texts.importFile().caption()));
        importItem.setId(id + "-more-import");
        importItem.setOnAction(event -> chooseImport());
        final MenuItem exportItem = new MenuItem(messages.get(texts.exportFile().caption()));
        exportItem.setId(id + "-more-export");
        exportItem.setOnAction(event -> chooseExport());
        more.getItems().addAll(importItem, exportItem);
        return more;
    }

    private void chooseImport() {
        log.debug("{} import chosen", id);
        files.chooseToOpen().ifPresent(chosen -> {
            log.debug("{} imports {}", id, chosen.getFileName());
            bar.importFile().accept(chosen);
        });
    }

    private void chooseExport() {
        log.debug("{} export chosen", id);
        files.chooseToSave(bar.exportName()).ifPresent(chosen -> {
            log.debug("{} exports to {}", id, chosen.getFileName());
            bar.exportFile().accept(chosen);
        });
    }

    private Button action(final String part, final Phrase words, final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(words.caption())), words.tip());
        button.setId(id + "-" + part);
        button.getStyleClass().add("btn-ghost");
        // A caption is never cut to an ellipsis: the toolbar folds or hides whole parts instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        // Every part of the row is as tall as the search field, so they share one top edge and one centre.
        button.setMaxHeight(Double.MAX_VALUE);
        button.setOnAction(event -> {
            log.debug("{} pressed", button.getId());
            onPress.run();
        });
        return button;
    }

    private TableView<R> table(final TextField search, final Texts texts, final Rows<R> rows) {
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(emptyLabel(texts, search.textProperty()));
        table.setFocusTraversable(false);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns().addAll(rows.columns());
        refreshWhenChanged(table, rows);
        return table;
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

    private static <R> Predicate<R> changedOnly(final Rows<R> rows) {
        return row -> !rows.marks().onlyChanged().get()
                || rows.marks().isMarked(rows.markKey().apply(row));
    }

    private Label emptyLabel(final Texts texts, final StringProperty query) {
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
    private void refreshWhenChanged(final TableView<R> table, final Rows<R> rows) {
        final List<ObservableValue<? extends Number>> triggers = new ArrayList<>(rows.refreshOn());
        triggers.add(rows.marks().revision());
        for (int at = 0; at < triggers.size(); at++) {
            final ChangeListener<Number> onChange = (observed, was, now) -> table.refresh();
            table.getProperties().put(LISTENER_KEY + at, onChange);
            triggers.get(at).addListener(new WeakChangeListener<>(onChange));
        }
    }
}
