package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The quant chip in the Providers model list and the Understood line under the Add a model id box, in the real window
 * over a fake Ollama that serves facts the way the real server does.
 */
// `slow`: the whole window over a database file and a fake server.
@Tag("slow")
class ModelPickerChipTest extends WorkspaceTestBase {

    private static final String CHIP_ID = "quant-chip";
    private static final long WAIT_SECONDS = 8;
    private static final double NARROW_WIDTH = 960;
    private static final String QWEN_URL =
            "https://huggingface.co/unsloth/Qwen3-8B-GGUF/blob/main/Qwen3-8B-Q4_K_M.gguf";
    private static final String QWEN_PULL = "ollama pull hf.co/unsloth/Qwen3-8B-GGUF:Q4_K_M";

    // IF a bare MLX tag showed a chip before its facts were read, THEN the person would be told a quant nobody read.
    @Test
    void chip_mlxTagBeforeTest_hasNoChipWhileTheNameHoldsNoQuantToken() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock(true)) {
            openSettingsOn(ollama, OllamaWireMock.LLAMA);

            showAllModels();

            // GLM's quant is in its name; llama3.1:8b is the selected model, so its facts were read at once.
            assertThat(onFx(ModelPickerChipTest::chipTexts))
                    .containsExactlyInAnyOrder("UD-Q4_K_XL · good", "Q4_K_M · acceptable");
            assertThat(ollama.showRequestsFor(OllamaWireMock.MLX))
                    .as("no facts read for the unselected tag")
                    .isZero();
        }
    }

    // IF the facts of a selected tag were never read, THEN the MLX tag would keep showing no quant at all.
    @Test
    void chip_afterTest_showsTheQuantTheServerReported() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock(true)) {
            openSettingsOn(ollama, OllamaWireMock.MLX);
            onFx(((Button) Objects.requireNonNull(shell.root().lookup("#settings-summary-test")))::fire);
            settle(() -> ollama.chatRequests() > 0);
            settle(() -> !((Button) shell.root().lookup("#settings-summary-test")).isDisabled());

            showAllModels();

            assertThat(onFx(ModelPickerChipTest::chipTexts)).contains("nvfp4 · acceptable");
        }
    }

    // IF the chip were laid out like text, THEN a narrow window would cut it to "UD-Q4_K_…" and hide the tier.
    @Test
    void chip_narrowWindow_staysWhole() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock(true)) {
            openSettingsOn(ollama, OllamaWireMock.LLAMA);
            onFx(() -> FxToolkit.toolkitContext().getRegisteredStage().setWidth(NARROW_WIDTH));

            showAllModels();

            final List<Boolean> whole = onFx(() -> chips().stream()
                    .map(node -> (Labeled) node)
                    .map(chip -> chip.getWidth() >= chip.prefWidth(-1) - 0.5)
                    .toList());
            assertThat(whole).isNotEmpty().containsOnly(true);
        }
    }

    // IF the pasted link were requested, THEN opening a page would be a network call nobody asked for.
    @Test
    void addModelId_huggingFaceUrl_showsUnderstoodLineAndNoRequest() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock(true)) {
            openSettingsOn(ollama, OllamaWireMock.LLAMA);
            turnOnCustomModels();
            WaitForAsyncUtils.waitForFxEvents();
            final int before = ollama.requests();

            onFx(() -> input().setText(QWEN_URL));
            WaitForAsyncUtils.waitForFxEvents();

            assertThat(onFx(() -> label("settings-form-model-understood").getText()))
                    .startsWith("Understood: Hugging Face repo unsloth/Qwen3-8B-GGUF, file Qwen3-8B-Q4_K_M.gguf")
                    .endsWith("Not in this server's list yet.");
            assertThat(ollama.requests())
                    .as("pasting asked the network nothing")
                    .isEqualTo(before);
        }
    }

    // IF the pull command were run, THEN BookLoom would download a model nobody confirmed; it is only shown.
    @Test
    void addModelId_ollamaAndUnlisted_showsPullCommandText() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock(true)) {
            openSettingsOn(ollama, OllamaWireMock.LLAMA);
            turnOnCustomModels();

            onFx(() -> input().setText(QWEN_URL));
            WaitForAsyncUtils.waitForFxEvents();

            final TextField pull = (TextField) onFx(() -> shell.root().lookup("#settings-form-model-pull"));
            final String shown = onFx(() -> pull.getText());
            final boolean editable = onFx(() -> pull.isEditable());
            assertThat(shown).isEqualTo(QWEN_PULL);
            assertThat(editable).isFalse();
        }
    }

    private void openSettingsOn(final OllamaWireMock ollama, final String model) throws Exception {
        startStoredWindow();
        assertThat(injector.getInstance(ProviderConfigs.class)
                        .save(ollama.provider())
                        .isOk())
                .isTrue();
        final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);
        onFx(settings::reloadProviders);
        waitUntil(() -> !settings.providers().isEmpty());
        onFx(() -> settings.adopt(new ModelSelection(OllamaWireMock.PROVIDER_ID, model)));
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        waitUntil(() -> shell.root().lookup("#settings-summary-test") != null);
        settle(() -> !onFx(() -> settings.modelListing().offered()).isEmpty());
    }

    // The popup lists every model: the entry holds the chosen model's name, which would narrow the list.
    private void showAllModels() {
        final ComboBox<?> combo =
                (ComboBox<?>) Objects.requireNonNull(onFx(() -> shell.root().lookup("#settings-model")));
        onFx(() -> combo.getEditor().setText(""));
        onFx(combo::show);
        settle(() -> combo.getItems().size() >= 2 && chips().size() >= 2);
    }

    private void turnOnCustomModels() {
        // A model name already in the draft is what turns the "Use custom models" switch on.
        onFx(() -> injector.getInstance(ProviderForm.class)
                .edit(draft -> draft.withCustomModels(List.of("office-model"))));
    }

    private TextField input() {
        return (TextField) shell.root().lookup("#settings-form-model-input");
    }

    private Labeled label(final String id) {
        return (Labeled) shell.root().lookup("#" + id);
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
    private static void settle(final Supplier<Boolean> condition) {
        try {
            WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::get));
        } catch (Exception _) {
            // The assertion that follows names what was missing.
        }
    }
}
