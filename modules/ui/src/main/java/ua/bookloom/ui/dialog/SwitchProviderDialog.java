package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Wrapped;
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
        final String heading = messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_TITLE, chosenName);
        final FormCard card = new FormCard(modalHost, messages, CARD_ID, ModalCard.Size.DIALOG, heading);
        card.card()
                .iconHeader(Feather.ALERT_TRIANGLE, "dialog-icon-warn", heading)
                .setId("switch-provider-title");
        card.card()
                .setContent(new VBox(
                        BODY_SPACING,
                        Wrapped.label(
                                messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_TEXT, earlierName, earlierModel),
                                "dialog-text"),
                        Wrapped.label(messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_NOTE), "dialog-sub")));
        wire(card, chosenName, earlierName, use);
        card.show();
    }

    private void wire(final FormCard card, final String chosenName, final String earlierName, final Runnable use) {
        final Button keep = card.button(
                KEEP_ID,
                messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_KEEP, earlierName),
                MessageKey.DIALOG_SWITCH_PROVIDER_KEEP_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-secondary",
                () -> log.debug("the switch was declined: nothing changes"));
        card.button(
                USE_ID,
                messages.get(MessageKey.DIALOG_SWITCH_PROVIDER_USE, chosenName),
                MessageKey.DIALOG_SWITCH_PROVIDER_USE_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-primary",
                () -> {
                    log.debug("the switch was confirmed");
                    use.run();
                });
        card.card().setOnEscape(keep::fire);
    }
}
