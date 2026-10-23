package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The {@link SwitchProviderPrompt} shown as a card in the shell's modal host. Keeping the earlier model is the default
 * and what Escape means, because it is the answer that changes nothing.
 */
@Slf4j
@Singleton
public final class SwitchProviderDialog implements SwitchProviderPrompt {

    static final String CARD_ID = "switch-provider-card";
    static final String KEEP_ID = "switch-provider-keep";
    static final String USE_ID = "switch-provider-use";

    private static final double BODY_SPACING = 8;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public SwitchProviderDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void ask(final String chosenName, final String earlierName, final String earlierModel, final Runnable use) {
        Objects.requireNonNull(chosenName, "chosenName");
        Objects.requireNonNull(earlierName, "earlierName");
        Objects.requireNonNull(earlierModel, "earlierModel");
        Objects.requireNonNull(use, "use");
        log.debug("asking about switching to {} from {}", chosenName, earlierName);
        final ModalCard card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final FontIcon icon = new FontIcon(Feather.ALERT_TRIANGLE);
        icon.getStyleClass().add("dialog-icon-warn");
        final Label title = new Label(messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_TITLE, chosenName));
        title.setId("switch-provider-title");
        title.setWrapText(true);
        title.getStyleClass().add("dialog-title");
        final HBox header = new HBox(icon, title);
        header.getStyleClass().addAll("dialog-h", "error-header");
        card.setHeader(header);
        card.setContent(new VBox(
                BODY_SPACING,
                line(messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_TEXT, earlierName, earlierModel), "dialog-text"),
                line(messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_NOTE), "dialog-sub")));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wire(card, chosenName, earlierName, use);
        modalHost.show(card, false, true);
    }

    private Label line(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private void wire(final ModalCard card, final String chosenName, final String earlierName, final Runnable use) {
        final ButtonType keepType = new ButtonType(
                messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_KEEP, earlierName), ButtonBar.ButtonData.OK_DONE);
        final ButtonType useType = new ButtonType(
                messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_USE, chosenName), ButtonBar.ButtonData.OTHER);
        card.getButtonTypes().setAll(keepType, useType);
        final Button keep = (Button) card.lookupButton(keepType);
        final Button useButton = (Button) card.lookupButton(useType);
        style(keep, KEEP_ID, MessageKey.DIALOG_SWITCH_PROVIDER_KEEP_TIP, "btn-secondary");
        style(useButton, USE_ID, MessageKey.DIALOG_SWITCH_PROVIDER_USE_TIP, "btn-primary");
        keep.setOnAction(event -> {
            log.debug("the switch was declined: nothing changes");
            modalHost.hide();
        });
        useButton.setOnAction(event -> {
            log.debug("the switch was confirmed");
            modalHost.hide();
            use.run();
        });
        card.setOnEscape(keep::fire);
    }

    private void style(final Button button, final String id, final MessageKey tip, final String styleClass) {
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(styleClass);
    }
}
