package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.stage.Window;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The quant chip beside a model in the Providers model list, in the real window over a fake Ollama: the server reports
 * the base quant of a GGUF, the chip names the quant the model's own name carries and judges it.
 */
// `slow`: the whole window over a database file and a fake server.
@Tag("slow")
class ProvidersQuantChipTest extends WorkspaceTestBase {

    private static final String CHIP_ID = "quant-chip";
    private static final long CHIP_WAIT_SECONDS = 8;

    // IF the chip were taken from the server's base quant (Q4_K_M) instead of the name's UD-Q4_K_XL, THEN a dynamic
    // quant would be judged as a plain one and a person would pick a model by the wrong token.
    @Test
    void providersModelList_ggufReportsBaseQuant_showsNameTokenAndGoodChip() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock()) {
            startStoredWindow();
            assertThat(injector.getInstance(ProviderConfigs.class)
                            .save(ollama.provider())
                            .isOk())
                    .isTrue();
            final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);
            onFx(settings::reloadProviders);
            waitUntil(() -> !settings.providers().isEmpty());
            onFx(() -> settings.adopt(new ModelSelection(OllamaWireMock.PROVIDER_ID, OllamaWireMock.GLM)));
            onFx(() -> shell.activate(ViewNames.SETTINGS));
            waitUntil(() -> shell.root().lookup("#settings-summary-test") != null);

            onFx(((Button) Objects.requireNonNull(shell.root().lookup("#settings-summary-test")))::fire);
            settle(() -> ollama.chatRequests() > 0);
            settle(() -> !((Button) shell.root().lookup("#settings-summary-test")).isDisabled());
            onFx(() -> ((ComboBox<?>) Objects.requireNonNull(shell.root().lookup("#settings-model"))).show());
            settle(() -> !chips().isEmpty());

            assertThat(ollama.requests()).as("the Test reached the fake server").isPositive();
            assertThat(onFx(ProvidersQuantChipTest::chipTexts)).containsExactly("UD-Q4_K_XL · good");
        }
    }

    private static List<String> chipTexts() {
        return chips().stream().map(node -> ((Labeled) node).getText()).toList();
    }

    private static List<Node> chips() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .flatMap(window -> window.getScene().getRoot().lookupAll("#" + CHIP_ID).stream())
                .toList();
    }

    // Waits a short while for a screen state; a state that never comes is the assertion's failure, not a timeout's.
    private static void settle(final java.util.function.Supplier<Boolean> condition) {
        try {
            WaitForAsyncUtils.waitFor(CHIP_WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::get));
        } catch (Exception _) {
            // The assertion that follows names what was missing.
        }
    }
}
