package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.Predicate;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The field above a model list that narrows it by the typed name, with the sentence shown when nothing matches. It
 * only reports what was typed: the owner of the list decides what is hidden and never lets the filter change the
 * chosen model.
 */
@Slf4j
public final class ModelFilterField {

    private final Messages messages;
    private final TextField field = new TextField();
    private final Label none = Wrapped.label("", "hint");
    private final VBox node = new VBox(4, field, none);
    private final Runnable onDown;

    /**
     * Builds the field.
     *
     * @param messages the catalogue the placeholder, tip and sentence come from
     * @param fieldId the id of the text field
     * @param noneId the id of the "nothing matches" label
     * @param onChange told the typed text on every change and answering whether any model is still listed, which
     *     decides the "nothing matches" sentence; it fires per key, so it must not log
     * @param onDown what the Down key does: moves the focus into the list
     */
    public ModelFilterField(
            final Messages messages,
            final String fieldId,
            final String noneId,
            final Predicate<String> onChange,
            final Runnable onDown) {
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onChange, "onChange");
        this.onDown = Objects.requireNonNull(onDown, "onDown");
        field.setId(fieldId);
        field.setPromptText(messages.get(MessageKey.MODEL_FILTER_PROMPT));
        field.setAccessibleText(messages.get(MessageKey.MODEL_FILTER_PROMPT));
        Tips.install(messages, field, MessageKey.MODEL_FILTER_TIP);
        none.setId(noneId);
        Visibility.show(none, false);
        field.textProperty().addListener((observed, was, now) -> showNone(!onChange.test(now)));
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    }

    /**
     * The field with its sentence under it.
     *
     * @return the node to place above the list
     */
    public VBox node() {
        return node;
    }

    /**
     * The text field itself.
     *
     * @return the text field
     */
    public TextField field() {
        return field;
    }

    /**
     * What is typed.
     *
     * @return the text; empty if nothing
     */
    public String text() {
        return field.getText();
    }

    /**
     * Empties the field, which tells the owner the list is whole again.
     *
     * @return {@code true} if there was text to clear, {@code false} if the field was already empty
     */
    public boolean clear() {
        if (field.getText().isEmpty()) {
            return false;
        }
        log.debug("model filter cleared");
        field.clear();
        return true;
    }

    /**
     * Shows or hides the "no model name contains" sentence.
     *
     * @param nothingMatches whether the typed text hides every model
     */
    public void showNone(final boolean nothingMatches) {
        final boolean shown = nothingMatches && !field.getText().isBlank();
        if (shown) {
            none.setText(
                    messages.get(MessageKey.MODEL_FILTER_NONE, field.getText().strip()));
        }
        Visibility.show(none, shown);
    }

    private void onKey(final KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && clear()) {
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            event.consume();
            log.debug("model filter: Down moves the focus into the list");
            onDown.run();
        }
    }
}
