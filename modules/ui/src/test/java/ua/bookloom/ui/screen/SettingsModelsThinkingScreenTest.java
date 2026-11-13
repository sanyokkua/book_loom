package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.RadioButton;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The Thinking row of Settings › Models (design D20): it says what thinking control the chosen model offers, and only a
 * model with a switch offers the three choices, which are stored on that model alone.
 */
class SettingsModelsThinkingScreenTest extends SettingsModelsScreenTestBase {

    private static final ReasoningControl SWITCH =
            new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of("true", "false"), "false", "true");
    private static final ReasoningControl HARMONY =
            new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of("low", "medium", "high"), "low", "medium");
    private static final ReasoningControl REASONING_ONLY =
            new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of("on"), null, "on");

    @Test
    void modelsTab_thinkingRow_switchModelSaysOnAndOffWithRecommendedSelected() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, SWITCH);

        openModels();

        assertThat(shown("models-thinking-offers")).isEqualTo("This model can turn thinking on and off.");
        assertThat(shown("models-thinking-sends"))
                .isEqualTo("BookLoom sends thinking off for draft, repair, proofread, names and brief calls.");
        assertThat(visible("models-thinking-choices")).isTrue();
        assertThat(radio("models-thinking-recommended").isSelected()).isTrue();
        assertThat(radio("models-thinking-recommended").getText()).isEqualTo("Recommended (off)");
        assertThat(radio("models-thinking-allow").getText()).isEqualTo("Allow on proofreading and names");
        assertThat(radio("models-thinking-always").getText()).isEqualTo("Always");
    }

    // The choice is stored through the model's own settings, so it applies from the next request to that model.
    @Test
    void modelsTab_thinkingRow_allowChosenAndSaved_isStoredOnTheModel() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, SWITCH);
        openModels();

        onFx(() -> radio("models-thinking-allow").fire());
        press("models-form-save");

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> modelSettings.stored("ollama", GEMMA).isPresent());
        assertThat(modelSettings.stored("ollama", GEMMA).orElseThrow().thinkingOverride())
                .isEqualTo(ThinkingOverride.ALLOW_PROOFREAD_AND_NAMES);
    }

    @Test
    void modelsTab_thinkingRow_anotherModelKeepsItsOwnChoice() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, SWITCH);
        capabilities.thinking("ollama", GPT_OSS, SWITCH);
        modelSettings.put(always(GPT_OSS));
        openModels();

        chooseModel(GPT_OSS);
        assertThat(radio("models-thinking-always").isSelected()).isTrue();
        chooseModel(GEMMA);

        assertThat(radio("models-thinking-recommended").isSelected()).isTrue();
    }

    @Test
    void modelsTab_thinkingRow_noThinkingControl_saysSoAndOffersNoChoice() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, ReasoningControl.NONE);

        openModels();

        assertThat(shown("models-thinking-offers"))
                .isEqualTo("This model has no thinking control, so BookLoom sends none.");
        assertThat(visible("models-thinking-choices")).isFalse();
        assertThat(visible("models-thinking-sends")).isFalse();
    }

    @Test
    void modelsTab_thinkingRow_alwaysThinksAtALevel_namesTheLowestLevelAndOffersNoChoice() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, HARMONY);

        openModels();

        assertThat(shown("models-thinking-offers")).isEqualTo("Always thinks; BookLoom sends the lowest level, low.");
        assertThat(visible("models-thinking-choices")).isFalse();
    }

    @Test
    void modelsTab_thinkingRow_alwaysThinksWithNoLevel_saysItCannotBeChanged() throws TimeoutException {
        capabilities.thinking("ollama", GEMMA, REASONING_ONLY);

        openModels();

        assertThat(shown("models-thinking-offers")).isEqualTo("Always thinks; this cannot be changed.");
        assertThat(visible("models-thinking-choices")).isFalse();
    }

    private RadioButton radio(final String id) {
        return ThemeTestSupport.onFx(() -> (RadioButton) required(id));
    }

    private static ModelSettings always(final String model) {
        return with("ollama", model, TemperatureChoice.CHOOSE, null, null, null)
                .withOverrides(ThinkingOverride.ALWAYS, SamplingOverride.profileDefault());
    }
}
