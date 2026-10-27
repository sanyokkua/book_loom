package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelDraft;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The fold for the rarely needed field that carries the answer length. An OpenAI-compatible server is offered the two
 * fields; Ollama has its own option, so it is only told so. The fold is open while technical details are on.
 */
final class ModelAdvancedFold {

    private final ModelForm form;
    private final TitledPane fold = new TitledPane();
    private final Label heading;
    private final ToggleButton completion = new ToggleButton();
    private final ToggleButton tokens = new ToggleButton();
    private final SegmentedButton segmented = new SegmentedButton(completion, tokens);
    private final Label ollama;

    // Held here: the singleton keeps only a weak reference, so the listener lives and dies with this fold.
    private final ChangeListener<Boolean> onTechnical = (observed, was, now) -> fold.setExpanded(now);

    // Set while the draft is drawn, so drawing it is not taken for a person's choice.
    private boolean drawing;

    ModelAdvancedFold(final ModelForm form, final Messages messages, final TechnicalDetails technical) {
        this.form = Objects.requireNonNull(form, "form");
        Objects.requireNonNull(messages, "messages");
        heading = text(messages, "models-field-heading", MessageKey.SETTINGS_MODELS_FIELD);
        ollama = text(messages, "models-field-ollama", MessageKey.SETTINGS_MODELS_FIELD_OLLAMA);
        ollama.getStyleClass().add("hint");
        label(messages, completion, "models-field-completion", MessageKey.SETTINGS_MODELS_FIELD_COMPLETION);
        label(messages, tokens, "models-field-tokens", MessageKey.SETTINGS_MODELS_FIELD_TOKENS);
        Tips.install(messages, completion, MessageKey.SETTINGS_MODELS_FIELD_COMPLETION_TIP);
        Tips.install(messages, tokens, MessageKey.SETTINGS_MODELS_FIELD_TOKENS_TIP);
        segmented.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> picked(now));
        segmented.setMaxWidth(Double.NEGATIVE_INFINITY);
        fold.setId("models-advanced");
        fold.getStyleClass().addAll("import-fold", "settings-fold");
        fold.setText(messages.get(MessageKey.SETTINGS_MODELS_ADVANCED));
        fold.setAnimated(false);
        fold.setMinHeight(Region.USE_PREF_SIZE);
        fold.setExpanded(technical.isOn());
        Tips.installOnTitle(messages, fold, MessageKey.SETTINGS_MODELS_ADVANCED_TIP);
        fold.setContent(new VBox(8, heading, segmented, ollama));
        technical.property().addListener(new WeakChangeListener<>(onTechnical));
    }

    Node node() {
        return fold;
    }

    /** Draws the chosen field and which of the two kinds of content the model's provider needs. */
    void show() {
        final ModelDraft draft = form.draft().get();
        final ModelEntry entry = form.selected().get();
        final boolean isOllama = entry != null && entry.kind() == ProviderKind.OLLAMA;
        final boolean offered = entry != null && !isOllama;
        for (final Node node : new Node[] {heading, segmented, completion, tokens}) {
            node.setVisible(offered);
            node.setManaged(offered);
        }
        ollama.setVisible(isOllama);
        ollama.setManaged(isOllama);
        drawing = true;
        try {
            final ToggleButton wanted = draft.field() == OutputLengthField.MAX_COMPLETION_TOKENS ? completion : tokens;
            if (!wanted.isSelected()) {
                wanted.setSelected(true);
            }
        } finally {
            drawing = false;
        }
    }

    private void picked(final Toggle now) {
        if (drawing) {
            return;
        }
        if (now == null) {
            show();
        } else {
            form.edit(draft -> draft.withField(
                    completion.equals(now) ? OutputLengthField.MAX_COMPLETION_TOKENS : OutputLengthField.MAX_TOKENS));
        }
    }

    private static Label text(final Messages messages, final String id, final MessageKey key) {
        final Label label = new Label(messages.get(key));
        label.setId(id);
        label.setWrapText(true);
        label.getStyleClass().add("muted");
        return label;
    }

    private static void label(
            final Messages messages, final ToggleButton button, final String id, final MessageKey key) {
        button.setId(id);
        button.setText(messages.get(key));
    }
}
