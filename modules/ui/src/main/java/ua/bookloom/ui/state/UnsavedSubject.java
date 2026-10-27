package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the "Save your changes?" question says it is about: the words differ per form, the name says which one.
 *
 * @param title the heading; argument 0 is {@code name}
 * @param text the line under the heading
 * @param name the thing being edited, as the heading shows it
 */
public record UnsavedSubject(MessageKey title, MessageKey text, String name) {

    public UnsavedSubject {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(name, "name");
    }

    /**
     * The question about a provider's form.
     *
     * @param name the provider's name
     * @return the words for the Providers form
     */
    public static UnsavedSubject provider(final String name) {
        return new UnsavedSubject(
                MessageKey.DIALOG_UNSAVED_PROVIDER_TITLE, MessageKey.DIALOG_UNSAVED_PROVIDER_TEXT, name);
    }

    /**
     * The question about a model's form.
     *
     * @param name the model and its provider, as the model picker names them
     * @return the words for the Models form
     */
    public static UnsavedSubject model(final String name) {
        return new UnsavedSubject(MessageKey.DIALOG_UNSAVED_MODEL_TITLE, MessageKey.DIALOG_UNSAVED_MODEL_TEXT, name);
    }
}
