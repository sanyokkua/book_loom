package ua.bookloom.ui.dialog;

import java.util.Objects;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProjectNameRules;

/**
 * A project-name field with its caption, the chips that append a word to the name and the line that says the name is
 * used. The refusal shows only after the first edit, so a card opened on a clean name does not already look wrong; a
 * refusal the service gave stays until then. FX thread only.
 */
final class NameField {

    private static final double SPACING = 4;
    private static final double CHIP_SPACING = 6;

    private enum Chip {
        SIZE("26b", MessageKey.DIALOG_PROJECT_NAME_CHIP_26B),
        QUALITY("maxQuality", MessageKey.DIALOG_PROJECT_NAME_CHIP_MAX_QUALITY),
        THINKING("thinkingLow", MessageKey.DIALOG_PROJECT_NAME_CHIP_THINKING_LOW),
        TONE("formal", MessageKey.DIALOG_PROJECT_NAME_CHIP_FORMAL),
        VERSION("v2", MessageKey.DIALOG_PROJECT_NAME_CHIP_V2);

        private final String idSuffix;
        private final MessageKey caption;

        Chip(final String idSuffix, final MessageKey caption) {
            this.idSuffix = idSuffix;
            this.caption = caption;
        }
    }

    private final Messages messages;
    private final Supplier<String> base;
    private final Runnable onEdit;
    private final TextField field = new TextField();
    private final Label error = new Label();
    private final FlowPane chips = new FlowPane(CHIP_SPACING, SPACING);
    private boolean touched;
    private boolean serviceError;
    private boolean starting;

    /**
     * Builds the field.
     *
     * @param messages the catalogue
     * @param idPrefix the card's id prefix; the field is {@code <prefix><fieldSuffix>}, the chips
     *     {@code <prefix>chip-*} and the line {@code <prefix>error}
     * @param fieldSuffix the field's id after the prefix
     * @param base what a chip is appended to when the field is empty
     * @param onEdit runs after every edit and chip, to refresh the card's buttons
     */
    NameField(
            final Messages messages,
            final String idPrefix,
            final String fieldSuffix,
            final Supplier<String> base,
            final Runnable onEdit) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.base = Objects.requireNonNull(base, "base");
        this.onEdit = Objects.requireNonNull(onEdit, "onEdit");
        field.setId(idPrefix + fieldSuffix);
        Tips.install(messages, field, MessageKey.DIALOG_PROJECT_NAME_FIELD_TIP);
        error.setId(idPrefix + "error");
        error.getStyleClass().add("dialog-error");
        error.setWrapText(true);
        hideError();
        final Label caption = new Label(messages.get(MessageKey.DIALOG_PROJECT_NAME_CHIPS));
        caption.getStyleClass().add("dialog-sub");
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.getChildren().add(caption);
        for (final Chip chip : Chip.values()) {
            chips.getChildren().add(chipButton(idPrefix, chip));
        }
        field.textProperty().addListener((observable, old, now) -> edited());
    }

    private Button chipButton(final String idPrefix, final Chip chip) {
        final Button button = new Button(messages.get(chip.caption));
        button.setId(idPrefix + "chip-" + chip.idSuffix);
        button.getStyleClass().addAll("btn-secondary", "name-chip");
        Tips.install(messages, button, MessageKey.DIALOG_PROJECT_NAME_CHIPS_TIP);
        button.setOnAction(event -> {
            final String typed = field.getText().strip();
            field.setText((typed.isEmpty() ? base.get() : typed) + " · " + messages.get(chip.caption));
            field.positionCaret(field.getText().length());
        });
        return button;
    }

    TextField field() {
        return field;
    }

    /** The caption, the field, the chips and the refusal line, stacked. */
    Node view(final String caption) {
        final Label label = new Label(caption);
        label.getStyleClass().add("field-label");
        field.setMaxWidth(Double.MAX_VALUE);
        return new VBox(SPACING, label, field, chips, error);
    }

    /** Sets the text without counting it as an edit. */
    void start(final String text) {
        starting = true;
        field.setText(text);
        starting = false;
        touched = false;
    }

    /** Shows a refusal the service gave, until the first edit. */
    void showServiceError(final @Nullable String text) {
        if (text != null) {
            serviceError = true;
            showError(text);
        }
    }

    /** Whether the person has changed the field since it opened. */
    boolean isTouched() {
        return touched;
    }

    /**
     * Updates the refusal line for the current verdict.
     *
     * @param check the rules' verdict on the typed text
     */
    void refresh(final ProjectNameRules.Check check) {
        final @Nullable MessageKey refusal =
                switch (check) {
                    case USED -> MessageKey.DIALOG_PROJECT_NAME_ERROR_USED;
                    case TOO_LONG -> MessageKey.DIALOG_PROJECT_NAME_ERROR_TOO_LONG;
                    case FORBIDDEN_CHARACTER -> MessageKey.DIALOG_PROJECT_NAME_ERROR_CHARACTERS;
                    case EMPTY, OK -> null;
                };
        if (touched && refusal != null) {
            showError(messages.get(refusal, Project.MAX_NAME_LENGTH));
        } else if (!(serviceError && !touched)) {
            hideError();
        }
    }

    private void edited() {
        if (starting) {
            return;
        }
        touched = true;
        serviceError = false;
        onEdit.run();
    }

    private void showError(final String text) {
        error.setText(text);
        error.setVisible(true);
        error.setManaged(true);
    }

    private void hideError() {
        error.setVisible(false);
        error.setManaged(false);
    }
}
