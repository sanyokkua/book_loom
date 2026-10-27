package ua.bookloom.ui.screen;

import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/**
 * A text field tied to one text of the form's draft: typing edits the draft, and a draft changed from elsewhere shows
 * in the field. Setting a field to the text it already holds changes nothing, so the two directions cannot loop. What
 * is wrong with the text shows right under the field, so the person sees which field holds Save back.
 */
final class DraftText {

    private static final String INVALID = "field-invalid";

    private final TextField node = new TextField();
    private final Label error = new Label();
    private final VBox cell = new VBox(4, node, error);
    private final Function<ProviderDraft, String> read;

    DraftText(
            final ProviderForm form,
            final Messages messages,
            final String id,
            final String widthClass,
            final MessageKey tip,
            final Function<ProviderDraft, String> read,
            final BiFunction<ProviderDraft, String, ProviderDraft> write) {
        this.read = read;
        node.setId(id);
        node.getStyleClass().add(widthClass);
        Tips.install(messages, node, tip);
        node.textProperty().addListener((observed, was, now) -> form.edit(draft -> write.apply(draft, now)));
        error.setId(id + "-error");
        error.getStyleClass().add("hint-err");
        error.setWrapText(true);
        showError("");
    }

    TextField node() {
        return node;
    }

    /** The field with its error line under it, for a form row. */
    VBox cell() {
        return cell;
    }

    /**
     * Shows what is wrong with the text, or nothing.
     *
     * @param text the translated finding; empty when the field is fine
     */
    void showError(final String text) {
        final boolean shown = !text.isEmpty();
        error.setText(text);
        Visibility.show(error, shown);
        node.getStyleClass().remove(INVALID);
        if (shown) {
            node.getStyleClass().add(INVALID);
        }
    }

    void show(final ProviderDraft draft) {
        final String text = read.apply(draft);
        if (!text.equals(node.getText())) {
            node.setText(text);
        }
    }
}
