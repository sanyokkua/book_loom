package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ServerEngine;

/** The verdict line and the window line of the Models tab, as the window shows them for each engine. */
class SettingsModelsReportScreenTest extends SettingsModelsScreenTestBase {

    private static ModelReport report(
            final @Nullable Integer context,
            final @Nullable Integer loaded,
            final String verdict,
            final List<String> warnings) {
        return new ModelReport(
                context,
                loaded,
                null,
                ModelReport.Match.DEFAULT,
                verdict,
                warnings,
                ReasoningControl.UNKNOWN,
                null,
                null,
                null,
                null);
    }

    private void addProvider(final String id, final ServerEngine engine, final String model) {
        configs.save(new ProviderConfig(
                        id,
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("http://localhost:9/v1"),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT)
                .withEngine(engine));
        catalog.respondFor(id, model);
    }

    // IF "unknown model" were left out, THEN the person could not tell a rated model from one nobody rated.
    @Test
    void verdict_unknownModel_isShownInTheVerdictLine() throws TimeoutException {
        capabilities.advise(
                "ollama", GEMMA, report(null, null, ModelReport.UNKNOWN_MODEL, List.of()), LanguageAdvice.NEUTRAL);

        openModels();

        assertThat(shown("models-verdict")).isEqualTo("BookLoom's verdict: unknown model");
    }

    @Test
    void verdict_fineTune_isShownAsUnknownFineTune() throws TimeoutException {
        capabilities.advise(
                "ollama", GEMMA, report(null, null, ModelReport.UNKNOWN_FINE_TUNE, List.of()), LanguageAdvice.NEUTRAL);

        openModels();

        assertThat(shown("models-verdict")).isEqualTo("BookLoom's verdict: unknown fine-tune");
    }

    // IF the licence note joined the verdict, THEN the same warning would show twice.
    @Test
    void verdict_licenceNoteAndAnotherWarning_onlyTheOtherIsInTheLine() throws TimeoutException {
        capabilities.advise(
                "ollama",
                GEMMA,
                report(null, null, "usable", List.of(ModelReport.WARNING_LICENCE_NOTE, "empty replies")),
                LanguageAdvice.NEUTRAL);

        openModels();

        assertThat(shown("models-verdict")).isEqualTo("BookLoom's verdict: usable · empty replies");
    }

    @Test
    void verdict_ukrainian_isWrittenInUkrainian() throws TimeoutException {
        useLocale(Locale.of("uk"));
        capabilities.advise(
                "ollama", GEMMA, report(null, null, ModelReport.UNKNOWN_MODEL, List.of()), LanguageAdvice.NEUTRAL);

        openModels();

        assertThat(shown("models-verdict")).isEqualTo("Оцінка BookLoom: невідома модель");
    }

    // IF /v1 of Ollama showed a window as BookLoom's, THEN a person would type one that Ollama ignores.
    @Test
    void context_ollamaThroughV1_saysSetInOllama() throws TimeoutException {
        addProvider("ollamav1", ServerEngine.OLLAMA, "v1-model");
        capabilities.advise("ollamav1", "v1-model", report(131072, null, "usable", List.of()), LanguageAdvice.NEUTRAL);
        openModels();

        chooseModel("v1-model");

        assertThat(shown("models-found-text")).contains("Window: set in Ollama, not by BookLoom");
    }

    @Test
    void context_lmStudioLoaded8192_saysLoadedInLmStudio() throws TimeoutException {
        capabilities.report("lmstudio", "lapa", 131072, 8192);
        openModels();

        chooseModel("lapa");

        assertThat(shown("models-found-text")).contains("loaded in LM Studio: 8,192");
    }

    @Test
    void context_llamaServer16384_saysPerSlot() throws TimeoutException {
        addProvider("llama", ServerEngine.LLAMA_CPP, "llama-model");
        capabilities.report("llama", "llama-model", 32768, 16384);
        openModels();

        chooseModel("llama-model");

        assertThat(shown("models-found-text")).contains("set at llama-server start: 16,384 per slot");
    }

    // IF a generic server's loaded figure were shown, THEN it would claim a window nobody reported.
    @Test
    void context_genericServer_showsTheReadableFigureAndNoLoadedOne() throws TimeoutException {
        addProvider("generic", ServerEngine.GENERIC, "gen-model");
        capabilities.report("generic", "gen-model", 4096, 2048);
        openModels();

        chooseModel("gen-model");

        assertThat(shown("models-found-text"))
                .contains("4,096 tokens")
                .doesNotContain("loaded")
                .doesNotContain("2,048");
    }
}
