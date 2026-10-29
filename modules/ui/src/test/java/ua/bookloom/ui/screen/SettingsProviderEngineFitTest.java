package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ProviderReports;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ProviderTest;

/**
 * The engine rows and the Advanced features row at the smallest window, in English and Ukrainian: the detected line,
 * Detect again, "Treat this server as…", the Ollama offer with its two buttons and four verdict lines keep every word.
 */
class SettingsProviderEngineFitTest extends SettingsScreenTestBase {

    // IF a button or the line ended in an ellipsis, THEN the person would read half of "Визначити ще раз".
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 944, 600", "uk, 944, 600", "uk, 960, 640", "uk, 1920, 700"})
    void engineRows_offerAndVerdicts_cutNoWord(final String language, final double width, final double height)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        openSettings();
        resizeScene(width, height);
        onFx(() -> viewModel().editProvider("lmstudio"));
        WaitForAsyncUtils.waitForFxEvents();
        setField("settings-form-address", "http://127.0.0.1:11434/v1");
        save();
        openFold();

        final Result<VerificationReport> advanced = ProviderReports.advancedPassed(
                "schema=honoured; thinking=reasoning_effort; samplers=ignored; cap=max_completion_tokens");
        final VerificationReport withOffer = new VerificationReport(
                Objects.requireNonNull(advanced.data(), "report").stages(),
                new EngineDetection(
                        ServerEngine.OLLAMA, "0.40.11", false, 3, Source.DETECTED, Suitability.SUITABLE, true));
        verifier.respondWith(Result.ok(withOffer));
        onFx(() -> viewModel().draftModel().set(MODEL));
        onFx(() -> viewModel().tests().run(ProviderTest.ADVANCED));
        awaitFx(() -> !viewModel().tests().checking().get()
                && viewModel().tests().stages().size() == 4);

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        assertThat(isShown("settings-engine-offer")).isTrue();
        assertThat(isShown("settings-engine-detect")).isTrue();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
    }
}
