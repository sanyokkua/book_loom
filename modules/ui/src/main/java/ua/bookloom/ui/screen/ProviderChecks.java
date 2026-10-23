package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;

/**
 * The three checks of the open provider with their findings. They run against the provider as it is saved, so they are
 * off while a new provider has not been stored, and they change nothing.
 */
@Slf4j
final class ProviderChecks {

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final StageChipRows chipRows;
    private final FlowPane chips = new FlowPane(8, 8);
    private final VBox box = new VBox(8);
    private final ListChangeListener<StageChip> onStages = change -> showChips();

    ProviderChecks(final SettingsViewModel viewModel, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chipRows = new StageChipRows(messages);
        build();
    }

    VBox node() {
        return box;
    }

    private void build() {
        box.setId("settings-checks");
        final Label title = new Label(messages.get(MessageKey.SETTINGS_CHECKS_TITLE));
        title.getStyleClass().add("card-title");
        chips.setId("settings-chips");
        viewModel.tests().stages().addListener(new WeakListChangeListener<>(onStages));
        showChips();
        box.getChildren()
                .addAll(
                        title,
                        note("settings-checks-hint", MessageKey.SETTINGS_CHECKS_HINT),
                        unsavedNote(),
                        needsModelNote(),
                        refusalLine(),
                        buttons(),
                        chips);
    }

    private Label unsavedNote() {
        final Label unsaved = note("settings-checks-unsaved", MessageKey.SETTINGS_CHECKS_UNSAVED);
        unsaved.visibleProperty().bind(viewModel.adding());
        unsaved.managedProperty().bind(unsaved.visibleProperty());
        return unsaved;
    }

    private Label needsModelNote() {
        final Label needsModel = note("settings-check-needs-model", MessageKey.SETTINGS_CHECK_NEEDS_MODEL);
        needsModel
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> viewModel.draftModel().get().isBlank()
                                && !viewModel.adding().get(),
                        viewModel.draftModel(),
                        viewModel.adding()));
        needsModel.managedProperty().bind(needsModel.visibleProperty());
        return needsModel;
    }

    private HBox buttons() {
        final Label progress = new Label(messages.get(MessageKey.SETTINGS_CHECK_PROGRESS));
        progress.setId("settings-check-progress");
        progress.getStyleClass().add("muted");
        progress.visibleProperty().bind(viewModel.tests().checking());
        progress.managedProperty().bind(progress.visibleProperty());
        return new HBox(
                10,
                testButton(
                        "settings-test-connection",
                        ProviderTest.CONNECTION,
                        MessageKey.SETTINGS_TEST_CONNECTION,
                        MessageKey.SETTINGS_TEST_CONNECTION_TIP),
                testButton(
                        "settings-test-models",
                        ProviderTest.MODELS,
                        MessageKey.SETTINGS_TEST_MODELS,
                        MessageKey.SETTINGS_TEST_MODELS_TIP),
                testButton(
                        "settings-test-inference",
                        ProviderTest.INFERENCE,
                        MessageKey.SETTINGS_TEST_INFERENCE,
                        MessageKey.SETTINGS_TEST_INFERENCE_TIP),
                progress);
    }

    private Label note(final String id, final MessageKey text) {
        final Label label = new Label(messages.get(text));
        label.setId(id);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    // The same line says why the inference test is off while other model work (a run, a scan) is under way.
    private Label refusalLine() {
        final Label refusal = new Label();
        refusal.setId("settings-check-refusal");
        refusal.getStyleClass().add("hint");
        refusal.setWrapText(true);
        refusal.textProperty()
                .bind(Bindings.createStringBinding(
                        this::checkLine,
                        viewModel.tests().checkRefusal(),
                        viewModel.tests().blockedBy()));
        refusal.visibleProperty()
                .bind(Bindings.createBooleanBinding(() -> !refusal.getText().isEmpty(), refusal.textProperty()));
        refusal.managedProperty().bind(refusal.visibleProperty());
        return refusal;
    }

    private String checkLine() {
        final String refusal = viewModel.tests().checkRefusal().get();
        final ActivityKind blocker = viewModel.tests().blockedBy().get();
        return !refusal.isEmpty() || blocker == null
                ? refusal
                : messages.get(MessageKey.ACTIVITY_BLOCKED, messages.get(blocker.label()));
    }

    private Button testButton(final String id, final ProviderTest test, final MessageKey text, final MessageKey tip) {
        final Button button = new Button(messages.get(text));
        button.setId(id);
        button.getStyleClass().add("btn-primary");
        Tips.install(messages, button, tip);
        button.disableProperty().bind(Bindings.not(viewModel.tests().available(test)));
        button.setOnAction(event -> viewModel.tests().run(test));
        return button;
    }

    private void showChips() {
        final List<Node> built = viewModel.tests().stages().stream()
                .map(chipRows::rowOf)
                .map(Node.class::cast)
                .toList();
        log.debug("showing {} stage chip(s)", built.size());
        chips.getChildren().setAll(built);
    }
}
