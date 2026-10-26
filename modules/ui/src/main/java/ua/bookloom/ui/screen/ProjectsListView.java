package ua.bookloom.ui.screen;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.ui.control.Overrun;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.LastOpened;
import ua.bookloom.ui.state.ProjectFilter;
import ua.bookloom.ui.state.ProjectRow;
import ua.bookloom.ui.state.ProjectsViewModel;

/**
 * The Projects list: the search and filter, a card per book with its translations as rows, and the line shown when
 * the search keeps nothing. The books are redrawn when the stored list, the search text or the filter changes; the
 * view model outlives the screen, so those changes are watched through weak listeners this object's fields hold.
 */
@Slf4j
final class ProjectsListView {

    private static final double SECTION_SPACING = 14;
    private static final double CARD_SPACING = 10;
    private static final double HEAD_SPACING = 12;
    private static final double COVER_WIDTH = 44;
    private static final double COVER_HEIGHT = 60;
    private static final double TITLE_SPACING = 2;
    private static final double ROW_INDENT = 56;
    private static final int HASH_PREFIX = 8;
    private static final int INITIALS = 2;

    private final Messages messages;
    private final ProjectsViewModel viewModel;
    private final ProjectRowView rows;
    private final VBox books = new VBox(SECTION_SPACING);
    private final ChangeListener<Object> onChange = (observed, was, now) -> render();
    private final TextField search = new TextField();
    private final Map<ProjectFilter, ToggleButton> segments = new EnumMap<>(ProjectFilter.class);

