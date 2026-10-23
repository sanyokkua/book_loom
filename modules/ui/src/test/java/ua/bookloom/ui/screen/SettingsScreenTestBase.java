package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;

/** What the settings screen tests share: the recording verifier, the view model, and reading and driving a check. */
abstract class SettingsScreenTestBase extends ShellTestBase {

    static final long WAIT_SECONDS = 10;
    static final String MODEL = "gemma3:12b";

    final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.idle();

    /** Lists no models until a test scripts it; script it before {@link #openSettings()}, which asks it. */
    final ScriptedModelCatalog catalog = ScriptedModelCatalog.idle();

    /** The registry behind the window; counts the saves that reached it and the calls made on the FX thread. */
    final FakeProviderConfigs configs = new FakeProviderConfigs();

    @Override
    protected final Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .verifier(verifier)
                .catalog(catalog)
                .configs(configs)
                .environment(Set.of("OFFICE_KEY"))
                .build();
    }

    void openSettings() {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
    }

    @SuppressWarnings("unchecked")
    ComboBox<String> modelCombo() {
        return (ComboBox<String>) required("settings-model");
    }

    Label listingLabel() {
        return (Label) required("settings-model-listing");
    }

    Label refusalLabel() {
        return (Label) required("settings-model-refusal");
    }

    ProviderForm form() {
        return injector.getInstance(ProviderForm.class);
    }

    Button button(final String id) {
        return (Button) required(id);
    }

    TextField field(final String id) {
        return (TextField) required(id);
    }

    TitledPane fold() {
        return (TitledPane) required("settings-connection");
    }

    String text(final String id) {
        return ThemeTestSupport.onFx(() -> ((Labeled) required(id)).getText());
    }

    /** Types into a form field the way the control's own listener sees it: the text changes on the FX thread. */
    void setField(final String id, final String text) {
        onFx(() -> field(id).setText(text));
    }

    /** Presses Save and waits until storage has answered and the list has been read again. */
    void save() throws TimeoutException {
        onFx(() -> button("settings-form-save").fire());
        awaitFx(() -> !form().saving().get());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens the connection details, so the controls in them can be focused and typed into. */
    void openFold() {
        onFx(() -> form().openFoldAt(ProviderForm.Spot.MODEL));
    }

    /** Types {@code text} into the model entry with real key events, the way a person would, without committing it. */
    void typeIntoModelEntry(final String text) {
        openFold();
        interact(() -> modelCombo().getEditor().requestFocus());
        write(text);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Presses Enter in the model entry, the way a person commits what they typed. */
    void commitModelEntry() {
        interact(() -> modelCombo().getEditor().requestFocus());
        type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
    }

    Label noListLabel() {
        return (Label) required("settings-model-nolist");
    }

    /** Waits, polling on the FX thread, until {@code condition} holds. */
    static void awaitFx(final BooleanSupplier condition) throws TimeoutException {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(condition::getAsBoolean));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens settings and waits until the listing it started has been answered, so a later step cannot race it. */
    void openSettingsSettled() throws TimeoutException {
        openSettings();
        awaitFx(() -> !viewModel().modelListing().listing().get());
    }

    SettingsViewModel viewModel() {
        return injector.getInstance(SettingsViewModel.class);
    }

    TabPane tabs() {
        return (TabPane) required("settings-tabs");
    }

    Pane chips() {
        return (Pane) required("settings-chips");
    }

    Labeled chip(final String stage) {
        return (Labeled) required("chip-" + stage);
    }

    static AppError discoveryFailed() {
        return AppError.of(ErrorCode.discoveryFailed, "Discovery failed", "The model list could not be read.");
    }

    Label detail(final String stage) {
        return (Label) required("chip-detail-" + stage);
    }

    static Result<VerificationReport> firstStageOnly(final StageStatus status) {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.");
        final StageOutcome outcome =
                switch (status) {
                    case FAILED -> new StageOutcome(VerificationStage.CONNECTION, status, unreachable, null);
                    case SOFT_PASS ->
                        new StageOutcome(
                                VerificationStage.CONNECTION, status, discoveryFailed(), "model list unavailable");
                    case PASSED, SKIPPED -> new StageOutcome(VerificationStage.CONNECTION, status, null, null);
                };
        return Result.ok(new VerificationReport(List.of(outcome)));
    }

    /** Chooses a model and fires the check, then waits until the three chips are on screen. */
    void checkWithReport(final Result<VerificationReport> report) throws TimeoutException {
        verifier.respondWith(report);
        onFx(() -> {
            viewModel().draftModel().set(MODEL);
            viewModel().tests().run(ProviderTest.INFERENCE);
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> chips().getChildren().size()) == 3);
        onFx(() -> {});
    }
}
