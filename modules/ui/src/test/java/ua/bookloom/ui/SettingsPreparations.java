package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.dialog.SwitchProviderDialog;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.ProviderTest;
import ua.bookloom.ui.state.SettingsViewModel;

/** What the Providers form conformance cases do to the window before it is read. */
final class SettingsPreparations {

    private static final long WAIT_SECONDS = 10;

    private final Injector injector;

    SettingsPreparations(final Injector injector) {
        this.injector = injector;
    }

    /** Types into the request timeout, so the footer shows its unsaved mark. */
    void typeIntoTheForm() throws TimeoutException {
        awaitProviders();
        onFx(() -> {
            injector.getInstance(ProviderForm.class).edit(draft -> draft.withRequestSeconds("240"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens the connection details, the way Change model does. */
    void openTheFold() {
        onFx(() -> {
            injector.getInstance(ProviderForm.class).openFoldAt(ProviderForm.Spot.MODEL);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Presses Delete… on the open provider, so the confirm question is on screen. */
    void askToDelete() {
        pressInScene("settings-provider-delete");
    }

    /** Switches to another provider with typing in the form, so the unsaved question is on screen. */
    void askAboutUnsaved() {
        onFx(() -> {
            injector.getInstance(AppShellView.class).activate(ViewNames.PROJECTS);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Runs the inference test against a verifier that answers, and waits for its three findings. */
    void runTheInferenceTest() throws TimeoutException {
        awaitProviders();
        final SettingsViewModel viewModel = injector.getInstance(SettingsViewModel.class);
        ((ScriptedProviderVerifier) injector.getInstance(ProviderVerifier.class))
                .respondWith(ProviderReports.allPassedWithExchanges());
        onFx(() -> {
            viewModel.draftModel().set("gemma3:12b");
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> viewModel.tests().stages().size() == 3
                        && !viewModel.tests().checking().get()));
    }

    /** Opens the test dialog on the inference test. */
    void openTheTestDialog() {
        onFx(() -> {
            injector.getInstance(ProviderTestDialog.class).open(VerificationStage.INFERENCE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Shows the question asked before another provider takes over a started book. */
    void askAboutSwitching() {
        onFx(() -> {
            injector.getInstance(SwitchProviderDialog.class).ask("LM Studio", "Ollama", "gemma4:e4b", () -> {});
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The registry is read in the background; a draft typed before it arrives would be replaced by the stored one.
    private void awaitProviders() throws TimeoutException {
        final SettingsViewModel viewModel = injector.getInstance(SettingsViewModel.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> !viewModel.providers().isEmpty()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void pressInScene(final String id) {
        onFx(() -> {
            final Button button =
                    (Button) injector.getInstance(AppShellView.class).root().lookup("#" + id);
            button.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
