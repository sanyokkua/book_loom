package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderDraft.HeaderRow;
import ua.bookloom.ui.state.ProviderForm;

/**
 * The two optional lists of a provider: extra request headers and extra model names. Each sits behind a switch that
 * only shows the list; turning it off empties the list in the draft.
 */
final class ProviderExtrasSection {

    private final ProviderForm form;
    private final Messages messages;
    private final VBox box = new VBox(10);
    private final ToggleSwitch headersOn = new ToggleSwitch();
    private final VBox headerRows = new VBox(6);
    private final VBox headersPart = new VBox(6);
    private final Label headersError = new Label();
    private final ToggleSwitch modelsOn = new ToggleSwitch();
    private final FlowPane chips = new FlowPane(6, 6);
    private final TextField modelInput = new TextField();
    private final VBox modelsPart = new VBox(6);
    private List<String> shownModels = List.of();

    ProviderExtrasSection(final ProviderForm form, final Messages messages) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        buildHeaders();
        buildModels();
        box.getChildren().addAll(headersOn, headersPart, modelsOn, modelsPart);
    }

    VBox node() {
        return box;
    }

    private void buildHeaders() {
        headersOn.setId("settings-form-headers-on");
        headersOn.setText(messages.get(MessageKey.SETTINGS_FORM_HEADERS_ON));
        Tips.install(messages, headersOn, MessageKey.SETTINGS_FORM_HEADERS_ON_TIP);
        headerRows.setId("settings-form-header-rows");
        final Button add = new Button(messages.get(MessageKey.SETTINGS_FORM_HEADERS_ADD));
        add.setId("settings-form-header-add");
        add.getStyleClass().add("btn-ghost");
        Tips.install(messages, add, MessageKey.SETTINGS_FORM_HEADERS_ADD_TIP);
        add.setOnAction(event -> form.edit(draft -> draft.withHeaders(plus(draft.headers(), new HeaderRow("", "")))));
        final Label hint = new Label(messages.get(MessageKey.SETTINGS_FORM_HEADERS_HINT));
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        headersError.setId("settings-form-headers-error");
        headersError.getStyleClass().add("hint-err");
        headersError.setWrapText(true);
        showHeadersError("");
        headersPart.getChildren().addAll(headerRows, headersError, add, hint);
        bindPart(headersOn, headersPart);
        headersOn.selectedProperty().addListener((observed, was, now) -> {
            if (!now) {
                form.edit(draft -> draft.withHeaders(List.of()));
            }
        });
    }

    private void buildModels() {
        modelsOn.setId("settings-form-models-on");
        modelsOn.setText(messages.get(MessageKey.SETTINGS_FORM_MODELS_ON));
        Tips.install(messages, modelsOn, MessageKey.SETTINGS_FORM_MODELS_ON_TIP);
        chips.setId("settings-form-model-chips");
        modelInput.setId("settings-form-model-input");
        modelInput.getStyleClass().add("w-model");
        modelInput.setPromptText(messages.get(MessageKey.SETTINGS_FORM_MODELS_ADD));
        Tips.install(messages, modelInput, MessageKey.SETTINGS_FORM_MODELS_ADD_TIP);
        modelInput.setOnAction(event -> addTyped());
        modelsPart.getChildren().addAll(chips, modelInput);
        bindPart(modelsOn, modelsPart);
        modelsOn.selectedProperty().addListener((observed, was, now) -> {
            if (!now) {
                form.edit(draft -> draft.withCustomModels(List.of()));
            }
        });
    }

    private static void bindPart(final ToggleSwitch toggle, final VBox part) {
        Visibility.bind(part, toggle.selectedProperty());
    }

    private void addTyped() {
        final String typed = modelInput.getText().strip();
        modelInput.clear();
        if (!typed.isEmpty()) {
            form.edit(draft -> draft.customModels().contains(typed)
                    ? draft
                    : draft.withCustomModels(plus(draft.customModels(), typed)));
        }
    }

    private static <T> List<T> plus(final List<T> list, final T added) {
        final List<T> grown = new ArrayList<>(list);
        grown.add(added);
        return grown;
    }

    /**
     * Shows what is wrong with the header rows under them.
     *
     * @param text the translated finding; empty when the rows are fine
     */
    void showHeadersError(final String text) {
        headersError.setText(text);
        Visibility.show(headersError, !text.isEmpty());
    }

    /** Shows the draft's lists; a switch that was turned on stays on while its list is still empty. */
    void show(final ProviderDraft draft) {
        headersOn.setSelected(headersOn.isSelected() || !draft.headers().isEmpty());
        modelsOn.setSelected(modelsOn.isSelected() || !draft.customModels().isEmpty());
        showHeaders(draft.headers());
        showModels(draft.customModels());
    }

    /** Sets both switches from the draft alone, for a draft that belongs to another provider. */
    void reset(final ProviderDraft draft) {
        headersOn.setSelected(!draft.headers().isEmpty());
        modelsOn.setSelected(!draft.customModels().isEmpty());
        modelInput.clear();
    }

    private void showHeaders(final List<HeaderRow> rows) {
        if (headerRows.getChildren().size() != rows.size()) {
            headerRows.getChildren().clear();
            for (int i = 0; i < rows.size(); i++) {
                headerRows.getChildren().add(headerRow(i));
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            final HBox row = (HBox) headerRows.getChildren().get(i);
            update((TextField) row.getChildren().get(0), rows.get(i).name());
            update((TextField) row.getChildren().get(1), rows.get(i).value());
        }
    }

    private static void update(final TextField field, final String text) {
        if (!text.equals(field.getText())) {
            field.setText(text);
        }
    }

    private HBox headerRow(final int index) {
        final TextField name = headerField(
                "settings-form-header-name-" + index,
                "w-name",
                MessageKey.SETTINGS_FORM_HEADERS_NAME,
                MessageKey.SETTINGS_FORM_HEADERS_NAME_TIP);
        final TextField value = headerField(
                "settings-form-header-value-" + index,
                "w-url",
                MessageKey.SETTINGS_FORM_HEADERS_VALUE,
                MessageKey.SETTINGS_FORM_HEADERS_VALUE_TIP);
        name.textProperty().addListener((observed, was, now) -> editRow(index, row -> new HeaderRow(now, row.value())));
        value.textProperty().addListener((observed, was, now) -> editRow(index, row -> new HeaderRow(row.name(), now)));
        final Button remove = new Button(messages.get(MessageKey.SETTINGS_FORM_HEADERS_REMOVE));
        remove.setId("settings-form-header-remove-" + index);
        remove.getStyleClass().add("btn-ghost");
        Tips.install(messages, remove, MessageKey.SETTINGS_FORM_HEADERS_REMOVE_TIP);
        remove.setOnAction(event -> form.edit(draft -> draft.withHeaders(without(draft.headers(), index))));
        HBox.setHgrow(value, Priority.ALWAYS);
        return new HBox(8, name, value, remove);
    }

    private TextField headerField(final String id, final String width, final MessageKey prompt, final MessageKey tip) {
        final TextField field = new TextField();
        field.setId(id);
        field.getStyleClass().add(width);
        field.setPromptText(messages.get(prompt));
        Tips.install(messages, field, tip);
        return field;
    }

    private void editRow(final int index, final UnaryOperator<HeaderRow> change) {
        form.edit(draft -> {
            if (index >= draft.headers().size()) {
                return draft;
            }
            final List<HeaderRow> rows = new ArrayList<>(draft.headers());
            rows.set(index, change.apply(rows.get(index)));
            return draft.withHeaders(rows);
        });
    }

    private static List<HeaderRow> without(final List<HeaderRow> rows, final int index) {
        final List<HeaderRow> kept = new ArrayList<>(rows);
        kept.remove(index);
        return kept;
    }

    private void showModels(final List<String> models) {
        if (models.equals(shownModels)) {
            return;
        }
        shownModels = List.copyOf(models);
        chips.getChildren().clear();
        for (int i = 0; i < models.size(); i++) {
            final String model = models.get(i);
            final Button chip = new Button(model + "  " + messages.get(MessageKey.SETTINGS_FORM_MODELS_REMOVE));
            chip.setId("settings-form-model-chip-" + i);
            chip.getStyleClass().addAll("chip", "chip-neutral");
            Tips.install(chip, messages.get(MessageKey.SETTINGS_FORM_MODELS_REMOVE_TIP, model));
            chip.setOnAction(event -> form.edit(draft -> draft.withCustomModels(draft.customModels().stream()
                    .filter(name -> !name.equals(model))
                    .toList())));
            chips.getChildren().add(chip);
        }
    }
}
