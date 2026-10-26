package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderNames;

/**
 * The {@link ModelPickPrompt} shown as a card in the shell's modal host: a radio list with nothing preselected, so
 * Enter never chooses a model the person did not look at. Escape means "Not now".
 */
@Slf4j
@Singleton
public final class ModelPickDialog implements ModelPickPrompt {

    static final String CARD_ID = "model-pick-card";
    static final String USE_ID = "model-pick-use";
    static final String NOT_NOW_ID = "model-pick-not-now";

    private static final double LIST_SPACING = 8;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public ModelPickDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(
            final String providerId, final List<String> modelIds, final Consumer<String> use, final Runnable notNow) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelIds, "modelIds");
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(notNow, "notNow");
        log.debug("asking which of {} model(s) of {} to use", modelIds.size(), providerId);
        final ToggleGroup group = new ToggleGroup();
        final VBox options = new VBox(LIST_SPACING);
        for (final String modelId : modelIds) {
            options.getChildren().add(option(group, modelId));
        }
        final FormCard card = new FormCard(
                modalHost, messages, CARD_ID, ModalCard.Size.DIALOG, messages.get(MessageKey.DIALOG_MODEL_PICK_TITLE));
        card.card()
                .setContent(new VBox(
                        LIST_SPACING * 2,
                        Wrapped.label(
                                messages.get(
                                        MessageKey.DIALOG_MODEL_PICK_BODY,
                                        ProviderNames.displayName(messages, providerId)),
                                "dialog-text"),
                        options));
        wire(card, group, use, notNow);
        card.show();
    }

    private void wire(final FormCard card, final ToggleGroup group, final Consumer<String> use, final Runnable notNow) {
        final Button notNowButton = card.button(
                NOT_NOW_ID,
                messages.get(MessageKey.DIALOG_MODEL_PICK_NOT_NOW),
                MessageKey.DIALOG_MODEL_PICK_NOT_NOW_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> {
                    log.debug("model pick declined");
                    notNow.run();
                });
        card.button(
                        USE_ID,
                        messages.get(MessageKey.DIALOG_MODEL_PICK_USE),
                        MessageKey.DIALOG_MODEL_PICK_USE_TIP,
                        ButtonBar.ButtonData.OK_DONE,
                        "btn-primary",
                        () -> {
                            final String picked = ((RadioButton) group.getSelectedToggle()).getText();
                            log.debug("model picked: {}", picked);
                            use.accept(picked);
                        })
                .disableProperty()
                .bind(group.selectedToggleProperty().isNull());
        card.card().setOnEscape(notNowButton::fire);
    }

    private RadioButton option(final ToggleGroup group, final String modelId) {
        final RadioButton option = new RadioButton(modelId);
        option.setToggleGroup(group);
        Tips.install(option, messages.get(MessageKey.DIALOG_MODEL_PICK_OPTION_HINT, modelId));
        return option;
    }
}
