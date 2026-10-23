package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;

/**
 * The card at the top of the Providers form: whose model this is, what the last check found, and the two things a
 * person usually wants next. Its words are computed from the view model on each change, so it can never disagree with
 * the form below it.
 */
final class ProviderSummaryCard {

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final VBox box = new VBox(8);
    private final Label title = new Label();
    private final Label line = new Label();
    private final ListChangeListener<StageChip> onStages = change -> markBad();

    ProviderSummaryCard(final SettingsViewModel viewModel, final ProviderForm form, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(form, "form");
        box.setId("settings-summary");
        box.getStyleClass().add("summary-card");
        title.setId("settings-summary-title");
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        line.setId("settings-summary-line");
        line.getStyleClass().add("muted");
        line.setWrapText(true);
        bindWords();
        box.getChildren().addAll(title, line, new HBox(8, test(), changeModel(form)));
        box.visibleProperty().bind(viewModel.adding().not());
        box.managedProperty().bind(box.visibleProperty());
        viewModel.tests().stages().addListener(new WeakListChangeListener<>(onStages));
        markBad();
    }

    VBox node() {
        return box;
    }

    private void bindWords() {
        title.textProperty()
                .bind(Bindings.createStringBinding(
                        this::titleText,
                        viewModel.editedProviderId(),
                        viewModel.selectedProviderId(),
                        viewModel.draftModel(),
                        viewModel.model(),
                        viewModel.providers(),
                        viewModel.tests().stages()));
        line.textProperty()
                .bind(Bindings.createStringBinding(
                        this::lineText,
                        viewModel.modelListing().offered(),
                        viewModel.tests().stages()));
        line.visibleProperty()
                .bind(Bindings.createBooleanBinding(() -> !line.getText().isEmpty(), line.textProperty()));
        line.managedProperty().bind(line.visibleProperty());
    }

    private Button test() {
        final Button test = new Button(messages.get(MessageKey.SETTINGS_SUMMARY_TEST));
        test.setId("settings-summary-test");
        test.getStyleClass().add("btn-primary");
        Tips.install(messages, test, MessageKey.SETTINGS_SUMMARY_TEST_TIP);
        test.disableProperty().bind(Bindings.not(viewModel.tests().available(ProviderTest.INFERENCE)));
        test.setOnAction(event -> viewModel.tests().run(ProviderTest.INFERENCE));
        return test;
    }

    private Button changeModel(final ProviderForm form) {
        final Button change = new Button(messages.get(MessageKey.SETTINGS_SUMMARY_CHANGE_MODEL));
        change.setId("settings-summary-change-model");
        change.getStyleClass().add("btn-ghost");
        Tips.install(messages, change, MessageKey.SETTINGS_SUMMARY_CHANGE_MODEL_TIP);
        change.setOnAction(event -> form.openFoldAt(ProviderForm.Spot.MODEL));
        return change;
    }

    private String titleText() {
        final String edited = viewModel.editedProviderId().get();
        final boolean inUse = edited.equals(viewModel.selectedProviderId().get());
        // The provider in use shows the model that is saved; another provider shows what is being typed for it.
        final String typed =
                (inUse ? viewModel.model() : viewModel.draftModel()).get().strip();
        final String model = typed.isEmpty() ? messages.get(MessageKey.SETTINGS_SUMMARY_NO_MODEL) : typed;
        final String head = messages.get(
                inUse ? MessageKey.SETTINGS_SUMMARY_CURRENT : MessageKey.SETTINGS_SUMMARY_OTHER,
                viewModel.providerName(edited),
                model);
        return verdict().map(word -> head + " " + word).orElse(head);
    }

    private Optional<String> verdict() {
        final List<StageChip> stages = viewModel.tests().stages();
        if (stages.stream().anyMatch(chip -> chip.status() == StageStatus.FAILED)) {
            return Optional.of(messages.get(MessageKey.SETTINGS_SUMMARY_FAILED));
        }
        return inference()
                .filter(chip -> chip.status() == StageStatus.PASSED)
                .map(chip -> messages.get(MessageKey.SETTINGS_SUMMARY_READY));
    }

    private Optional<StageChip> inference() {
        return viewModel.tests().stages().stream()
                .filter(chip -> chip.stage() == VerificationStage.INFERENCE)
                .findFirst();
    }

    private String lineText() {
        final List<String> parts = new ArrayList<>();
        inference()
                .filter(chip -> chip.status() == StageStatus.PASSED && chip.elapsed() != null)
                .ifPresent(chip -> parts.add(messages.get(
                        MessageKey.SETTINGS_SUMMARY_SPEED,
                        StageChipRows.durationText(messages, Objects.requireNonNull(chip.elapsed())))));
        final int offered = viewModel.modelListing().offered().size();
        if (offered > 0) {
            parts.add(messages.get(MessageKey.SETTINGS_SUMMARY_MODELS, offered));
        }
        return String.join(" · ", parts);
    }

    private void markBad() {
        final boolean failed =
                viewModel.tests().stages().stream().anyMatch(chip -> chip.status() == StageStatus.FAILED);
        box.getStyleClass().remove("summary-card-bad");
        if (failed) {
            box.getStyleClass().add("summary-card-bad");
        }
    }
}
