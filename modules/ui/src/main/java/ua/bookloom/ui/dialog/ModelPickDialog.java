package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
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
        final ModalCard card = card(providerId, options);
        final ButtonType notNowType =
                new ButtonType(messages.get(MessageKey.DIALOG_MODEL_PICK_NOT_NOW), ButtonBar.ButtonData.OTHER);
        final ButtonType useType =
                new ButtonType(messages.get(MessageKey.DIALOG_MODEL_PICK_USE), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(notNowType, useType);
        final Button notNowButton = (Button) card.lookupButton(notNowType);
        wireNotNow(notNowButton, notNow);
        wireUse((Button) card.lookupButton(useType), group, use);
        card.setOnEscape(() -> notNowButton.fire());
        modalHost.show(card, false, true);
    }

    private void wireNotNow(final Button button, final Runnable notNow) {
        button.setId(NOT_NOW_ID);
        button.getStyleClass().add("btn-secondary");
        Tips.install(messages, button, MessageKey.DIALOG_MODEL_PICK_NOT_NOW_TIP);
        button.setOnAction(event -> {
            log.debug("model pick declined");
            modalHost.hide();
            notNow.run();
        });
    }

    private void wireUse(final Button button, final ToggleGroup group, final Consumer<String> use) {
        button.setId(USE_ID);
        button.getStyleClass().add("btn-primary");
        Tips.install(messages, button, MessageKey.DIALOG_MODEL_PICK_USE_TIP);
        button.disableProperty().bind(group.selectedToggleProperty().isNull());
        button.setOnAction(event -> {
            final String picked = ((RadioButton) group.getSelectedToggle()).getText();
            log.debug("model picked: {}", picked);
            modalHost.hide();
            use.accept(picked);
        });
    }

    private RadioButton option(final ToggleGroup group, final String modelId) {
        final RadioButton option = new RadioButton(modelId);
        option.setToggleGroup(group);
        Tips.install(option, messages.get(MessageKey.DIALOG_MODEL_PICK_OPTION_HINT, modelId));
        return option;
    }

    private ModalCard card(final String providerId, final VBox options) {
        final ModalCard card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label heading = new Label(messages.get(MessageKey.DIALOG_MODEL_PICK_TITLE));
        heading.getStyleClass().add("dialog-title");
        final VBox header = new VBox(heading);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        final Label text = new Label(
                messages.get(MessageKey.DIALOG_MODEL_PICK_BODY, ProviderNames.displayName(messages, providerId)));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        card.setContent(new VBox(LIST_SPACING * 2, text, options));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }
}
