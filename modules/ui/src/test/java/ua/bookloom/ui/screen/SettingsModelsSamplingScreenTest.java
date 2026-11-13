package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The Sampling row of Settings › Models (design D20): it names what BookLoom sends, offers only Profile default and
 * Custom, stores Custom's values on the model, and a temperature typed there is the tab's My value.
 */
class SettingsModelsSamplingScreenTest extends SettingsModelsScreenTestBase {

    @Test
    void modelsTab_samplingRow_offersProfileDefaultAndCustomOnly() throws TimeoutException {
        openModels();

        assertThat(radio("models-sampling-profile").getText()).isEqualTo("Profile default");
        assertThat(radio("models-sampling-custom").getText()).isEqualTo("Custom");
        assertThat(radio("models-sampling-profile").isSelected()).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> lookup("#models-sampling-choices .radio-button")
                        .queryAll()
                        .size()))
                .isEqualTo(2);
        assertThat(shown("models-sampling-sends"))
                .isEqualTo("BookLoom sends its own temperature for each kind of call and no other sampler of its own;"
                        + " a server whose defaults are not neutral is sent neutral values instead.");
        assertThat(visible("models-sampling-fields")).isFalse();
    }

    @Test
    void modelsTab_samplingCustomTyped_namesWhatEveryRequestCarries() throws TimeoutException {
        openModels();

        onFx(() -> radio("models-sampling-custom").fire());
        type("models-sampling-top-k", "40");
        type("models-sampling-top-p", "0.9");

        assertThat(visible("models-sampling-fields")).isTrue();
        assertThat(shown("models-sampling-sends"))
                .isEqualTo("Every request to this model carries top_k 40, top_p 0.9; the rest stays as the profile"
                        + " sets it.");
    }

    @Test
    void modelsTab_samplingCustomSaved_isStoredOnTheModelAndTheTemperatureIsMyValue() throws TimeoutException {
        openModels();

        onFx(() -> radio("models-sampling-custom").fire());
        type("models-sampling-temperature", "0.5");
        type("models-sampling-top-k", "20");
        press("models-form-save");

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> modelSettings.stored("ollama", GEMMA).isPresent());
        final ModelSettings stored = modelSettings.stored("ollama", GEMMA).orElseThrow();
        assertThat(stored.samplingOverride()).isEqualTo(SamplingOverride.custom(0.5, 20, null, null));
        assertThat(stored.temperatureChoice()).isEqualTo(TemperatureChoice.VALUE);
        assertThat(stored.temperature()).isEqualTo(0.5);
    }

    @Test
    void modelsTab_samplingCustomTopPOutOfRange_holdsSave() throws TimeoutException {
        openModels();

        onFx(() -> radio("models-sampling-custom").fire());
        type("models-sampling-top-p", "1.5");
        press("models-form-save");

        assertThat(shown("models-errors-text")).isEqualTo("Type a top_p above 0 and at most 1.");
        assertThat(modelSettings.stored("ollama", GEMMA)).isEmpty();
    }

    private void type(final String id, final String text) {
        onFx(() -> ((TextField) required(id)).setText(text));
    }

    private RadioButton radio(final String id) {
        return ThemeTestSupport.onFx(() -> (RadioButton) required(id));
    }
}
