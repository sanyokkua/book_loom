package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.PriorityRow;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * One of the two equal cards of Names &amp; style, the Glossary and the Recurring terms: a title, the toolbar (search,
 * + Add, Scan, Review, Translate with Stop while one runs, Import, Export) and a table. Both are built from this class,
 * so the two lists look and behave the same and only their columns, words and services differ.
 *
 * <p>The toolbar is a {@link PriorityRow}: a card too narrow for all of it moves Import and Export into a three-dot
 * menu first, then Review and Translate, and narrows the search field only down to a floor, so it stays on one line
 * and no caption is cut.
 *
 * @param <R> the row type of the table
 */
@Slf4j
final class TermTableCard<R> extends VBox {

    private static final double SPACING = 10;
    private static final double TOOLBAR_SPACING = 8;
    // About 35 characters: the search yields to nothing but the menu fold, and narrows only once Import and Export,
    // then Review and Translate, are in it; it never goes below the floor, so a query can always be read and typed.
    private static final double SEARCH_WIDTH = 300;
    private static final double SEARCH_FLOOR = 120;
    private static final double SEARCH_MAX_WIDTH = 420;
    private static final double BLANK_HEADER_WIDTH = 24;
    // The header cell's own padding and the sort arrow beside a sorted column's name.
    private static final double HEADER_ROOM = 22;
    private static final String MORE_GLYPH = "⋯";

    /**
     * A button's caption and its hover explanation.
     *
     * @param caption the catalogue entry of the caption
     * @param tip the catalogue entry of the explanation
     */
    record Phrase(MessageKey caption, MessageKey tip) {}

    /**
     * A link that opens a short explanation under the note.
     *
     * @param link the catalogue entry of the link's caption
     * @param text the catalogue entry of the explanation
     */
    record Help(MessageKey link, MessageKey text) {}

    /**
     * The words of a card.
     *
     * @param titleId the id of the title label
     * @param title the card's heading
     * @param note the line under the heading, or {@code null} for none
     * @param help the "How it works" link under the note and the text it opens, or {@code null} for none
     * @param add + Add
     * @param importFile Import
     * @param exportFile Export
     * @param empty the table's placeholder while it holds no row
     * @param emptyFiltered the table's placeholder while the search matches no row
     * @param emptyScan the caption of the placeholder's Scan button, or {@code null} for a placeholder with no buttons;
     *     it also carries + Add
     */
    record Texts(
            String titleId,
            MessageKey title,
            @Nullable MessageKey note,
            @Nullable Help help,
            Phrase add,
            Phrase importFile,
            Phrase exportFile,
            MessageKey empty,
            MessageKey emptyFiltered,
            @Nullable Phrase emptyScan) {}

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
     * @param frozen whether the screen is read-only because work in the background writes to it; the editing actions
     *     of the toolbar follow it
     */
    record Toolbar(
            ModelActionBar.Words words,
            ModelActionBar.Actions actions,
            Runnable add,
            Consumer<Path> importFile,
            Consumer<Path> exportFile,
            String exportName,
            @Nullable Button extra,
            ObservableBooleanValue frozen) {}

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
    private final FileChooserPort files;
    private final String id;
    private final Toolbar bar;
    private @Nullable Button scan;

    TermTableCard(
            final Messages messages,
            final NamesStyleViewModel names,
            final FileChooserPort files,
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
        getChildren().addAll(CardHeading.of(messages, id, texts, rows.items()));
        final PriorityRow tools = toolbar(search, texts, rows.marks());
        getChildren()
                .addAll(tools, blockedNote(), TermTables.of(messages, id, search, texts, rows, emptyActions(texts)));
        setId(id + "-card");
        getStyleClass().add("card");
    }

    /**
     * A column of either table.
     *
     * @param header the catalogue entry of the header text, which a blank-headed column keeps as its accessible name
     * @param shown {@code false} for a column whose header carries no text (the remove cross, "→ Glossary") and
     *     explains itself on hover only
     * @param width the width the column starts at
     * @param minWidth the width the column cannot be dragged below
     */
    static <R> TableColumn<R, R> column(
            final Messages messages,
            final MessageKey header,
            final boolean shown,
            final MessageKey tip,
            final double width,
            final double minWidth,
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
        if (shown && column.getGraphic() instanceof Label caption) {
            keepHeaderWhole(column, caption, minWidth);
        } else {
            column.setMinWidth(minWidth);
        }
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(_ -> cell.get());
        return column;
    }

