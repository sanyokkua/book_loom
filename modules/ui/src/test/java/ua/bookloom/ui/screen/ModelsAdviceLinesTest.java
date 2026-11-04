package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.ui.AdviceReports;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBookFixture;

/** The identity, quant, languages, licence, thinking and date lines on Settings › Models, scripted at the port. */
class ModelsAdviceLinesTest extends SettingsModelsScreenTestBase {

    private static final String PROVIDER = "ollama";

    private void openFor(final String source, final String target) throws TimeoutException {
        onFx(() -> OpenedBookFixture.open(injector.getInstance(CurrentProject.class), source, target));
        openModels();
    }

    private void awaitLine(final String id) throws TimeoutException {
        awaitFx(() -> visible(id));
    }

    // IF the line did not name the base, THEN a person could take a fine-tune's advice for the fine-tune's own rating.
    @Test
    void identityLine_fineTune_namesBase() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.BASE_FAMILY,
                        "Llama 3.1",
                        AdviceReports.identity("hermes", 70.0, "Q4_K_M"),
                        AdviceReports.notes("Hermes 4 70B", "Hermes 4"),
                        null,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-identity-line");

        assertThat(shown("models-identity-line"))
                .isEqualTo("Understood: Hermes 4 70B · Q4_K_M · recognised as a fine-tune of Llama 3.1"
                        + " (behaviour of Llama 3.1)");
    }

    @Test
    void identityLine_serverQuantDiffers_namesBoth() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.EXACT,
                        null,
                        AdviceReports.identity("glm", null, "UD-Q4_K_XL"),
                        new ModelNotes("GLM 4.7 Flash", "GLM 4.7", null, "Q4_K_M", false),
                        null,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-identity-line");

        assertThat(shown("models-identity-line"))
                .isEqualTo("Understood: GLM 4.7 Flash · UD-Q4_K_XL (server reports Q4_K_M) · exact match");
    }

    // IF the target were not named, THEN the warning about names and endings would not say which language suffers.
    @Test
    void quantLine_acceptableAtSmall_namesTargetLanguage() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("phi-4-mini", 3.8, "Q4_K_M"),
                        AdviceReports.notes("Phi 4 Mini", "Phi 4 Mini"),
                        QuantTier.ACCEPTABLE,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-quant-line");

        assertThat(shown("models-quant-line"))
                .isEqualTo("Slightly reduced precision. Usually fine, but names and word endings may drift,"
                        + " especially for Ukrainian; a larger version translates more reliably.");
    }

    @Test
    void languagesLine_bookLoomTestedPair_addsTestedNote() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("gemma", 4.0, "Q4_K_M"),
                        AdviceReports.notes("Gemma 4 E4B", "Gemma 4"),
                        null,
                        List.of()),
                AdviceReports.advice(LanguageState.UNKNOWN, LanguageState.UNKNOWN, true, "Gemma 4"));

        openFor("en", "uk");
        awaitLine("models-languages-line");

        assertThat(shown("models-languages-line"))
                .isEqualTo("Declared languages: not stated by the maker · BookLoom tested it for English → Ukrainian");
    }

    @Test
    void languagesLine_pairNotTested_hasNoTestedPart() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("gemma", 4.0, "Q4_K_M"),
                        AdviceReports.notes("Gemma 4 E4B", "Gemma 4"),
                        null,
                        List.of()),
                AdviceReports.advice(LanguageState.UNKNOWN, LanguageState.UNKNOWN, false, "Gemma 4"));

        openFor("ru", "uk");
        awaitLine("models-languages-line");

        assertThat(shown("models-languages-line")).isEqualTo("Declared languages: not stated by the maker");
    }

    @Test
    void factsDate_shown() throws Exception {
        capabilities.factsDate("2026-10-28");
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("gemma", 4.0, null),
                        AdviceReports.notes("Gemma 4 E4B", "Gemma 4"),
                        null,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-facts-date");

        assertThat(shown("models-facts-date")).isEqualTo("Model facts as of 2026-10-28");
    }

    @Test
    void thinkingHint_hfGgufWithoutRenderer_namesTheLibraryTag() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("qwen", 4.0, "Q4_K_M"),
                        new ModelNotes("Qwen3.5 4B", "Qwen3.5", "qwen3.5:4b", null, false),
                        null,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-thinking-hint");

        assertThat(shown("models-thinking-hint"))
                .isEqualTo("This download cannot turn thinking off in Ollama, so it is slower."
                        + " The library model qwen3.5:4b or a Modelfile with RENDERER and PARSER fixes it.");
    }

    @Test
    void licenceAndHint_modelWithNeither_showsNoLine() throws Exception {
        capabilities.advise(
                PROVIDER,
                GEMMA,
                AdviceReports.report(
                        ModelReport.Match.EXACT,
                        null,
                        AdviceReports.identity("gemma", 4.0, "Q4_K_M"),
                        AdviceReports.notes("Gemma 4 E4B", "Gemma 4"),
                        QuantTier.EXCELLENT,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        openFor("en", "uk");
        awaitLine("models-identity-line");

        assertThat(visible("models-licence-line")).isFalse();
        assertThat(visible("models-thinking-hint")).isFalse();
        assertThat(visible("models-quant-line")).isFalse();
    }
}
