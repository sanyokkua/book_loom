package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelDraft;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.SamplingDraft;

/**
 * The Sampling row of the Models tab (design D20): what BookLoom sends to the chosen model, and the two choices,
 * Profile default and Custom. There is no choice that leaves sampling to the server, because a server whose defaults are
 * not neutral would bring them back. Custom shows a field per value; an empty one keeps the profile's, and the
 * temperature is the tab's My value. It only draws the draft and turns a click or a keystroke back into a draft change.
 */
@Slf4j
final class ModelSamplingSection {

    private static final double FIELD_GAP = 12;
    private static final String SEPARATOR = ", ";

    private final ModelForm form;
    private final Messages messages;
    private final VBox box = new VBox(6);
    private final Label sends = Wrapped.wholeLines(new Label());
    private final ToggleGroup group = new ToggleGroup();
    private final RadioButton profile = choice("models-sampling-profile");
    private final RadioButton custom = choice("models-sampling-custom");
    private final VBox choices = new VBox(4, profile, custom);
    private final TextField temperature = field("models-sampling-temperature");
    private final TextField topK = field("models-sampling-top-k");
    private final TextField topP = field("models-sampling-top-p");
    private final TextField minP = field("models-sampling-min-p");
    private final FlowPane fields = new FlowPane(FIELD_GAP, 6);
    private final Label hint = Wrapped.wholeLines(new Label());
    private final VBox customBox = new VBox(6, fields, hint);

    // Set while the draft is drawn, so drawing it is not taken for a person's click or typing.
    private boolean drawing;

    ModelSamplingSection(final ModelForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        box.setId("models-sampling");
        sends.setId("models-sampling-sends");
        choices.setId("models-sampling-choices");
        customBox.setId("models-sampling-fields");
        hint.setId("models-sampling-hint");
        hint.getStyleClass().add("hint");
        hint.setText(messages.get(MessageKey.SETTINGS_MODELS_SAMPLING_HINT));
        label(profile, MessageKey.SETTINGS_MODELS_SAMPLING_PROFILE, MessageKey.SETTINGS_MODELS_SAMPLING_PROFILE_TIP);
        label(custom, MessageKey.SETTINGS_MODELS_SAMPLING_CUSTOM, MessageKey.SETTINGS_MODELS_SAMPLING_CUSTOM_TIP);
        group.selectedToggleProperty().addListener((observed, was, now) -> picked(now));
        buildFields();
        box.getChildren().addAll(heading(), sends, choices, customBox);
    }

    private void buildFields() {
        fields.getChildren()
                .addAll(
                        labelled(
                                temperature,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TEMPERATURE,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TEMPERATURE_TIP,
                                SamplingDraft::withTemperatureText),
                        labelled(
                                topK,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TOP_K,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TOP_K_TIP,
                                SamplingDraft::withTopKText),
                        labelled(
                                topP,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TOP_P,
                                MessageKey.SETTINGS_MODELS_SAMPLING_TOP_P_TIP,
                                SamplingDraft::withTopPText),
                        labelled(
                                minP,
                                MessageKey.SETTINGS_MODELS_SAMPLING_MIN_P,
                                MessageKey.SETTINGS_MODELS_SAMPLING_MIN_P_TIP,
                                SamplingDraft::withMinPText));
    }

    VBox node() {
        return box;
    }

    /** Draws the draft's choice, its typed values and the line that says what every request carries. */
    void show(final ModelDraft draft) {
        final SamplingDraft row = draft.sampling();
        drawing = true;
        try {
            final RadioButton wanted = row.custom() ? custom : profile;
            if (!wanted.isSelected()) {
                wanted.setSelected(true);
            }
            replace(temperature, row.temperatureText());
            replace(topK, row.topKText());
            replace(topP, row.topPText());
            replace(minP, row.minPText());
            Visibility.show(customBox, row.custom());
            sends.setText(sendsText(row));
        } finally {
            drawing = false;
        }
    }

    private String sendsText(final SamplingDraft row) {
        if (!row.custom()) {
            return messages.get(MessageKey.SETTINGS_MODELS_SAMPLING_SENDS_PROFILE);
        }
        final List<String> typed = new ArrayList<>();
        add(typed, MessageKey.SETTINGS_MODELS_SAMPLING_TEMPERATURE, row.temperatureText());
        add(typed, MessageKey.SETTINGS_MODELS_SAMPLING_TOP_K, row.topKText());
        add(typed, MessageKey.SETTINGS_MODELS_SAMPLING_TOP_P, row.topPText());
        add(typed, MessageKey.SETTINGS_MODELS_SAMPLING_MIN_P, row.minPText());
        return typed.isEmpty()
                ? messages.get(MessageKey.SETTINGS_MODELS_SAMPLING_SENDS_CUSTOM_NONE)
                : messages.get(MessageKey.SETTINGS_MODELS_SAMPLING_SENDS_CUSTOM, String.join(SEPARATOR, typed));
    }

    // The name as the field's label shows it, in lower case inside the sentence.
    private void add(final List<String> typed, final MessageKey name, final String text) {
        if (!text.isBlank()) {
            typed.add(messages.get(name).toLowerCase(Locale.ROOT) + " " + text.strip());
        }
    }

    private HBox heading() {
        final Label title = new Label(messages.get(MessageKey.SETTINGS_MODELS_SAMPLING));
        title.getStyleClass().add("card-title");
        final Label sub = new Label(messages.get(MessageKey.SETTINGS_MODELS_SAMPLING_SUB));
        sub.getStyleClass().add("muted");
        return new HBox(8, title, sub);
    }

    private void picked(final @Nullable Toggle now) {
        if (drawing) {
            return;
        }
        if (now == null) {
            // A radio button cannot be deselected by a click, but a cleared group still shows the draft's choice.
            show(form.draft().get());
            return;
        }
        final boolean isCustom = custom.equals(now);
        log.debug("sampling choice picked custom={}", isCustom);
        form.edit(draft -> draft.withCustomSampling(isCustom));
    }

    private HBox labelled(
            final TextField input,
            final MessageKey nameKey,
            final MessageKey tip,
            final BiFunction<SamplingDraft, String, SamplingDraft> change) {
        final String name = messages.get(nameKey);
        final Label label = new Label(name);
        label.setLabelFor(input);
        label.setMinWidth(Label.USE_PREF_SIZE);
        Tips.install(messages, input, tip);
        input.setAccessibleText(name);
        input.textProperty().addListener((observed, was, now) -> {
            if (!drawing) {
                form.edit(draft -> draft.withSampling(change.apply(draft.sampling(), now)));
            }
        });
        final HBox line = new HBox(6, label, input);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
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

    private static TextField field(final String id) {
        final TextField input = new TextField();
        input.setId(id);
        input.getStyleClass().add("w-num");
        return input;
    }

    private static void replace(final TextField input, final String text) {
        if (!text.equals(input.getText())) {
            input.setText(text);
        }
    }
}
