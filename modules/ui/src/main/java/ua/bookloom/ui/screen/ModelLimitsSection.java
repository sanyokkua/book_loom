package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.function.BiFunction;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelDraft;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelFacts;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The two optional numbers of the Models tab: the context window and the answer-length cap. Each sits behind a switch;
 * off means BookLoom picks the value, and the window's field then shows what the program loaded, as a suggestion that
 * is not part of the draft.
 */
final class ModelLimitsSection {

    private final ModelForm form;
    private final Messages messages;
    private final TechnicalDetails technical;
    private final VBox box = new VBox(14);
    private final ToggleSwitch contextOn = new ToggleSwitch();
    private final TextField context;
    private final Label contextTech;
    private final Label contextOpenAi;
    private final Label contextEngine;
    private final Label limitIgnored;
    private final ToggleSwitch limitOn = new ToggleSwitch();
    private final TextField limit;
    private final Label limitTech;

    // Set while the draft is drawn, so drawing it is not taken for a person's switch or typing.
    private boolean drawing;

    ModelLimitsSection(final ModelForm form, final Messages messages, final TechnicalDetails technical) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.technical = Objects.requireNonNull(technical, "technical");
        context = number("models-context", MessageKey.SETTINGS_MODELS_CONTEXT_TIP);
        limit = number("models-limit", MessageKey.SETTINGS_MODELS_LIMIT_TIP);
        contextTech = note("models-context-tech", MessageKey.SETTINGS_MODELS_CONTEXT_TECH, "tech-note");
        contextOpenAi = note("models-context-openai", MessageKey.SETTINGS_MODELS_CONTEXT_OPENAI, "hint");
        contextEngine = note("models-context-engine", MessageKey.SETTINGS_MODELS_CONTEXT_TECH, "hint");
        limitIgnored = note("models-limit-ignored", MessageKey.SETTINGS_MODELS_LIMIT_IGNORED, "hint");
        limitTech = note("models-limit-tech", MessageKey.SETTINGS_MODELS_LIMIT_TECH, "tech-note");
        contextOn.setId("models-context-on");
        contextOn.setText(messages.get(MessageKey.SETTINGS_MODELS_CONTEXT));
        Tips.install(messages, contextOn, MessageKey.SETTINGS_MODELS_CONTEXT_TIP);
        limitOn.setId("models-limit-on");
        limitOn.setText(messages.get(MessageKey.SETTINGS_MODELS_LIMIT));
        Tips.install(messages, limitOn, MessageKey.SETTINGS_MODELS_LIMIT_TIP);
        contextOn.selectedProperty().addListener((observed, was, now) -> switched(now, true));
        limitOn.selectedProperty().addListener((observed, was, now) -> switched(now, false));
        context.textProperty().addListener((observed, was, now) -> typed(now, true));
        limit.textProperty().addListener((observed, was, now) -> typed(now, false));
        box.getChildren()
                .addAll(
                        block(
                                contextOn,
                                context,
                                contextTech,
                                "models-context-hint",
                                MessageKey.SETTINGS_MODELS_CONTEXT_HINT),
                        contextEngine,
                        contextOpenAi,
                        block(limitOn, limit, limitTech, "models-limit-hint", MessageKey.SETTINGS_MODELS_LIMIT_HINT),
                        limitIgnored);
    }

    VBox node() {
        return box;
    }

    /** Draws both numbers from the draft, the loaded figure as the window's suggestion and the technical names. */
    void show() {
        final ModelDraft draft = form.draft().get();
        drawing = true;
        try {
            contextOn.setSelected(draft.contextOn());
            limitOn.setSelected(draft.limitOn());
            showWindow(draft);
            replace(limit, draft.limitText());
            limit.setDisable(!draft.limitOn());
        } finally {
            drawing = false;
        }
        showNames(form.selected().get());
        Visibility.show(
                limitIgnored,
                form.facts().get().verdicts().capField() != null
                        && form.facts().get().verdicts().capField().outcome() == ProbeVerdicts.Outcome.IGNORED);
    }

    // The engines that load the model with their own window; native Ollama takes BookLoom's num_ctx, so it is not here.
    private static @Nullable ServerEngine windowSetBy(final @Nullable ModelEntry entry) {
        if (entry == null || entry.kind() == ProviderKind.OLLAMA) {
            return null;
        }
        return switch (entry.engine()) {
            case LM_STUDIO, LLAMA_CPP, OLLAMA -> entry.engine();
            case GENERIC, OPENROUTER, UNSLOTH -> null;
        };
    }

    private void showEngine(final @Nullable ServerEngine setBy) {
        Visibility.show(contextEngine, setBy != null);
        if (setBy != null) {
            contextEngine.setText(messages.get(MessageKey.SETTINGS_MODELS_CONTEXT_ENGINE, engineToken(setBy)));
        }
    }

    private static String engineToken(final ServerEngine engine) {
        return switch (engine) {
            case LM_STUDIO -> "lmStudio";
            case LLAMA_CPP -> "llamaServer";
            case OLLAMA -> "ollama";
            case GENERIC, OPENROUTER, UNSLOTH -> "other";
        };
    }

    private void showWindow(final ModelDraft draft) {
        final ModelFacts facts = form.facts().get();
        final @Nullable ServerEngine setBy = windowSetBy(form.selected().get());
        final @Nullable Integer loaded = facts.loaded();
        final String text;
        if (setBy == null) {
            text = draft.contextOn() ? draft.contextText() : Objects.toString(loaded, "");
        } else {
            text = Objects.toString(loaded != null ? loaded : facts.contextLength(), "");
        }
        replace(context, text);
        context.setDisable(setBy != null || !draft.contextOn());
        contextOn.setDisable(setBy != null);
        showEngine(setBy);
    }

    private void showNames(final ModelEntry entry) {
        final boolean ollama = entry != null && entry.kind() == ProviderKind.OLLAMA;
        Visibility.show(contextTech, ollama && technical.isOn());
        Visibility.show(contextOpenAi, entry != null && !ollama);
        Visibility.show(limitTech, technical.isOn());
    }

    private void switched(final boolean on, final boolean isContext) {
        if (drawing) {
            return;
        }
        if (isContext) {
            form.switchContext(on);
        } else {
            form.edit(draft -> draft.withLimit(on, draft.limitText()));
        }
    }

    private void typed(final String text, final boolean isContext) {
        if (drawing) {
            return;
        }
        final BiFunction<ModelDraft, String, ModelDraft> change =
                isContext ? ModelDraft::withContextText : ModelDraft::withLimitText;
        form.edit(draft -> change.apply(draft, text));
    }

    private VBox block(
            final ToggleSwitch toggle,
            final TextField field,
            final Label tech,
            final String hintId,
            final MessageKey hintKey) {
        final Label hint = note(hintId, hintKey, "hint");
        final HBox line = new HBox(12, toggle, field, tech);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, line, hint);
    }

    private Label note(final String id, final MessageKey text, final String style) {
        final Label label = new Label(messages.get(text));
        label.setId(id);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }

    private TextField number(final String id, final MessageKey tip) {
        final TextField field = new TextField();
        field.setId(id);
        field.getStyleClass().add("w-num");
        Tips.install(messages, field, tip);
        return field;
    }

    private static void replace(final TextField field, final String text) {
        if (!text.equals(field.getText())) {
            field.setText(text);
        }
    }
}
