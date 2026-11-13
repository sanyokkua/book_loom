package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelDraft;
import ua.bookloom.ui.state.ModelFacts;
import ua.bookloom.ui.state.ModelForm;

/**
 * The thinking row of the Models tab (design D20): what thinking control the chosen model offers and, when it has a
 * switch, the person's three choices. A model with no control, or one that always thinks, says so and offers no choice,
 * because nothing the person picked could reach it. It only draws the draft and the model's facts and turns a click back
 * into a draft change. The choices are stacked, one per line, so a long word in either language never squeezes them.
 */
@Slf4j
final class ModelThinkingSection {

    private final ModelForm form;
    private final Messages messages;
    private final VBox box = new VBox(6);
    private final Label offers = Wrapped.wholeLines(new Label());
    private final Label sends = Wrapped.wholeLines(new Label());
    private final ToggleGroup group = new ToggleGroup();
    private final RadioButton recommended = choice("models-thinking-recommended");
    private final RadioButton allow = choice("models-thinking-allow");
    private final RadioButton always = choice("models-thinking-always");
    private final VBox choices = new VBox(4, recommended, allow, always);

    // Set while the draft is drawn, so drawing it is not taken for a person's click.
    private boolean drawing;

    ModelThinkingSection(final ModelForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        box.setId("models-thinking");
        offers.setId("models-thinking-offers");
        sends.setId("models-thinking-sends");
        sends.getStyleClass().add("hint");
        sends.setText(messages.get(MessageKey.SETTINGS_MODELS_THINKING_SENDS));
        choices.setId("models-thinking-choices");
        label(
                recommended,
                MessageKey.SETTINGS_MODELS_THINKING_RECOMMENDED,
                MessageKey.SETTINGS_MODELS_THINKING_RECOMMENDED_TIP);
        label(allow, MessageKey.SETTINGS_MODELS_THINKING_ALLOW, MessageKey.SETTINGS_MODELS_THINKING_ALLOW_TIP);
        label(always, MessageKey.SETTINGS_MODELS_THINKING_ALWAYS, MessageKey.SETTINGS_MODELS_THINKING_ALWAYS_TIP);
        group.selectedToggleProperty().addListener((observed, was, now) -> picked(now));
        box.getChildren().addAll(heading(), offers, sends, choices);
    }

    VBox node() {
        return box;
    }

    /** Draws what the model offers and, when it has a switch, the draft's choice. */
    void show(final ModelDraft draft, final ModelFacts facts) {
        drawing = true;
        try {
            final @Nullable ReasoningControl control = facts.thinking();
            offers.setText(offerText(control));
            final boolean switchable = isSwitchable(control);
            for (final Node node : new Node[] {sends, choices}) {
                node.setVisible(switchable);
                node.setManaged(switchable);
            }
            final RadioButton wanted = toggleOf(draft.thinking());
            if (!wanted.isSelected()) {
                wanted.setSelected(true);
            }
        } finally {
            drawing = false;
        }
    }

    private HBox heading() {
        final Label title = new Label(messages.get(MessageKey.SETTINGS_MODELS_THINKING));
        title.getStyleClass().add("card-title");
        final Label sub = new Label(messages.get(MessageKey.SETTINGS_MODELS_THINKING_SUB));
        sub.getStyleClass().add("muted");
        return new HBox(8, title, sub);
    }

    private String offerText(final @Nullable ReasoningControl control) {
        if (control == null) {
            return messages.get(MessageKey.SETTINGS_MODELS_THINKING_READING);
        }
        return switch (control.kind()) {
            case BOOLEAN -> messages.get(MessageKey.SETTINGS_MODELS_THINKING_BOOLEAN);
            case LEVELS -> messages.get(MessageKey.SETTINGS_MODELS_THINKING_LEVELS);
            case NONE -> messages.get(MessageKey.SETTINGS_MODELS_THINKING_NONE);
            case UNKNOWN -> messages.get(MessageKey.SETTINGS_MODELS_THINKING_UNKNOWN);
            case ALWAYS, UNSWITCHABLE -> alwaysText(control);
        };
    }

    // A model that always thinks is sent its lowest level when it has one; else nothing can change it.
    private String alwaysText(final ReasoningControl control) {
        final @Nullable String lowest = control.offValue();
        if (control.kind() == ReasoningControl.Kind.ALWAYS && lowest != null && !isOffWord(lowest)) {
            return messages.get(MessageKey.SETTINGS_MODELS_THINKING_ALWAYS_LEVEL, lowest);
        }
        return messages.get(MessageKey.SETTINGS_MODELS_THINKING_ALWAYS_FIXED);
    }

    private static boolean isOffWord(final String value) {
        return value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("none");
    }

    private static boolean isSwitchable(final @Nullable ReasoningControl control) {
        return control != null
                && (control.kind() == ReasoningControl.Kind.BOOLEAN || control.kind() == ReasoningControl.Kind.LEVELS);
    }

    private void picked(final @Nullable Toggle now) {
        if (drawing) {
            return;
        }
        if (now == null) {
            // A radio button cannot be deselected by a click, but a cleared group still shows the draft's choice.
            show(form.draft().get(), form.facts().get());
            return;
        }
        final ThinkingOverride chosen = overrideOf(now);
        log.debug("thinking choice picked {}", chosen);
        form.edit(draft -> draft.withThinking(chosen));
    }

    private ThinkingOverride overrideOf(final Toggle toggle) {
        if (allow.equals(toggle)) {
            return ThinkingOverride.ALLOW_PROOFREAD_AND_NAMES;
        }
        return always.equals(toggle) ? ThinkingOverride.ALWAYS : ThinkingOverride.RECOMMENDED;
    }

    private RadioButton toggleOf(final ThinkingOverride override) {
        return switch (override) {
            case RECOMMENDED -> recommended;
            case ALLOW_PROOFREAD_AND_NAMES -> allow;
            case ALWAYS -> always;
        };
    }

    private void label(final RadioButton button, final MessageKey text, final MessageKey tip) {
        button.setText(messages.get(text));
        button.setToggleGroup(group);
        Tips.install(messages, button, tip);
    }

    private static RadioButton choice(final String id) {
        final RadioButton button = Wrapped.wholeLines(new RadioButton());
        button.setId(id);
        return button;
    }
}
