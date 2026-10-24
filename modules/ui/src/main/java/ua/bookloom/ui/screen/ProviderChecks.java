package ua.bookloom.ui.screen;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;

/**
 * The three checks of the open provider as a grid: a button, the sentence the last run found and a Details link for
 * each. They run against the values in the form, saved or not, and change nothing; they are off while the form holds
 * something the rules refuse.
 */
@Slf4j
final class ProviderChecks {

    private static final double GRID_GAP = 10;

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final ProviderTestDialog dialog;
    private final StageChipRows chipRows;
    private final GridPane grid = new GridPane(GRID_GAP, GRID_GAP);
    private final Map<VerificationStage, Label> results = new EnumMap<>(VerificationStage.class);
    private final Map<VerificationStage, VBox> cells = new EnumMap<>(VerificationStage.class);
    private final VBox box = new VBox(8);
    private final InvalidationListener onFindings = observed -> showResults();

    ProviderChecks(final SettingsViewModel viewModel, final Messages messages, final ProviderTestDialog dialog) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
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
        grid.setId("settings-checks-grid");
        grid.getStyleClass().add("checks-grid");
        final ColumnConstraints wide = new ColumnConstraints();
        wide.setHgrow(Priority.ALWAYS);
        // The sentence beside the button wraps and gives way; the button and the link keep their own width.
        wide.setMinWidth(0);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), wide, new ColumnConstraints());
        addRow(0, ProviderTest.CONNECTION, "settings-test-connection", MessageKey.SETTINGS_TEST_CONNECTION);
        addRow(1, ProviderTest.MODELS, "settings-test-models", MessageKey.SETTINGS_TEST_MODELS);
        addRow(2, ProviderTest.INFERENCE, "settings-test-inference", MessageKey.SETTINGS_TEST_INFERENCE);
        viewModel.tests().stages().addListener(new WeakInvalidationListener(onFindings));
        viewModel.tests().running().addListener(new WeakInvalidationListener(onFindings));
        viewModel.testedSubject().addListener(new WeakInvalidationListener(onFindings));
        showResults();
        box.getChildren()
                .addAll(
                        title,
                        note("settings-checks-hint", MessageKey.SETTINGS_CHECKS_HINT),
                        blockedNote(),
                        needsModelNote(),
                        refusalLine(),
                        grid,
                        progress());
    }

    private void addRow(final int row, final ProviderTest test, final String buttonId, final MessageKey text) {
        final VerificationStage stage = test.lastStage();
        final Label result = new Label();
        result.setId("check-result-" + stage.name());
        result.setFocusTraversable(true);
        Tips.install(messages, result, MessageKey.SETTINGS_CHECK_DETAILS_TIP);
        result.setOnMouseClicked(event -> open(stage));
        result.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                event.consume();
                open(stage);
            }
        });
        final VBox cell = new VBox(4, result);
        results.put(stage, result);
        cells.put(stage, cell);
        final Hyperlink details = new Hyperlink(messages.get(MessageKey.SETTINGS_CHECK_DETAILS));
        details.setId("check-details-" + stage.name());
        Tips.install(messages, details, MessageKey.SETTINGS_CHECK_DETAILS_TIP);
        details.setOnAction(event -> open(stage));
        grid.add(testButton(buttonId, test, text), 0, row);
        grid.add(cell, 1, row);
        grid.add(details, 2, row);
    }

    private void open(final VerificationStage stage) {
        log.debug("details of the {} result asked for", stage);
        dialog.open(stage);
    }

    private Label progress() {
        final Label progress = new Label(messages.get(MessageKey.SETTINGS_CHECK_PROGRESS));
        progress.setId("settings-check-progress");
        progress.getStyleClass().add("muted");
        progress.visibleProperty().bind(viewModel.tests().checking());
        progress.managedProperty().bind(progress.visibleProperty());
        return progress;
    }

    private Label blockedNote() {
        final Label blocked = note("settings-checks-blocked", MessageKey.SETTINGS_CHECKS_BLOCKED);
        blocked.visibleProperty().bind(viewModel.testsBlocked());
        blocked.managedProperty().bind(blocked.visibleProperty());
        return blocked;
    }

    private Label needsModelNote() {
        final Label needsModel = note("settings-check-needs-model", MessageKey.SETTINGS_CHECK_NEEDS_MODEL);
        needsModel
                .visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> viewModel.draftModel().get().isBlank(), viewModel.draftModel()));
        needsModel.managedProperty().bind(needsModel.visibleProperty());
        return needsModel;
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

    private Button testButton(final String id, final ProviderTest test, final MessageKey text) {
        final Button button = new Button(messages.get(text));
        button.setId(id);
        button.getStyleClass().add("btn-primary");
        // A button's computed minimum is its ellipsis, so a long result sentence beside it would squeeze it to "Test
        // co…".
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Double.MAX_VALUE);
        Tips.install(messages, button, tipOf(test));
        button.disableProperty().bind(Bindings.not(viewModel.tests().available(test)));
        button.setOnAction(event -> viewModel.tests().run(test));
        return button;
    }

    private static MessageKey tipOf(final ProviderTest test) {
        return switch (test) {
            case CONNECTION -> MessageKey.SETTINGS_TEST_CONNECTION_TIP;
            case MODELS -> MessageKey.SETTINGS_TEST_MODELS_TIP;
            case INFERENCE -> MessageKey.SETTINGS_TEST_INFERENCE_TIP;
        };
    }

    private void showResults() {
        final ProviderTest running = viewModel.tests().running().get();
        for (final VerificationStage stage : VerificationStage.values()) {
            final Label result = Objects.requireNonNull(results.get(stage));
            final VBox cell = Objects.requireNonNull(cells.get(stage));
            final Optional<StageChip> found = viewModel.tests().stages().stream()
                    .filter(chip -> chip.stage() == stage)
                    .findFirst();
            if (running != null && stage.compareTo(running.lastStage()) <= 0) {
                chipRows.plain(result, messages.get(MessageKey.SETTINGS_TEST_VERDICT_RUNNING), cell);
            } else if (found.isPresent()) {
                chipRows.fill(result, found.get(), viewModel.testedName(), cell);
            } else {
                chipRows.plain(result, messages.get(MessageKey.SETTINGS_CHECK_NOT_RUN), cell);
            }
        }
        log.debug("check results redrawn, running {}", running);
    }
}
