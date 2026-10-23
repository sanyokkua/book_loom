package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelFacts;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The body of the Models tab: what the program says about the chosen model, then its temperature, the settings it
 * refused, its two optional numbers and the fold of advanced options. It only draws what {@link ModelForm} holds and
 * turns edits back into draft changes. The form outlives it, so it listens through weak listeners held by the fields
 * below, which live exactly as long as the nodes that use them.
 */
@Slf4j
final class ModelsFormView {

    private final ModelForm form;
    private final Messages messages;
    private final VBox root = new VBox(14);
    private final Banner found = new Banner("models-found", Banner.Role.INFO, "i", "", "");
    private final ModelTemperatureSection temperature;
    private final ModelRefusedSection refused;
    private final ModelLimitsSection limits;
    private final ModelAdvancedFold advanced;
    private final Label errorsText = new Label();
    private final VBox errors = new VBox(errorsText);
    private final ChangeListener<Object> onChange = (observed, was, now) -> showAll();
    private final ListChangeListener<RefusedSetting> onRefused = change -> showRefused();

    ModelsFormView(final ModelForm form, final Messages messages, final TechnicalDetails technical) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        temperature = new ModelTemperatureSection(form, messages);
        refused = new ModelRefusedSection(form, messages);
        limits = new ModelLimitsSection(form, messages, technical);
        advanced = new ModelAdvancedFold(form, messages, technical);
        errors.setId("models-errors");
        errors.getStyleClass().add("form-errors");
        errorsText.setId("models-errors-text");
        errorsText.setWrapText(true);
        root.getChildren().addAll(found, temperature.node(), refused.node(), limits.node(), advanced.node(), errors);
        root.disableProperty().bind(form.reading());
        form.draft().addListener(new WeakChangeListener<>(onChange));
        form.facts().addListener(new WeakChangeListener<>(onChange));
        form.selected().addListener(new WeakChangeListener<>(onChange));
        form.errorsShown().addListener(new WeakChangeListener<>(onChange));
        form.saveFailed().addListener(new WeakChangeListener<>(onChange));
        form.refusedFailed().addListener(new WeakChangeListener<>(onChange));
        technical.property().addListener(new WeakChangeListener<>(onChange));
        form.refused().addListener(new WeakListChangeListener<>(onRefused));
        showAll();
    }

    VBox node() {
        return root;
    }

    private void showRefused() {
        refused.show();
    }

    private void showAll() {
        temperature.show(form.draft().get());
        limits.show();
        advanced.show();
        refused.show();
        showFound(form.facts().get());
        showErrors();
    }

    private void showFound(final ModelFacts facts) {
        if (facts.loading()) {
            found.setText(messages.get(MessageKey.SETTINGS_MODELS_FOUND_LOADING));
            return;
        }
        found.setText(messages.get(MessageKey.SETTINGS_MODELS_FOUND, readable(facts), structured(facts)));
    }

    private String readable(final ModelFacts facts) {
        final Integer length = facts.contextLength();
        if (length == null) {
            return messages.get(MessageKey.SETTINGS_MODELS_FOUND_UNKNOWN);
        }
        final String read = messages.get(MessageKey.SETTINGS_MODELS_FOUND_READ, length);
        final Integer loaded = facts.loaded();
        return loaded == null ? read : messages.get(MessageKey.SETTINGS_MODELS_FOUND_LOADED, read, loaded);
    }

    private String structured(final ModelFacts facts) {
        final Boolean answered = facts.structured();
        if (answered == null) {
            return messages.get(MessageKey.SETTINGS_MODELS_FOUND_UNCHECKED);
        }
        return messages.get(answered ? MessageKey.SETTINGS_MODELS_FOUND_YES : MessageKey.SETTINGS_MODELS_FOUND_NO);
    }

    private void showErrors() {
        final StringBuilder lines = new StringBuilder();
        if (form.errorsShown().get()) {
            form.findings().get().forEach(key -> lines.append(messages.get(key)).append('\n'));
        }
        if (form.saveFailed().get()) {
            lines.append(messages.get(MessageKey.SETTINGS_ERROR_SAVE_FAILED));
        }
        errorsText.setText(lines.toString().strip());
        final boolean shown = !errorsText.getText().isEmpty();
        errors.setVisible(shown);
        errors.setManaged(shown);
    }
}
