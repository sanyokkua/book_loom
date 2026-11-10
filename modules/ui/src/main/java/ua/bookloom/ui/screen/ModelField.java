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
import ua.bookloom.ui.control.ModelFilterField;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelListing;
import ua.bookloom.ui.state.ModelNameFilter;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The Model entry with its refresh button and the hints about the listing: an editable combo whose text is the view
 * model's typed model. The view model outlives this field, so it is observed through weak listeners held by the fields
 * below, which live exactly as long as the nodes that use them.
 */
@Slf4j
final class ModelField {

    private final SettingsViewModel viewModel;
    private final ProviderForm form;
    private final Messages messages;
    private final ComboBox<String> combo = new ComboBox<>();
    private final ModelFilterField filter;
    private static final int MAX_VISIBLE_ROWS = 10;

    private final VBox box = new VBox(6);
    private final ListChangeListener<String> onOffered = change -> showOffered();
    private final ChangeListener<String> onEntryText = (observed, was, now) -> onEntryTyped(now);
    private final ChangeListener<String> onModel = (observed, was, now) -> showModel(now);

    // Set while this field changes the combo's items, editor or value, so the events that causes are not mistaken
    // for a person typing or picking; FX thread only.
    private boolean applying;

    ModelField(final SettingsViewModel viewModel, final ProviderForm form, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.filter = new ModelFilterField(
                messages, "settings-model-filter", "settings-model-filter-none", this::onFilterTyped, this::openList);
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
        if (!combo.getItems().equals(matching(combo.getEditor().getText()))) {
            showOffered();
        }
    }

    private void build() {
        final ModelListing listing = viewModel.modelListing();
        configureCombo(listing);
        applyText(viewModel.draftModel().get());
        combo.getEditor().textProperty().addListener(onEntryText);
        viewModel.draftModel().addListener(new WeakChangeListener<>(onModel));
        listing.offered().addListener(new WeakListChangeListener<>(onOffered));
        combo.setOnAction(event -> onEntryCommitted());
        box.getChildren().addAll(filter.node(), entryRow());
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

    private void configureCombo(final ModelListing listing) {
        combo.setId("settings-model");
        combo.setEditable(true);
        combo.setPromptText(messages.get(MessageKey.SETTINGS_MODEL_NOT_CHOSEN));
        combo.getStyleClass().add("w-model");
        Tips.install(messages, combo, MessageKey.SETTINGS_MODEL_LABEL_TIP);
        combo.getItems().setAll(form.modelChoices());
        combo.setCellFactory(list -> new ModelChoiceCell(
                listing,
                viewModel.quantChips(),
                () -> viewModel.editedProviderId().get(),
                messages));
        viewModel.quantChips().fromNames(viewModel.editedProviderId().get(), form.modelChoices());
    }

    private HBox entryRow() {
        final Button refresh = new Button(messages.get(MessageKey.SETTINGS_FORM_MODEL_REFRESH));
        refresh.setId("settings-model-refresh");
        refresh.getStyleClass().add("btn-ghost");
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, refresh, MessageKey.SETTINGS_FORM_MODEL_REFRESH_TIP);
        // The button shows only an arrow; a screen reader reads what it does instead.
        refresh.setAccessibleText(messages.get(MessageKey.SETTINGS_FORM_MODEL_REFRESH_TIP));
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

    private void onEntryTyped(final String text) {
        if (applying) {
            return;
        }
        log.trace("model entry now holds '{}'", text);
        viewModel.setModelText(text);
        narrow(text);
    }

    private void onEntryCommitted() {
        if (!applying) {
            log.debug("model entry committed");
            viewModel.commitModel();
            // Settling on a model of a saved provider reads its facts for the chip; typing never does.
            viewModel.testedSubject().get().ifPresent(viewModel.quantChips()::readFacts);
            narrow("");
        }
    }

    // The offered names that contain the entry's text and the filter's text anywhere, ignoring case and accents.
    private List<String> matching(final String text) {
        final List<String> byEntry = ModelNameFilter.narrow(form.modelChoices(), id -> id, id -> id, text);
        return ModelNameFilter.narrow(byEntry, id -> id, id -> id, filter.text());
    }

    // The filter only changes which names are listed; the entry, the value and the draft stay as they are.
    private boolean onFilterTyped(final String typed) {
        final String entry = combo.getEditor().getText();
        final List<String> shown = matching(entry);
        applying = true;
        try {
            if (combo.isShowing()) {
                combo.hide();
            }
            combo.getItems().setAll(shown);
            combo.setVisibleRowCount(Math.clamp(shown.size(), 1, MAX_VISIBLE_ROWS));
            combo.getEditor().setText(entry);
            combo.setValue(entry.isEmpty() ? null : entry);
        } finally {
            applying = false;
        }
        return !shown.isEmpty();
    }

    private void openList() {
        combo.getEditor().requestFocus();
        combo.show();
    }

    /**
     * Narrows the list to the names containing {@code filter} while the entry keeps what was typed. Changing the items
     * makes the skin rewrite the entry, and the open popup keeps its first height, so the entry is put back and the
     * popup is reopened at the new size.
     */
    private void narrow(final String filter) {
        final String typed = combo.getEditor().getText();
        final List<String> shown = matching(filter);
        log.debug("narrowing the model list to {} name(s)", shown.size());
        if (combo.isShowing()) {
            combo.hide();
        }
        applying = true;
        try {
            if (!shown.equals(combo.getItems())) {
                combo.getItems().setAll(shown);
            }
            combo.setVisibleRowCount(Math.clamp(shown.size(), 1, MAX_VISIBLE_ROWS));
            combo.setValue(typed.isEmpty() ? null : typed);
            combo.getEditor().setText(typed);
            combo.getEditor().positionCaret(typed.length());
        } finally {
            applying = false;
        }
        if (combo.getEditor().isFocused() && !filter.isEmpty() && !shown.isEmpty()) {
            combo.show();
        }
    }

    private void showModel(final String model) {
        if (!model.equals(combo.getEditor().getText())) {
            log.debug("model text of {} character(s) shown in the entry", model.length());
            applyText(model);
        }
    }

    private void showOffered() {
        final String typed = combo.getEditor().getText();
        final List<String> choices = matching(typed);
        log.debug("showing {} model(s) to choose from", choices.size());
        applying = true;
        try {
            combo.getItems().setAll(choices);
        } finally {
            applying = false;
        }
        applyText(typed);
        viewModel.quantChips().fromNames(viewModel.editedProviderId().get(), form.modelChoices());
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
}
