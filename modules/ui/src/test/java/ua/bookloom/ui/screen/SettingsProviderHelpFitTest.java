package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The engine help and the privacy lines at the smallest window, in English and Ukrainian: the privacy line naming a
 * remote host, LM Studio's log note and the Unsloth key and tools lines keep every word.
 */
class SettingsProviderHelpFitTest extends SettingsScreenTestBase {

    private void open(final String id, final String address, final ServerEngine engine) throws TimeoutException {
        configs.save(new ProviderConfig(
                        id,
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(address),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60))
                .withEngine(engine));
        onFx(() -> viewModel().reloadProviders());
        awaitFx(() -> viewModel().configOf(id).isPresent());
        onFx(() -> viewModel().editProvider(id));
        WaitForAsyncUtils.waitForFxEvents();
        openFold();
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a help line ended in an ellipsis, THEN the person would lose where the key is made or where the text goes.
    @ParameterizedTest(name = "{0} {3} at {1} x {2}")
    @CsvSource({
        "en, 944, 600, UNSLOTH",
        "uk, 944, 600, UNSLOTH",
        "uk, 944, 600, LM_STUDIO",
        "uk, 960, 640, UNSLOTH",
        "uk, 1920, 700, LM_STUDIO"
    })
    void helpRows_remoteProvider_cutNoWord(
            final String language, final double width, final double height, final ServerEngine engine)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        openSettings();
        resizeScene(width, height);

        open("remote", "https://models.example.com/v1", engine);

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        assertThat(isShown("settings-privacy-line")).isTrue();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
    }
}
