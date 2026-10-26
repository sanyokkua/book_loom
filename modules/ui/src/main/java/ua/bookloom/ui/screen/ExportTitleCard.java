package ua.bookloom.ui.screen;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.ProtectedMarks;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.BookTitleViewModel.Part;
import ua.bookloom.ui.state.BookTitleViewModel.Row;
import ua.bookloom.ui.state.Tone;

/**
 * The Title and author card of Export: the original title and author, read-only, above the one translated title and
 * author the whole book takes, which the file name is built from. A translated field fills when the run has translated
 * the book's own title and author and is edited here; an edit is committed on Enter or when the field loses focus, not
 * on every key, because each commit is a stored edit. A title with formatting marks shows them as {@code ⟦0⟧}, and the
 * field keeps every one of them; an edit that is not saved says why under the field.
 *
 * <p>The view model outlives this card, so it is observed through weak listeners held by the fields below.
 */
@Slf4j
final class ExportTitleCard {

    private final BookTitleViewModel viewModel;
    private final Messages messages;
    private final TextField titleField = new TextField();
    private final TextField authorField = new TextField();
    private final Label titleSource = new Label();
    private final Label authorSource = new Label();
    private final Label titleReason = new Label();
    private final Label titleRefusal = new Label();
    private final VBox titleRow;
    private final Node node;
    private final ChangeListener<Row> onTitle;
    private final ChangeListener<Row> onAuthor;

    ExportTitleCard(final BookTitleViewModel viewModel, final Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
        this.titleRow = rows(Part.TITLE);
        final VBox authorRow = rows(Part.AUTHOR);
        final Label hint = BriefCards.hint(messages, MessageKey.EXPORT_BOOK_TITLE_HINT);
        hint.setId("export-book-hint");
        this.node =
                BriefCards.card("export-book-card", messages, MessageKey.EXPORT_BOOK_CARD, titleRow, authorRow, hint);
        this.onTitle = (observed, was, now) -> showTitle(now);
        this.onAuthor = (observed, was, now) -> showAuthor(now);
        viewModel.title().addListener(new WeakChangeListener<>(onTitle));
        viewModel.author().addListener(new WeakChangeListener<>(onAuthor));
        showTitle(viewModel.title().get());
        showAuthor(viewModel.author().get());
        viewModel.refresh();
    }

    Node node() {
        return node;
    }

    private VBox rows(final Part part) {
        final boolean isTitle = part == Part.TITLE;
        final String id = isTitle ? "export-book-title" : "export-book-author";
        final TextField field = isTitle ? titleField : authorField;
        final Label source = isTitle ? titleSource : authorSource;
        source.setId(id + "-original");
        source.getStyleClass().add("input-readonly");
        source.setWrapText(true);
        source.setMaxWidth(Double.MAX_VALUE);
        wireField(part, id, field);
        final VBox rows = new VBox(new TwoColumns(
                BriefCards.field(
                        messages,
                        isTitle ? MessageKey.EXPORT_BOOK_TITLE_ORIGINAL : MessageKey.EXPORT_BOOK_AUTHOR_ORIGINAL,
                        source),
                BriefCards.field(
                        messages,
                        isTitle ? MessageKey.EXPORT_BOOK_TITLE_TRANSLATED : MessageKey.EXPORT_BOOK_AUTHOR_TRANSLATED,
                        field),
                LayoutClass.Width.REGULAR));
        if (isTitle) {
            rows.getChildren().addAll(line(titleReason, id + "-reason", "hint"), refusal(id));
        }
        return rows;
    }

    // An edit is committed on Enter or when the field loses focus; every formatting mark of the original stays.
    private void wireField(final Part part, final String id, final TextField field) {
        final boolean isTitle = part == Part.TITLE;
        field.setId(id);
        Tips.install(messages, field, isTitle ? MessageKey.EXPORT_BOOK_TITLE_TIP : MessageKey.EXPORT_BOOK_AUTHOR_TIP);
        ProtectedMarks.install(
                field,
                () -> (isTitle ? viewModel.title() : viewModel.author()).get().sourceShown());
        field.setOnAction(event -> commit(part, field));
        field.focusedProperty().addListener((observed, was, now) -> {
            if (!now) {
                commit(part, field);
            }
        });
    }

    private Node refusal(final String id) {
        line(titleRefusal, id + "-refusal", Tone.ERR.statusClass());
        titleRefusal.textProperty().bind(viewModel.refusal());
        return Visibility.bind(titleRefusal, viewModel.refusal().isNotEmpty());
    }

    private static Label line(final Label label, final String id, final String styleClass) {
        label.setId(id);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    private void commit(final Part part, final TextField field) {
        log.debug("{} field committed", part);
        viewModel.save(part, field.getText());
    }

    private static void fill(final Row row, final TextField field) {
        field.setDisable(!row.editable());
        if (!field.getText().equals(row.target()) && !field.isFocused()) {
            field.setText(row.target());
        }
    }

    // A book with no title at all leaves the title rows out; one with no author says so in the author's place.
    private void showTitle(final Row row) {
        final boolean present = !row.source().isEmpty() || row.origin() != null;
        titleRow.setVisible(present);
        titleRow.setManaged(present);
        titleSource.setText(row.sourceShown());
        fill(row, titleField);
        showReason(row.origin());
    }

    private void showAuthor(final Row row) {
        authorSource.setText(
                row.source().isEmpty() ? messages.get(MessageKey.EXPORT_BOOK_AUTHOR_NONE) : row.sourceShown());
        fill(row, authorField);
    }

    private void showReason(final @Nullable TitleOrigin origin) {
        titleReason.setVisible(origin != null);
        titleReason.setManaged(origin != null);
        titleReason.setText(origin == null ? "" : messages.get(reasonKey(origin)));
    }

    private static MessageKey reasonKey(final TitleOrigin origin) {
        return switch (origin) {
            case DETAILS -> MessageKey.EXPORT_BOOK_TITLE_FROM_DETAILS;
            case FRONT_MATTER -> MessageKey.EXPORT_BOOK_TITLE_FROM_FRONT_MATTER;
            case FIRST_HEADING -> MessageKey.EXPORT_BOOK_TITLE_FROM_HEADING;
            case FILE_NAME -> MessageKey.EXPORT_BOOK_TITLE_FROM_FILE_NAME;
        };
    }
}
