package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.ui.MemorySource;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Literal;
import ua.bookloom.ui.control.ModelFilterField;
import ua.bookloom.ui.control.RecommendationLine;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelNameFilter;
import ua.bookloom.ui.state.ModelRecommendation;
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
    private final MemorySource memory;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param memory this computer's memory, which the card's model advice is read from
     */
    @Inject
    public ModelPickDialog(final ModalHost modalHost, final Messages messages, final MemorySource memory) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    @Override
    public void ask(
            final ProviderConfig provider,
            final List<String> modelIds,
            final Consumer<String> use,
            final Runnable notNow) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelIds, "modelIds");
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(notNow, "notNow");
        log.debug("asking which of {} model(s) of {} to use", modelIds.size(), provider.id());
        final ToggleGroup group = new ToggleGroup();
        final VBox options = new VBox(LIST_SPACING);
        for (final String modelId : modelIds) {
            options.getChildren().add(option(group, modelId));
        }
        final ModelFilterField filter = filterAbove(options);
        final FormCard card = new FormCard(
                modalHost, messages, CARD_ID, ModalCard.Size.DIALOG, messages.get(MessageKey.DIALOG_MODEL_PICK_TITLE));
        final VBox content = new VBox(
                LIST_SPACING * 2,
                Wrapped.label(
                        messages.get(
                                MessageKey.DIALOG_MODEL_PICK_BODY, ProviderNames.displayName(messages, provider.id())),
                        "dialog-text"));
        final boolean ollama = ModelRecommendation.isOllamaServer(provider.kind(), provider.effectiveEngine());
        log.debug("pull advice for {}: {}", provider.id(), ollama);
        if (ollama) {
            content.getChildren().addAll(RecommendationLine.nodes(messages, memory, "model-pick"));
        }
        content.getChildren().addAll(filter.node(), options);
        card.card().setContent(content);
        wire(card, group, use, notNow, filter);
        card.show();
    }

    private void wire(
            final FormCard card,
            final ToggleGroup group,
            final Consumer<String> use,
            final Runnable notNow,
            final ModelFilterField filter) {
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
        // Escape first empties a typed filter and only then means "Not now"; the card's own key filter sees it first.
        card.card().setOnEscape(() -> {
            if (!filter.clear()) {
                notNowButton.fire();
            }
        });
    }

    // The filter only hides rows; the radios stay the same nodes, so a hidden pick stays selected and Use stays
    // enabled.
    private ModelFilterField filterAbove(final VBox options) {
        return new ModelFilterField(
                messages,
                "model-pick-filter",
                "model-pick-filter-none",
                typed -> narrow(options, typed),
                () -> options.getChildren().stream()
                        .filter(node -> node.isVisible() && node.isManaged())
                        .findFirst()
                        .ifPresent(Node::requestFocus));
    }

    private static boolean narrow(final VBox options, final String typed) {
        boolean any = false;
        for (final Node node : options.getChildren()) {
            final String id = ((RadioButton) node).getText();
            final boolean shown = ModelNameFilter.matches(id, id, typed);
            Visibility.show(node, shown);
            any |= shown;
        }
        return any;
    }

    private RadioButton option(final ToggleGroup group, final String modelId) {
        final RadioButton option = Literal.text(new RadioButton(modelId));
        option.setToggleGroup(group);
        Tips.install(option, messages.get(MessageKey.DIALOG_MODEL_PICK_OPTION_HINT, modelId));
        return option;
    }
}
