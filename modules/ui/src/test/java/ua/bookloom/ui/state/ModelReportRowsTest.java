package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The verdict line and the window line of Settings › Models, as words, for each engine. */
class ModelReportRowsTest {

    private final ModelReportRows english =
            new ModelReportRows(UiTestInjector.create(Locale.ENGLISH).getInstance(Messages.class));
    private final ModelReportRows ukrainian = new ModelReportRows(
            UiTestInjector.create(Locale.forLanguageTag("uk")).getInstance(Messages.class));

    private static ModelEntry entry(final ProviderKind kind, final ServerEngine engine) {
        return new ModelEntry("p", "P", kind, "m", engine);
    }

    private static ModelFacts facts(
            final @Nullable Integer context, final @Nullable Integer loaded, final @Nullable String verdict) {
        return new ModelFacts(context, loaded, verdict, List.of(), ProbeVerdicts.none(), null, false, null);
    }

    // IF the unknown-model verdict showed the raw constant, THEN the person would read an engine string.
    @Test
    void verdict_unknownModel_saysUnknownModel() {
        assertThat(english.verdict(facts(null, null, ModelReport.UNKNOWN_MODEL)))
                .isEqualTo("BookLoom's verdict: unknown model");
    }

    @Test
    void verdict_fineTune_saysUnknownFineTune() {
        assertThat(english.verdict(facts(null, null, ModelReport.UNKNOWN_FINE_TUNE)))
                .isEqualTo("BookLoom's verdict: unknown fine-tune");
    }

    @Test
    void verdict_recommended_saysRecommended() {
        assertThat(english.verdict(facts(null, null, "recommended"))).isEqualTo("BookLoom's verdict: recommended");
    }

    @Test
    void verdict_otherText_isShownAsItIs() {
        assertThat(english.verdict(facts(null, null, "slow on this computer")))
                .isEqualTo("BookLoom's verdict: slow on this computer");
    }

    // IF the licence warning were in the verdict line, THEN it would show twice: it has its own line (group 9).
    @Test
    void verdict_warnings_joinedExceptTheLicenceNote() {
        final ModelFacts withWarnings = new ModelFacts(
                null,
                null,
                "usable",
                List.of("empty replies", ModelReport.WARNING_LICENCE_NOTE, "slow"),
                ProbeVerdicts.none(),
                null,
                false,
                null);

        assertThat(english.verdict(withWarnings)).isEqualTo("BookLoom's verdict: usable · empty replies · slow");
    }

    @Test
    void verdict_notReportedYet_isEmpty() {
        assertThat(english.verdict(ModelFacts.NONE)).isEmpty();
    }

    // IF /v1 of Ollama showed a loaded figure, THEN the person would think BookLoom sets that window.
    @Test
    void context_ollamaV1_saysSetInOllama() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.OLLAMA), facts(131072, null, null)))
                .isEqualTo("Window: set in Ollama, not by BookLoom");
    }

    @Test
    void context_lmStudioLoaded8192_saysLoadedInLmStudio() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LM_STUDIO), facts(131072, 8192, null)))
                .isEqualTo("loaded in LM Studio: 8,192");
    }

    @Test
    void context_lmStudioNotLoaded_showsTheReadableFigureOnly() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LM_STUDIO), facts(32768, null, null)))
                .isEqualTo("it can read 32,768 tokens at once");
    }

    @Test
    void context_llamaServer16384_saysPerSlot() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LLAMA_CPP), facts(null, 16384, null)))
                .isEqualTo("set at llama-server start: 16,384 per slot");
    }

    @Test
    void context_nativeOllama_keepsTheReadableAndLoadedWords() {
        assertThat(english.context(entry(ProviderKind.OLLAMA, ServerEngine.OLLAMA), facts(131072, 16384, null)))
                .isEqualTo("it can read 131,072 tokens at once (16,384 loaded now)");
    }

    // IF a generic server's loaded figure were shown, THEN it would claim a window nobody reported.
    @Test
    void context_generic_noLoadedFigure() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.GENERIC), facts(4096, 2048, null)))
                .isEqualTo("it can read 4,096 tokens at once");
    }

    @Test
    void context_unknownWindow_saysItDidNotSay() {
        assertThat(english.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.OPENROUTER), facts(null, null, null)))
                .isEqualTo("it did not say how much it can read at once");
    }

    @Test
    void ukrainian_verdictAndContext_areWrittenInUkrainian() {
        assertThat(ukrainian.verdict(facts(null, null, ModelReport.UNKNOWN_MODEL)))
                .startsWith("Оцінка BookLoom: ")
                .doesNotContain("unknown");
        assertThat(ukrainian.context(
                        entry(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LM_STUDIO), facts(131072, 8192, null)))
                .contains("LM Studio")
                .contains("8 192");
    }
}
