package ua.bookloom.ui.screen;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
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
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.BlockedWords;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StageChip;
import ua.bookloom.ui.state.StageStart;
import ua.bookloom.ui.state.TestDetails;
import ua.bookloom.ui.state.TestLive;
import ua.bookloom.ui.state.TestProgress;
import ua.bookloom.ui.state.TestSubject;

/**
 * The checks of the open provider as a grid: a button (two for the model tests, the second opening the test card at
 * once), the sentence the last run found and a Details link for each. They run against the values in the form, saved or not, and change nothing; they are off while the form holds
 * something the rules refuse.
 */
@Slf4j
final class ProviderChecks {

    private static final double GRID_GAP = 10;
    private static final double BUTTON_GAP = 4;

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
        addRow(3, ProviderTest.ADVANCED, "settings-test-advanced", MessageKey.SETTINGS_TEST_ADVANCED);
        redrawOnFindings();
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

    private void redrawOnFindings() {
        final TestProgress progress = viewModel.tests().progress();
        for (final Observable source : List.<Observable>of(
                viewModel.tests().stages(),
                viewModel.tests().running(),
                viewModel.testedSubject(),
                progress.started(),
                progress.finished(),
                progress.sent())) {
            source.addListener(new WeakInvalidationListener(onFindings));
        }
    }

    private void addRow(final int row, final ProviderTest test, final String buttonId, final MessageKey text) {
        final VerificationStage stage = test.lastStage();
        final Label result = Wrapped.wholeWords(new Label());
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
        // Like the button: a link's computed minimum is its ellipsis, so a failure's reason would squeeze it to "Det…".
        details.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, details, MessageKey.SETTINGS_CHECK_DETAILS_TIP);
        details.setOnAction(event -> open(stage));
        grid.add(buttons(buttonId, test, text), 0, row);
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
        Visibility.bind(progress, viewModel.tests().checking());
        return progress;
    }

    private Label blockedNote() {
        final Label blocked = note("settings-checks-blocked", MessageKey.SETTINGS_CHECKS_BLOCKED);
        Visibility.bind(blocked, viewModel.testsBlocked());
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
        return Wrapped.wholeLines(label);
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
        Visibility.bind(
                refusal, Bindings.createBooleanBinding(() -> !refusal.getText().isEmpty(), refusal.textProperty()));
        return refusal;
    }

    private String checkLine() {
        final String refusal = viewModel.tests().checkRefusal().get();
        final ActivityKind blocker = viewModel.tests().blockedBy().get();
        return !refusal.isEmpty() || blocker == null
                ? refusal
                : BlockedWords.in(messages, blocker, ActivityKind.PROVIDER_INFERENCE_TEST);
    }

    // The model tests have two ways in: the quiet one leaves its line beside the button, "Test with details" opens the
    // card at once, so the request shows while the model works.
    private Node buttons(final String id, final ProviderTest test, final MessageKey text) {
        final Button quiet = testButton(id, test, text);
        if (!test.needsModel()) {
            return quiet;
        }
        final Button detailed = new Button(messages.get(MessageKey.SETTINGS_TEST_WITH_DETAILS));
        detailed.setId(id + "-details");
        detailed.getStyleClass().add("btn-secondary");
        detailed.setMinWidth(Region.USE_PREF_SIZE);
        detailed.setMaxWidth(Double.MAX_VALUE);
        Tips.install(messages, detailed, MessageKey.SETTINGS_TEST_WITH_DETAILS_TIP);
        detailed.disableProperty().bind(Bindings.not(viewModel.tests().available(test)));
        detailed.setOnAction(event -> {
            log.debug("{} with details pressed", test);
            if (viewModel.tests().run(test)) {
                dialog.open(test.lastStage());
            }
        });
        return new VBox(BUTTON_GAP, quiet, detailed);
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
            case ADVANCED -> MessageKey.SETTINGS_TEST_ADVANCED_TIP;
        };
    }

    // The optional stage says before it runs that it is optional and uses the model.
    private static MessageKey notRunOf(final VerificationStage stage) {
        return stage == VerificationStage.ADVANCED
                ? MessageKey.SETTINGS_TEST_ADVANCED_TITLE
                : MessageKey.SETTINGS_CHECK_NOT_RUN;
    }

    private TestDetails.Subject tested() {
        final Optional<TestSubject> subject = viewModel.testedSubject().get();
        return new TestDetails.Subject(
                viewModel.testedName(),
                subject.map(TestSubject::isLocalProgram).orElse(false),
                subject.map(tested -> !tested.model().isBlank()).orElse(false));
    }

    // While a test runs each stage shows its own state: an ended one its result, the first unended one the live step
    // line (what it waits for, the model's load included), the rest what they were before.
    private void showResults() {
        final ProviderTest running = viewModel.tests().running().get();
        final TestProgress progress = viewModel.tests().progress();
        final VerificationStage current = running == null ? null : currentStage(running, progress);
        for (final Map.Entry<VerificationStage, Label> entry : results.entrySet()) {
            final VerificationStage stage = entry.getKey();
            final VBox cell = Objects.requireNonNull(cells.get(stage));
            final boolean inRun = running != null && stage.compareTo(running.lastStage()) <= 0;
            final Optional<StageChip> found = (inRun
                            ? progress.finished()
                            : viewModel.tests().stages())
                    .stream().filter(chip -> chip.stage() == stage).findFirst();
            if (found.isPresent()) {
                chipRows.fill(entry.getValue(), found.get(), tested(), cell);
            } else if (inRun && stage == current) {
                chipRows.plain(entry.getValue(), liveLine(stage, running, progress), cell);
            } else {
                chipRows.plain(entry.getValue(), messages.get(notRunOf(stage)), cell);
            }
        }
        log.debug("check results redrawn, running {}", running);
    }

    // The stage that has begun and not ended; before any begins, the last one the test covers, which is the one the
    // test is named for.
    private static VerificationStage currentStage(final ProviderTest running, final TestProgress progress) {
        return progress.started().stream()
                .map(StageStart::stage)
                .filter(stage -> progress.finished().stream().noneMatch(chip -> chip.stage() == stage))
                .reduce((first, second) -> second)
                .orElse(running.lastStage());
    }

    private String liveLine(final VerificationStage stage, final ProviderTest running, final TestProgress progress) {
        final TestLive live = new TestLive(
                progress.sent(),
                progress.started(),
                progress.finished(),
                TestDetails.stepOf(running.lastStage()),
                progress.now());
        return TestDetails.of(messages, stage, List.of(), true, tested(), live).verdict();
    }
}
