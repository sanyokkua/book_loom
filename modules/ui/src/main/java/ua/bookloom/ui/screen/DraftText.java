package ua.bookloom.ui.screen;

import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.scene.control.TextField;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;

/**
 * A text field tied to one text of the form's draft: typing edits the draft, and a draft changed from elsewhere shows
 * in the field. Setting a field to the text it already holds changes nothing, so the two directions cannot loop.
 */
final class DraftText {

    private final TextField node = new TextField();
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
    }

    TextField node() {
        return node;
    }

    void show(final ProviderDraft draft) {
        final String text = read.apply(draft);
        if (!text.equals(node.getText())) {
            node.setText(text);
        }
    }
}