    ProjectsListView(final Messages messages, final ProjectsViewModel viewModel, final LastOpened lastOpened) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.rows = new ProjectRowView(messages, viewModel, lastOpened);
    }

    Node build() {
        log.debug("building the projects list");
        books.setId("projects-books");
        final Label hint = new Label(messages.get(MessageKey.PROJECTS_REMOVE_HINT));
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        final VBox root = new VBox(SECTION_SPACING, controls(), books, hint);
        viewModel.groups().addListener(new WeakChangeListener<>(onChange));
        viewModel.search().addListener(new WeakChangeListener<>(onChange));
        viewModel.filter().addListener(new WeakChangeListener<>(onChange));
        render();
        return root;
    }

    private Node controls() {
        search.setId("projects-search");
        search.setPromptText(messages.get(MessageKey.PROJECTS_SEARCH));
        search.textProperty().bindBidirectional(viewModel.search());
        Tips.install(messages, search, MessageKey.PROJECTS_SEARCH_TIP);
        HBox.setHgrow(search, Priority.ALWAYS);
        final HBox line = new HBox(HEAD_SPACING, search, filter());
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private SegmentedButton filter() {
        final SegmentedButton buttons = new SegmentedButton();
        for (final ProjectFilter choice : ProjectFilter.values()) {
            final ToggleButton segment = new ToggleButton(messages.get(choice.label()));
            segment.setId("projects-filter-" + choice.name().toLowerCase(Locale.ROOT));
            segment.setSelected(choice == viewModel.filter().get());
            Tips.install(messages, segment, choice.tip());
            segment.setOnAction(event -> chosen(choice, segment));
            segments.put(choice, segment);
            buttons.getButtons().add(segment);
        }
        buttons.setId("projects-filter");
        return buttons;
    }

    private void chosen(final ProjectFilter choice, final ToggleButton segment) {
        // Pressing the chosen segment again must not leave the choice empty.
        segment.setSelected(true);
        if (viewModel.filter().get() != choice) {
            log.debug("filter {} chosen", choice);
            viewModel.filter().set(choice);
        }
    }

    // Runs on every keystroke of the search, so it logs nothing.
    private void render() {
        books.getChildren().clear();
        for (final BookGroup group : viewModel.groups().get()) {
            final List<ProjectRow> visible = viewModel.visibleRows(group);
            if (!visible.isEmpty()) {
                books.getChildren().add(card(group, visible));
            }
        }
        if (books.getChildren().isEmpty()) {
            books.getChildren().add(noMatch());
        }
    }

    // A search with no hits names the text and offers Clear search; a filter alone that leaves nothing says what it found
    // none of, since the filter's own segment takes it back.
    private Node noMatch() {
        final String shown = viewModel.search().get().strip();
        final HBox box = new HBox(CARD_SPACING);
        box.setId("projects-no-match");
        box.setAlignment(Pos.CENTER_LEFT);
        if (shown.isEmpty()) {
            box.getChildren().add(new Label(messages.get(viewModel.filter().get().none())));
            return box;
        }
        final Label line = new Label(messages.get(MessageKey.PROJECTS_NO_MATCH, shown));
        final Hyperlink clear = new Hyperlink(messages.get(MessageKey.PROJECTS_CLEAR_SEARCH));
        clear.setId("projects-clear-search");
        Tips.install(messages, clear, MessageKey.PROJECTS_CLEAR_SEARCH_TIP);
        clear.setOnAction(event -> clearSearch());
        box.getChildren().addAll(line, clear);
        return box;
    }

    private void clearSearch() {
        log.debug("clear search pressed");
        viewModel.search().set("");
        viewModel.filter().set(ProjectFilter.ALL);
        segments.forEach((choice, segment) -> segment.setSelected(choice == ProjectFilter.ALL));
    }

    private Node card(final BookGroup group, final List<ProjectRow> visible) {
        final VBox card = new VBox(CARD_SPACING, head(group));
        card.setId("projects-book-"
                + group.contentHash()
                        .substring(0, Math.min(HASH_PREFIX, group.contentHash().length())));
        card.getStyleClass().add("card");
        final VBox list = new VBox(CARD_SPACING);
        list.setPadding(new Insets(0, 0, 0, ROW_INDENT));
        visible.forEach(row -> list.getChildren().add(rows.build(row)));
        card.getChildren().add(list);
        return card;
    }

    private Node head(final BookGroup group) {
        final String hash8 = group.contentHash()
                .substring(0, Math.min(HASH_PREFIX, group.contentHash().length()));
        final Label title = new Label(group.title());
        title.setId("projects-book-title-" + hash8);
        title.getStyleClass().add("card-title");
        title.setMinWidth(0);
        Overrun.install(title);
        final Label meta = new Label(messages.get(
                MessageKey.PROJECTS_BOOK_META, group.fileName(), group.format().name(), group.totalSegments()));
        meta.getStyleClass().add("muted");
        meta.setId("projects-book-meta-"
                + group.contentHash()
                        .substring(0, Math.min(HASH_PREFIX, group.contentHash().length())));
        Tips.install(messages, meta, MessageKey.TRANSLATING_STRIP_DONE_TIP);
        final Label count = new Label(
                messages.get(MessageKey.PROJECTS_BOOK_TRANSLATIONS, group.rows().size()));
        count.getStyleClass().add("count-pill");
        final Button translateInto = new Button(messages.get(MessageKey.PROJECTS_BOOK_TRANSLATE_INTO));
        translateInto.getStyleClass().add("btn-secondary");
        Tips.install(messages, translateInto, MessageKey.PROJECTS_BOOK_TRANSLATE_INTO_TIP);
        translateInto.setOnAction(event -> viewModel.translateInto(group));
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox head = new HBox(
                HEAD_SPACING, cover(group), new VBox(TITLE_SPACING, title, meta), spacer, count, translateInto);
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    private static Node cover(final BookGroup group) {
        final String initials = initials(group.title());
        final StackPane cover = new StackPane(new Label(initials));
        cover.getStyleClass().add("cover-placeholder");
        cover.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        cover.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        cover.setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        return cover;
    }

    private static String initials(final String title) {
        final StringBuilder letters = new StringBuilder();
        boolean startsWord = true;
        for (final int symbol : title.codePoints().toArray()) {
            if (startsWord && Character.isLetterOrDigit(symbol) && letters.length() < INITIALS) {
                letters.appendCodePoint(Character.toUpperCase(symbol));
            }
            startsWord = Character.isWhitespace(symbol);
        }
        return letters.toString();
    }
}
