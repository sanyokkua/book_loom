package ua.bookloom.ui.screen;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.BookTitleViewModel.Part;
import ua.bookloom.ui.state.BookTitleViewModel.Row;

/**
 * The Title and author card: the one translated title and author the whole book takes. A row fills when the run has
 * translated the book's own title and author and is edited here; an edit is committed on Enter or when the field loses
 * focus, not on every key, because each commit is a stored edit.
 *
 * <p>The view model outlives this card, so it is observed through weak listeners held by the fields below.
 */
@Slf4j
final class BriefTitleCard {

    private final BookTitleViewModel viewModel;
    private final Messages messages;
    private final TextField titleField = new TextField();
    private final TextField authorField = new TextField();
    private final Label titleSource = new Label();
    private final Label authorSource = new Label();
    private final VBox titleRow;
    private final VBox authorRow;
    private final Node node;
    private final ChangeListener<Row> onTitle;
    private final ChangeListener<Row> onAuthor;

    BriefTitleCard(final BookTitleViewModel viewModel, final Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
        this.titleRow = row("brief-book-title", MessageKey.BRIEF_TITLE, MessageKey.BRIEF_BOOK_TITLE_TIP, Part.TITLE);
        this.authorRow =
                row("brief-book-author", MessageKey.BRIEF_BOOK_AUTHOR, MessageKey.BRIEF_BOOK_AUTHOR_TIP, Part.AUTHOR);
        final Label hint = BriefCards.hint(messages, MessageKey.BRIEF_BOOK_TITLE_HINT);
        hint.setId("brief-book-hint");
        this.node =
                BriefCards.card("brief-book-card", messages, MessageKey.BRIEF_CARD_TITLE, titleRow, authorRow, hint);
        this.onTitle = (observed, was, now) -> show(now, titleRow, titleField, titleSource);
        this.onAuthor = (observed, was, now) -> show(now, authorRow, authorField, authorSource);
        viewModel.title().addListener(new WeakChangeListener<>(onTitle));
        viewModel.author().addListener(new WeakChangeListener<>(onAuthor));
        show(viewModel.title().get(), titleRow, titleField, titleSource);
        show(viewModel.author().get(), authorRow, authorField, authorSource);
        viewModel.refresh();
    }

    Node node() {
        return node;
    }

    private VBox row(final String id, final MessageKey label, final MessageKey tip, final Part part) {
        final TextField field = part == Part.TITLE ? titleField : authorField;
        final Label source = part == Part.TITLE ? titleSource : authorSource;
        field.setId(id);
        Tips.install(messages, field, tip);
        field.setOnAction(event -> commit(part, field));
        field.focusedProperty().addListener((observed, was, now) -> {
            if (!now) {
                commit(part, field);
            }
        });
        source.setId(id + "-source");
        source.getStyleClass().add("hint");
        source.setWrapText(true);
        final VBox row = BriefCards.field(messages, label, field);
        row.getChildren().add(source);
        return row;
    }

    private void commit(final Part part, final TextField field) {
        log.debug("{} field committed", part);
        viewModel.save(part, field.getText());
    }

    // A row with no source text is the book not having a title or author at all, and takes no room.
    private void show(final Row row, final VBox box, final TextField field, final Label source) {
        final boolean present = !row.source().isEmpty();
        box.setVisible(present);
        box.setManaged(present);
        source.setText(messages.get(MessageKey.BRIEF_BOOK_TITLE_SOURCE, row.source()));
        field.setDisable(!row.editable());
        if (!field.getText().equals(row.target()) && !field.isFocused()) {
            field.setText(row.target());
        }
    }
}
