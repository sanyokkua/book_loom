package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderDraft;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The body of the Providers form: the summary card and, folded under it, every connection field, the model entry, the
 * optional lists and the checks. It only draws what {@link ProviderForm} holds and turns edits back into draft
 * changes; the form outlives it, so it listens through weak listeners held by the fields below.
 */
@Slf4j
final class ProviderFormView {

    private final ProviderForm form;
    private final Messages messages;
    private final VBox root = new VBox(14);
    private final TitledPane fold = new TitledPane();
    private final Label errorsText = new Label();
    private final VBox errors = new VBox(errorsText);
    private final ProviderConnectionSection connection;
    private final ProviderExtrasSection extras;
    private final ModelField model;
    private final ChangeListener<ProviderDraft> onDraft = (observed, was, now) -> showDraft(now);
    private final ChangeListener<Object> onFeedback = (observed, was, now) -> showFeedback();
    private final ChangeListener<Boolean> onFoldOpen = (observed, was, now) -> fold.setExpanded(now);
    private final ChangeListener<Boolean> onRefused = (observed, was, now) -> openFoldOn(now);
    private final ChangeListener<ProviderForm.Spot> onFocusRequest = (observed, was, now) -> focus(now);
    private final ChangeListener<String> onProviderChanged = (observed, was, now) -> resetExtras();

    ProviderFormView(
            final SettingsViewModel viewModel,
            final ProviderForm form,
            final Messages messages,
            final ProviderTestDialog dialog) {
        this.form = Objects.requireNonNull(form, "form");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(viewModel, "viewModel");
        connection = new ProviderConnectionSection(form, messages);
        extras = new ProviderExtrasSection(form, messages);
        model = new ModelField(viewModel, form, messages);
        buildFold(new ProviderChecks(viewModel, messages, dialog));
        root.getChildren().addAll(new ProviderSummaryCard(viewModel, form, messages, dialog).node(), fold);
        form.draft().addListener(new WeakChangeListener<>(onDraft));
        form.findings().addListener(new WeakChangeListener<>(onFeedback));
        form.errorsShown().addListener(new WeakChangeListener<>(onFeedback));
        form.saveFailed().addListener(new WeakChangeListener<>(onFeedback));
        form.dirty().addListener(new WeakChangeListener<>(onFeedback));
        form.credentialStatus().addListener(new WeakChangeListener<>(onFeedback));
        form.errorsShown().addListener(new WeakChangeListener<>(onRefused));
        form.saveFailed().addListener(new WeakChangeListener<>(onRefused));
        form.foldOpen().addListener(new WeakChangeListener<>(onFoldOpen));
        form.focusRequest().addListener(new WeakChangeListener<>(onFocusRequest));
        viewModel.editedProviderId().addListener(new WeakChangeListener<>(onProviderChanged));
        viewModel.adding().addListener(new WeakChangeListener<>(onFeedback));
        extras.reset(form.draft().get());
        showDraft(form.draft().get());
        showFeedback();
        fold.setExpanded(form.foldOpen().get());
    }

    VBox node() {
        return root;
    }

    ModelField model() {
        return model;
    }

    private void buildFold(final ProviderChecks checks) {
        fold.setId("settings-connection");
        fold.getStyleClass().addAll("import-fold", "settings-fold");
        fold.setText(messages.get(MessageKey.SETTINGS_CONNECTION));
        final Label subtitle = new Label(messages.get(MessageKey.SETTINGS_CONNECTION_SUBTITLE));
        subtitle.getStyleClass().add("muted");
        fold.setGraphic(subtitle);
        fold.setContentDisplay(ContentDisplay.RIGHT);
        fold.setAnimated(false);
        fold.setMinHeight(Region.USE_PREF_SIZE);
        Tips.installOnTitle(messages, fold, MessageKey.SETTINGS_CONNECTION_TIP);
        errors.setId("settings-form-errors");
        errors.getStyleClass().add("form-errors");
        errorsText.setId("settings-form-errors-text");
        errorsText.setWrapText(true);
        final VBox content = new VBox(14, errors, connection.node(), model.node(), extras.node(), checks.node());
        fold.setContent(content);
    }

    private void resetExtras() {
        extras.reset(form.draft().get());
    }

    private void showDraft(final ProviderDraft draft) {
        connection.show(draft);
        extras.show(draft);
        model.showChoices();
    }

    private void showFeedback() {
        connection.showCredential(form.credentialStatus().get());
        connection.showAdvice(form.findings().get());
        showFieldErrors();
        final List<String> lines = form.errorsShown().get()
                ? form.findings().get().stream()
                        .filter(ProviderDraft.FormFinding::blocking)
                        .map(finding ->
                                messages.get(finding.key(), finding.args().toArray()))
                        .collect(Collectors.toList())
                : new ArrayList<>();
        if (form.saveFailed().get()) {
            lines.add(messages.get(MessageKey.SETTINGS_ERROR_SAVE_FAILED));
        }
        errorsText.setText(String.join("\n", lines));
        errors.setVisible(!lines.isEmpty());
        errors.setManaged(!lines.isEmpty());
    }

    // Each field's first blocking finding sits under it once the person has typed or pressed Save; an untouched new
    // form is not scolded for the name nobody has typed yet.
    private void showFieldErrors() {
        final boolean shown = form.dirty().get() || form.errorsShown().get();
        final Map<ProviderDraft.Field, String> errorOf = new EnumMap<>(ProviderDraft.Field.class);
        if (shown) {
            form.findings().get().stream()
                    .filter(ProviderDraft.FormFinding::blocking)
                    .forEach(finding -> errorOf.putIfAbsent(
                            finding.field(),
                            messages.get(finding.key(), finding.args().toArray())));
        }
        connection.showErrors(field -> errorOf.getOrDefault(field, ""));
        extras.showHeadersError(errorOf.getOrDefault(ProviderDraft.Field.HEADERS, ""));
    }

    // A refused or failed save shows its reasons inside the fold, so the fold must not be shut on them.
    private void openFoldOn(final boolean refused) {
        if (refused) {
            log.debug("connection details opened to show why the save did not happen");
            fold.setExpanded(true);
        }
    }

    private void focus(final ProviderForm.Spot spot) {
        if (spot == ProviderForm.Spot.MODEL) {
            fold.setExpanded(true);
            Platform.runLater(model::focus);
        }
    }
}
