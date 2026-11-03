package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.event.ActionEvent;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelInput;
import ua.bookloom.api.llm.ModelInputForm;
import ua.bookloom.api.llm.PastedModel;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;

/** What the Add a model id box says about a pasted link: the Understood line, the pull command, and what Enter adds. */
class ProviderPasteLineScreenTest extends SettingsScreenTestBase {

    private static final String URL =
            "https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF/blob/main/GLM-4.7-Flash-UD-Q4_K_XL.gguf";
    private static final String ID = "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL";
    private static final String PULL = "ollama pull " + ID;

    private final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.capabilities(capabilities);
        capabilities.paste(
                URL,
                new PastedModel(
                        new ModelInput(
                                URL,
                                ModelInputForm.HUGGING_FACE_URL,
                                ID,
                                "unsloth/GLM-4.7-Flash-GGUF",
                                "GLM-4.7-Flash-UD-Q4_K_XL.gguf",
                                "q4_k_xl",
                                true),
                        "GLM 4.7 Flash",
                        "UD-Q4_K_XL",
                        PULL));
    }

    private void openBox() throws TimeoutException {
        catalog.respondWith(MODEL);
        openSettingsSettled();
        openFold();
        onFx(() -> ((ToggleSwitch) required("settings-form-models-on")).setSelected(true));
    }

    private boolean shown(final String id) {
        return ThemeTestSupport.onFx(() -> required(id).isVisible());
    }

    // IF the line did not name the repository, the file and the quant, THEN a person could not tell what was read.
    @Test
    void paste_huggingFaceFileUrl_saysWhatWasUnderstoodAndThatTheServerDoesNotListIt() throws Exception {
        openBox();

        setField("settings-form-model-input", URL);

        assertThat(text("settings-form-model-understood"))
                .isEqualTo(
                        "Understood: Hugging Face repo unsloth/GLM-4.7-Flash-GGUF, file GLM-4.7-Flash-UD-Q4_K_XL.gguf"
                                + " → GLM 4.7 Flash · UD-Q4_K_XL Not in this server's list yet.");
    }

    // IF Enter added the raw link, THEN the model list would hold a URL no server knows.
    @Test
    void enter_recognisedPaste_addsTheModelIdNotTheLink() throws Exception {
        openBox();
        setField("settings-form-model-input", URL);

        onFx(() -> field("settings-form-model-input").getOnAction().handle(new ActionEvent()));

        assertThat(ThemeTestSupport.onFx(() -> modelCombo().getItems()))
                .contains(ID)
                .doesNotContain(URL);
    }

    // IF the pull command were missing for an Ollama provider, THEN the person would have to build it by hand.
    @Test
    void paste_ollamaAndUnlisted_offersThePullCommandAsSelectableText() throws Exception {
        openBox();

        setField("settings-form-model-input", URL);

        assertThat(shown("settings-form-model-pull")).isTrue();
        assertThat(field("settings-form-model-pull").getText()).isEqualTo(PULL);
        assertThat(ThemeTestSupport.onFx(() -> field("settings-form-model-pull").isEditable()))
                .isFalse();
    }

    // IF a plain id showed an Understood line, THEN every typed name would carry a sentence nobody asked for.
    @Test
    void typing_plainModelId_showsNoLineAndNoPullCommand() throws Exception {
        openBox();

        setField("settings-form-model-input", "gemma4:e4b");

        assertThat(shown("settings-form-model-understood")).isFalse();
        assertThat(shown("settings-form-model-pull")).isFalse();
    }
}
