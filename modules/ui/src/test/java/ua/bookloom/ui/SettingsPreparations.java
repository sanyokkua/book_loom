package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.state.ProviderForm;
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
