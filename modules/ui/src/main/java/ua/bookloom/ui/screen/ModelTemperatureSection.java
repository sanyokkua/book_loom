package ua.bookloom.ui.screen;

import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelDraft;
import ua.bookloom.ui.state.ModelForm;

/**
 * The temperature of the Models tab: the three ways it is sent, the slider for one value of one's own and the line that
 * says what the chosen way means. It only draws the draft and turns a click or a drag back into a draft change.
 */
final class ModelTemperatureSection {

    private static final double STEP = 0.05;

    private final ModelForm form;
    private final Messages messages;
    private final VBox box = new VBox(8);
    private final ToggleButton choose = toggle("models-temperature-choose");
    private final ToggleButton value = toggle("models-temperature-value");
    private final ToggleButton omit = toggle("models-temperature-omit");
    private final Slider slider = new Slider(ModelSettings.MIN_TEMPERATURE, ModelSettings.MAX_TEMPERATURE, 0);
    private final Label readout = new Label();
    private final Label hint = new Label();
    private final HBox sliderRow = new HBox(10, slider, readout);

    // Set while the draft is drawn, so drawing it is not taken for a person's click or drag.
    private boolean drawing;

    ModelTemperatureSection(final ModelForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        final SegmentedButton segmented = new SegmentedButton(choose, value, omit);
        segmented.setId("models-temperature");
        label(choose, MessageKey.SETTINGS_MODELS_TEMPERATURE_CHOOSE, MessageKey.SETTINGS_MODELS_TEMPERATURE_CHOOSE_TIP);
        label(value, MessageKey.SETTINGS_MODELS_TEMPERATURE_VALUE, MessageKey.SETTINGS_MODELS_TEMPERATURE_VALUE_TIP);
        label(omit, MessageKey.SETTINGS_MODELS_TEMPERATURE_OMIT, MessageKey.SETTINGS_MODELS_TEMPERATURE_OMIT_TIP);
        buildSlider();
        hint.setId("models-temperature-hint");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        segmented.setMaxWidth(Double.NEGATIVE_INFINITY);
        segmented.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> picked(now));
        box.getChildren().addAll(heading(), segmented, sliderRow, hint);
    }

    VBox node() {
        return box;
    }

    /** Draws the draft's choice, value and the line that explains the choice. */
    void show(final ModelDraft draft) {
        drawing = true;
        try {
            final ToggleButton wanted = toggleOf(draft.choice());
            if (!wanted.isSelected()) {
                wanted.setSelected(true);
            }
            if (Math.abs(slider.getValue() - draft.temperature()) > 1e-9) {
                slider.setValue(draft.temperature());
            }
            readout.setText(String.format(Locale.ROOT, "%.2f", draft.temperature()));
            hint.setText(messages.get(hintOf(draft.choice())));
            final boolean own = draft.choice() == TemperatureChoice.VALUE;
            for (final Node node : new Node[] {slider, readout}) {
                node.setVisible(own);
                node.setManaged(own);
            }
        } finally {
            drawing = false;
        }
    }

    private HBox heading() {
        final Label title = new Label(messages.get(MessageKey.SETTINGS_MODELS_TEMPERATURE));
        title.getStyleClass().add("card-title");
        final Label sub = new Label(messages.get(MessageKey.SETTINGS_MODELS_TEMPERATURE_SUB));
        sub.getStyleClass().add("muted");
        return new HBox(8, title, sub);
    }

    private void buildSlider() {
        slider.setId("models-temperature-slider");
        slider.setBlockIncrement(STEP);
        slider.setMaxWidth(260);
        slider.setAccessibleText(messages.get(MessageKey.SETTINGS_MODELS_TEMPERATURE_SLIDER));
        HBox.setHgrow(slider, Priority.NEVER);
        Tips.install(messages, slider, MessageKey.SETTINGS_MODELS_TEMPERATURE_SLIDER_TIP);
        readout.setId("models-temperature-readout");
        readout.getStyleClass().add("temperature-value");
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        slider.valueProperty().addListener((observed, was, now) -> {
            if (!drawing) {
                form.edit(draft -> draft.withTemperature(now.doubleValue()));
            }
        });
    }

    private void picked(final Toggle now) {
        if (drawing) {
            return;
        }
        if (now == null) {
            // A toggle in a group deselects when pressed again; the choice has not changed, so it must still show.
            show(form.draft().get());
        } else {
            form.edit(draft -> draft.withChoice(choiceOf(now)));
        }
    }

    private TemperatureChoice choiceOf(final Toggle toggle) {
        if (value.equals(toggle)) {
            return TemperatureChoice.VALUE;
        }
        return omit.equals(toggle) ? TemperatureChoice.OMIT : TemperatureChoice.CHOOSE;
    }

    private ToggleButton toggleOf(final TemperatureChoice choice) {
        return switch (choice) {
            case CHOOSE -> choose;
            case VALUE -> value;
            case OMIT -> omit;
        };
    }

    private static MessageKey hintOf(final TemperatureChoice choice) {
        return switch (choice) {
            case CHOOSE -> MessageKey.SETTINGS_MODELS_TEMPERATURE_HINT_CHOOSE;
            case VALUE -> MessageKey.SETTINGS_MODELS_TEMPERATURE_HINT_VALUE;
            case OMIT -> MessageKey.SETTINGS_MODELS_TEMPERATURE_HINT_OMIT;
        };
    }

    private void label(final ToggleButton button, final MessageKey text, final MessageKey tip) {
        button.setText(messages.get(text));
        Tips.install(messages, button, tip);
    }

    private static ToggleButton toggle(final String id) {
        final ToggleButton button = new ToggleButton();
        button.setId(id);
        return button;
    }
}
