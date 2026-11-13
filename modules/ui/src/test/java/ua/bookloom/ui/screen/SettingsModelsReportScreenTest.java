package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.ui.ThemeTestSupport;

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

    private static ModelSettings storedWith(
            final String provider,
            final String model,
            final List<RefusedSetting> refused,
            final ProbeVerdicts verdicts) {
        return new ModelSettings(
                provider,
                model,
                TemperatureChoice.CHOOSE,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                refused,
                Map.of(),
                verdicts,
                ThinkingOverride.RECOMMENDED,
                SamplingOverride.profileDefault());
    }

    private static ProbeVerdicts.Verdict verdict(final ProbeVerdicts.Outcome outcome) {
        return new ProbeVerdicts.Verdict(outcome, "advanced features", Instant.parse("2026-10-02T00:00:00Z"));
    }

    private boolean contextFieldDisabled() {
        return ThemeTestSupport.onFx(modelsField("models-context")::isDisabled);
    }

    // IF a refused kind made every kind plain JSON, THEN Translate would look worse than it is.
    @Test
    void structured_gptOss20b_translateSchemaNameReadingPlainJson() throws TimeoutException {
        modelSettings.put(storedWith(
                "ollama",
                GPT_OSS,
                List.of(new RefusedSetting("response_format", LocalDate.of(2026, 10, 2), "plain JSON", "term-reading")),
                new ProbeVerdicts(verdict(ProbeVerdicts.Outcome.HONOURED), null, null, null)));
        openModels();

        chooseModel(GPT_OSS);

        assertThat(shown("models-found-structured")).isEqualTo("Translate: schema\nName reading: plain JSON");
    }

    @Test
    void structured_nothingKnown_notCheckedYetNamesTheTest() throws TimeoutException {
        openModels();

        assertThat(shown("models-found-structured"))
                .isEqualTo("structured answers: not checked yet \u2014 Test inference on the Providers tab checks it");
    }

    @Test
    void structured_ukrainianStoredVerdicts_areWrittenInUkrainian() throws TimeoutException {
        useLocale(Locale.of("uk"));
        modelSettings.put(storedWith(
                "ollama",
                GPT_OSS,
                List.of(new RefusedSetting("response_format", LocalDate.of(2026, 10, 2), "plain JSON", "term-reading")),
                new ProbeVerdicts(verdict(ProbeVerdicts.Outcome.HONOURED), null, null, null)));
        openModels();

        chooseModel(GPT_OSS);

        assertThat(shown("models-found-structured")).isEqualTo("Переклад: схема\nЧитання імен: простий JSON");
    }

    // IF the window stayed editable where LM Studio sets it, THEN a typed number would be ignored by the server.
    @Test
    void context_lmStudio_fieldReadOnlyAndNoDirtyMark() throws TimeoutException {
        capabilities.report("lmstudio", "lapa", 131072, 8192);
        openModels();

        chooseModel("lapa");

        assertThat(contextFieldDisabled()).isTrue();
        assertThat(ThemeTestSupport.onFx(((ToggleSwitch) required("models-context-on"))::isDisabled))
                .isTrue();
        assertThat(modelsField("models-context").getText()).isEqualTo("8192");
        assertThat(shown("models-context-engine")).contains("LM Studio");
        assertThat(modelForm().dirty().get()).isFalse();
    }

    @Test
    void context_ollamaThroughV1_fieldReadOnlyAndNamesOllama() throws TimeoutException {
        addProvider("ollamav1", ServerEngine.OLLAMA, "v1-model");
        capabilities.report("ollamav1", "v1-model", 131072, null);
        openModels();

        chooseModel("v1-model");

        assertThat(contextFieldDisabled()).isTrue();
        assertThat(modelsField("models-context").getText()).isEqualTo("131072");
        assertThat(shown("models-context-engine")).contains("Ollama");
        assertThat(modelForm().dirty().get()).isFalse();
    }

    @Test
    void context_nativeOllama_stillEditable() throws TimeoutException {
        modelSettings.put(with("ollama", GEMMA, TemperatureChoice.CHOOSE, null, 16384, null));

        openModels();

        assertThat(contextFieldDisabled()).isFalse();
        assertThat(visible("models-context-engine")).isFalse();
    }

    // IF the cap were shown as working, THEN a person would trust a limit the server ignored.
    @Test
    void limit_storedCapIgnored_showsNoteUnderAnswerLength() throws TimeoutException {
        modelSettings.put(storedWith(
                "ollama",
                GEMMA,
                List.of(),
                new ProbeVerdicts(null, null, null, verdict(ProbeVerdicts.Outcome.IGNORED))));

        openModels();

        assertThat(visible("models-limit-ignored")).isTrue();
        assertThat(shown("models-limit-ignored"))
                .isEqualTo("The server ignored this limit on the last Advanced features check.");
    }

    @Test
    void limit_nothingStored_noIgnoredNote() throws TimeoutException {
        openModels();

        assertThat(visible("models-limit-ignored")).isFalse();
    }

    @Test
    void advanced_ollamaThroughV1_noFieldChoice() throws TimeoutException {
        addProvider("ollamav1", ServerEngine.OLLAMA, "v1-model");
        openModels();

        chooseModel("v1-model");

        assertThat(visible("models-field-ollama")).isTrue();
        assertThat(visible("models-field-completion")).isFalse();
        assertThat(visible("models-field-tokens")).isFalse();
    }
}