    // A column is never narrower than its name: a narrow window takes the room from the wide columns instead, so a
    // Ukrainian header ("Закріплено") is not cut. Followed after the stylesheet sets the header's font and padding and
    // its skin, which is what first measures the words.
    private static void keepHeaderWhole(final TableColumn<?, ?> column, final Label caption, final double minWidth) {
        column.minWidthProperty()
                .bind(Bindings.createDoubleBinding(
                        () -> Math.max(minWidth, Math.ceil(caption.prefWidth(-1) + HEADER_ROOM)),
                        caption.textProperty(),
                        caption.fontProperty(),
                        caption.paddingProperty(),
                        caption.skinProperty()));
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
        search.setMinWidth(SEARCH_FLOOR);
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
        importButton.disableProperty().bind(bar.frozen());
        final Button exportButton = action("export", texts.exportFile(), this::chooseExport);
        final MenuButton more = menu(texts, importButton, exportButton);
        final Button add = action("add", texts.add(), bar.add());
        add.disableProperty().bind(bar.frozen());
        row.getChildren().addAll(search, add);
        final ModelActionBar.Bar model =
                ModelActionBar.build(messages, id, bar.words(), bar.actions(), names, bar.frozen());
        scan = model.scan();
        row.getChildren().addAll(model.buttons());
        if (bar.extra() != null) {
            row.getChildren().add(bar.extra());
        }
        row.getChildren()
                .addAll(ChangedMarks.filter(messages, id + "-changed-filter", marks), importButton, exportButton, more);
        row.elastic(search, SEARCH_WIDTH);
        row.fold(more, importButton, exportButton);
        row.fold(more, model.review(), model.translate());
        more.getItems()
                .addAll(0, List.of(foldedItem("review", model.review()), foldedItem("translate", model.translate())));
        return row;
    }

    // The empty table's own Scan and + Add, which do what the toolbar's do and are off when theirs are.
    private EmptyState.@Nullable Actions emptyActions(final Texts texts) {
        final Button toolbarScan = scan;
        if (texts.emptyScan() == null || toolbarScan == null) {
            return null;
        }
        return new EmptyState.Actions(
                texts.emptyScan(),
                texts.add(),
                bar.actions().scan(),
                bar.add(),
                toolbarScan.disableProperty(),
                bar.frozen());
    }

    // Import and Export move here when the card is too narrow for them; the words are the buttons' own.
    private MenuButton menu(final Texts texts, final Button importButton, final Button exportButton) {
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
        importItem.disableProperty().bind(bar.frozen());
        final MenuItem exportItem = new MenuItem(messages.get(texts.exportFile().caption()));
        exportItem.setId(id + "-more-export");
        exportItem.setOnAction(event -> chooseExport());
        more.getItems().addAll(importItem, exportItem);
        importItem.visibleProperty().bind(importButton.visibleProperty().not());
        exportItem.visibleProperty().bind(exportButton.visibleProperty().not());
        return more;
    }

    // A button that has moved into the menu keeps its words and its enabled state; the item shows only while it has.
    private MenuItem foldedItem(final String part, final Button button) {
        final MenuItem item = new MenuItem(button.getText());
        item.setId(id + "-more-" + part);
        item.disableProperty().bind(button.disableProperty());
        item.visibleProperty().bind(button.visibleProperty().not());
        item.setOnAction(event -> button.fire());
        return item;
    }

    // The modal host is a pane of the main window, so the window is the owner the platform dialog needs.
    private @Nullable Window owner() {
        final Scene scene = getScene();
        return scene == null ? null : scene.getWindow();
    }

    private void chooseImport() {
        log.debug("{} import chosen", id);
        files.chooseToOpen(owner(), FileChooserPort.FileKind.CSV).ifPresent(chosen -> {
            log.debug("{} imports {}", id, chosen.getFileName());
            bar.importFile().accept(chosen);
        });
    }

    private void chooseExport() {
        log.debug("{} export chosen", id);
        files.chooseToSave(owner(), FileChooserPort.FileKind.CSV, Path.of(bar.exportName()))
                .ifPresent(chosen -> {
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
}
