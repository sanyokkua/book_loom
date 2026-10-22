package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderNames;

/**
 * The {@link ContinuePrompt} shown as cards in the shell's modal host.
 *
 * <p>Escape closes either card and runs the answer that starts nothing: "Not now" on the not-answering card and the
 * cancel action on the different-model card, so the Translating screen never stays busy behind a closed card.
 */
@Slf4j
@Singleton
public final class ContinueDialog implements ContinuePrompt {

    static final String CARD_ID = "continue-card";
    static final String TRY_AGAIN_ID = "continue-try-again";
    static final String USE_ANOTHER_ID = "continue-use-another";
    static final String NOT_NOW_ID = "continue-not-now";
    static final String EARLIER_ID = "continue-earlier";
    static final String NEW_ID = "continue-new";

    private static final Words NOT_NOW =
            new Words(MessageKey.DIALOG_CONTINUE_NOT_NOW, MessageKey.DIALOG_CONTINUE_NOT_NOW_TIP);
    private static final Words USE_ANOTHER =
            new Words(MessageKey.DIALOG_CONTINUE_USE_ANOTHER, MessageKey.DIALOG_CONTINUE_USE_ANOTHER_TIP);
    private static final Words TRY_AGAIN =
            new Words(MessageKey.DIALOG_CONTINUE_TRY_AGAIN, MessageKey.DIALOG_CONTINUE_TRY_AGAIN_TIP);
    private static final Words NEW = new Words(MessageKey.DIALOG_CONTINUE_NEW, MessageKey.DIALOG_CONTINUE_NEW_TIP);
    private static final Words EARLIER =
            new Words(MessageKey.DIALOG_CONTINUE_EARLIER, MessageKey.DIALOG_CONTINUE_EARLIER_TIP);

    /** A button's caption and its hover explanation. */
    private record Words(MessageKey caption, MessageKey tip) {}

    /** One button of a card: what it is called, how it looks and what it does. */
    private record Answer(String id, Words words, boolean primary, Runnable action) {}

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the cards are shown in
     * @param messages the catalogue every word of the cards comes from
     */
    @Inject
    public ContinueDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void askNotAnswering(
            final ModelSelection model, final Runnable tryAgain, final Runnable useAnother, final Runnable notNow) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(tryAgain, "tryAgain");
        Objects.requireNonNull(useAnother, "useAnother");
        Objects.requireNonNull(notNow, "notNow");
        log.debug("asking about the model that is not answering: {}", model.providerId());
        final ModalCard card = card(
                MessageKey.DIALOG_CONTINUE_NOT_ANSWERING_TITLE,
                MessageKey.DIALOG_CONTINUE_NOT_ANSWERING_BODY,
                ProviderNames.displayName(messages, model.providerId()),
                model.modelId());
        final Button notNowButton = add(card, new Answer(NOT_NOW_ID, NOT_NOW, false, notNow));
        add(card, new Answer(USE_ANOTHER_ID, USE_ANOTHER, false, useAnother));
        add(card, new Answer(TRY_AGAIN_ID, TRY_AGAIN, true, tryAgain));
        card.setOnEscape(() -> notNowButton.fire());
        modalHost.show(card, false, true);
    }

    @Override
    public void askDifferent(
            final ModelSelection earlier,
            final ModelSelection now,
            final Runnable useEarlier,
            final Runnable useNew,
            final Runnable cancelled) {
        Objects.requireNonNull(earlier, "earlier");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(useEarlier, "useEarlier");
        Objects.requireNonNull(useNew, "useNew");
        Objects.requireNonNull(cancelled, "cancelled");
        log.debug("asking about a different model: earlier {}, now {}", earlier, now);
        final ModalCard card = card(
                MessageKey.DIALOG_CONTINUE_DIFFERENT_TITLE,
                MessageKey.DIALOG_CONTINUE_DIFFERENT_BODY,
                ProviderNames.displayName(messages, earlier.providerId()),
                earlier.modelId(),
                ProviderNames.displayName(messages, now.providerId()),
                now.modelId());
        add(card, new Answer(NEW_ID, NEW, false, useNew));
        add(card, new Answer(EARLIER_ID, EARLIER, true, useEarlier));
        card.setOnEscape(() -> {
            log.debug("different-model question closed with Escape");
            modalHost.hide();
            cancelled.run();
        });
        modalHost.show(card, false, true);
    }

    private ModalCard card(final MessageKey title, final MessageKey body, final Object... arguments) {
        final ModalCard card = new ModalCard(ModalCard.Size.DIALOG);
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label heading = new Label(messages.get(title));
        heading.getStyleClass().add("dialog-title");
        final VBox header = new VBox(heading);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        final Label text = new Label(messages.get(body, arguments));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        card.setContent(new VBox(text));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private Button add(final ModalCard card, final Answer answer) {
        final ButtonType type = new ButtonType(
                messages.get(answer.words().caption()),
                answer.primary() ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.OTHER);
        card.getButtonTypes().add(type);
        final Button button = (Button) card.lookupButton(type);
        button.setId(answer.id());
        button.getStyleClass().add(answer.primary() ? "btn-primary" : "btn-secondary");
        Tips.install(messages, button, answer.words().tip());
        button.setOnAction(event -> {
            log.debug("continue question answered with {}", answer.id());
            modalHost.hide();
            answer.action().run();
        });
        return button;
    }
}
