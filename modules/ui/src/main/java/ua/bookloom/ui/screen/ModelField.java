package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelAdvice;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Model entry with its refresh button and the notes about the listing: an editable combo whose text is the view
 * model's typed model. The view model outlives this field, so it is observed through weak listeners held by the fields
 * below, which live exactly as long as the nodes that use them.
 */
@Slf4j
final class ModelField {

    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final Messages messages;
    private final ComboBox<String> combo = new ComboBox<>();
    private final Label note = new Label();
    private final VBox box = new VBox(6);
    private final ListChangeListener<String> onOffered = change -> showOffered();
    private final ChangeListener<String> onEntryText = (observed, was, now) -> onEntryTyped(now);
    private final ChangeListener<String> onModel = (observed, was, now) -> showModel(now);
    private final ChangeListener<List<ModelAdvice.Note>> onNotes = (observed, was, now) -> showNotes(now);

    // Set while this field changes the combo's items, editor or value, so the events that causes are not mistaken
    // for a person typing or picking; FX thread only.
    private boolean applying;

    ModelField(final SettingsViewModel viewModel, final ProviderForm form, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        build();
    }

    VBox node() {
        return box;
    }

    ComboBox<String> combo() {
        return combo;
    }

    /** Puts the caret in the entry. */
    void focus() {
        combo.getEditor().requestFocus();
    }

    /** Lists the models again when the person added or removed names of their own. */
    void showChoices() {
        if (!combo.getItems().equals(form.modelChoices())) {
            showOffered();
        }
    }

    private void build() {
        final ModelListing listing = viewModel.modelListing();
        combo.setId("settings-model");
        combo.setEditable(true);
        combo.setPromptText(messages.get(MessageKey.SETTINGS_MODEL_NOT_CHOSEN));
        combo.getStyleClass().add("w-model");
        Tips.install(messages, combo, MessageKey.SETTINGS_MODEL_LABEL_TIP);
        combo.getItems().setAll(form.modelChoices());
        applyText(viewModel.draftModel().get());
        combo.getEditor().textProperty().addListener(onEntryText);
        viewModel.draftModel().addListener(new WeakChangeListener<>(onModel));
        listing.offered().addListener(new WeakListChangeListener<>(onOffered));
        viewModel.modelNotes().addListener(new WeakChangeListener<>(onNotes));
        combo.setOnAction(event -> onEntryCommitted());
        configureNote();
        showNotes(viewModel.modelNotes().get());
        box.getChildren().addAll(entryRow(), note);
        box.getChildren()
                .addAll(
                        hint(
                                "settings-model-listing",
                                messages.get(MessageKey.SETTINGS_MODEL_LISTING),
                                listing.listing()),
                        hint(
                                "settings-model-nolist",
                                messages.get(MessageKey.SETTINGS_MODEL_NO_LIST),
                                listing.noListObtained()),
                        refusal(listing),
                        Wrapped.label(messages.get(MessageKey.SETTINGS_FORM_MODEL_HINT), "hint"));
    }

    private HBox entryRow() {
        final Button refresh = new Button(messages.get(MessageKey.SETTINGS_FORM_MODEL_REFRESH));
        refresh.setId("settings-model-refresh");
        refresh.getStyleClass().add("btn-ghost");
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, refresh, MessageKey.SETTINGS_FORM_MODEL_REFRESH_TIP);
        refresh.setOnAction(event -> viewModel.refreshModels());
        HBox.setHgrow(combo, Priority.SOMETIMES);
        return new HBox(8, combo, refresh);
    }

    private Label refusal(final ModelListing listing) {
        final Label refusal = new Label();
        refusal.setId("settings-model-refusal");
        refusal.getStyleClass().add("hint");
        refusal.setWrapText(true);
        refusal.textProperty().bind(listing.refusal());
        Visibility.bind(
                refusal,
                Bindings.createBooleanBinding(() -> !listing.refusal().get().isEmpty(), listing.refusal()));
        return refusal;
    }

    private static Label hint(final String id, final String text, final ObservableBooleanValue shown) {
        final Label label = Wrapped.label(text, "hint");
        label.setId(id);
        Visibility.bind(label, shown);
        return label;
    }

    // A note is a sentence or two; it wraps under the entry rather than being cut at the card's edge.
    private void configureNote() {
        note.setId("settings-model-note");
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
    }

    private void showNotes(final List<ModelAdvice.Note> notes) {
        ModelNoteLabel.show(note, notes, messages);
    }

    private void onEntryTyped(final String text) {
        if (applying) {
            return;
        }
        log.trace("model entry now holds '{}'", text);
        viewModel.setModelText(text);
        applyValue(text);
    }

    private void onEntryCommitted() {
        if (!applying) {
            log.debug("model entry committed");
            viewModel.commitModel();
        }
    }

    private void showModel(final String model) {
        if (!model.equals(combo.getEditor().getText())) {
            log.debug("model text of {} character(s) shown in the entry", model.length());
            applyText(model);
        }
    }

    private void showOffered() {
        final List<String> choices = form.modelChoices();
        log.debug("showing {} model(s) to choose from", choices.size());
        final String typed = combo.getEditor().getText();
        applying = true;
        try {
            combo.getItems().setAll(choices);
        } finally {
            applying = false;
        }
        applyText(typed);
    }

    private void applyText(final String text) {
        applying = true;
        try {
            combo.getEditor().setText(text);
            combo.setValue(text.isEmpty() ? null : text);
        } finally {
            applying = false;
        }
    }

    /**
     * Keeps the combo's value equal to what was typed: an editable combo whose value is not one of its items would
     * otherwise have its skin reset the value, and with it the entry, when the items change.
     */
    private void applyValue(final String text) {
        applying = true;
        try {
            combo.setValue(text.isEmpty() ? null : text);
        } finally {
            applying = false;
        }
    }
}
