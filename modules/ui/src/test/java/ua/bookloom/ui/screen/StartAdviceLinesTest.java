package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.ui.AdviceReports;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.Tone;

/** The language, quant, licence and thinking lines under the Book brief's estimate, scripted at the port. */
class StartAdviceLinesTest extends BookBriefScreenTestBase {

    private static final String PROVIDER = "ollama";

    private final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .prompt(prompt)
                .errorPresenter(errors)
                .capabilities(capabilities)
                .build();
    }

    private void startOn(final String model, final String source, final String target) throws TimeoutException {
        projects.on(FRANKENSTEIN, ua.bookloom.api.Result.ok(ua.bookloom.ui.BookFixtures.frankensteinImport()));
        openImport();
        openBook(FRANKENSTEIN);
        onFx(() -> {
            injector.getInstance(BookBriefViewModel.class).setSourceLanguage(source);
            injector.getInstance(BookBriefViewModel.class).setTargetLanguage(target);
            injector.getInstance(SettingsViewModel.class).reloadProviders();
        });
        awaitFx(() -> !injector.getInstance(SettingsViewModel.class).providers().isEmpty());
        onFx(() -> injector.getInstance(SettingsViewModel.class).adopt(new ModelSelection(PROVIDER, model)));
        showBrief();
    }

    private String line(final String id) {
        final Labeled label = (Labeled) required(id);
        return ThemeTestSupport.onFx(label::getText);
    }

    private boolean shown(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final var node = scene.getRoot().lookup("#" + id);
            return node != null && node.isVisible() && node.isManaged();
        });
    }

    private void awaitShown(final String id) throws TimeoutException {
        awaitFx(() -> {
            final var node = scene.getRoot().lookup("#" + id);
            return node != null && node.isVisible() && node.isManaged();
        });
    }

    private void script(final String model, final ModelReport report, final LanguageAdvice advice) {
        capabilities.advise(PROVIDER, model, report, advice);
    }

    private static ModelReport plain(final String family, final double size, final @Nullable QuantTier tier) {
        return AdviceReports.report(
                ModelReport.Match.FAMILY,
                null,
                AdviceReports.identity(family, size, "Q4_K_M"),
                AdviceReports.notes(family, family),
                tier,
                List.of());
    }

    // IF only the missing side were named, THEN a person could not tell which language the model leaves out.
    @Test
    void start_ruToUkOnQwen25_15b_warnsUkrainianOnly() throws Exception {
        script(
                "qwen2.5:1.5b",
                plain("Qwen 2.5", 1.5, null),
                AdviceReports.advice(LanguageState.DECLARED, LanguageState.NOT_DECLARED, false, "Qwen 2.5"));

        startOn("qwen2.5:1.5b", "ru", "uk");
        awaitShown("start-language-line");

        assertThat(line("start-language-line"))
                .isEqualTo("Qwen 2.5 does not list Ukrainian among its languages. Translation may have problems.");
        assertThat(ThemeTestSupport.onFx(() -> required("start-language-line").getStyleClass()))
                .contains(Tone.WARN.statusClass());
    }

    @Test
    void start_bothSidesMissing_namesBothLanguages() throws Exception {
        script(
                "llama3.1:8b",
                plain("Llama 3.1", 8.0, null),
                AdviceReports.advice(LanguageState.NOT_DECLARED, LanguageState.NOT_DECLARED, false, "Llama 3.1"));

        startOn("llama3.1:8b", "ru", "uk");
        awaitShown("start-language-line");

        assertThat(line("start-language-line"))
                .isEqualTo("Llama 3.1 does not list Russian and Ukrainian among its languages."
                        + " Translation may have problems.");
    }

    @Test
    void start_enToUkOnGemma4E4b_noLanguageLine() throws Exception {
        script(
                "gemma4:e4b",
                plain("Gemma 4", 4.0, null),
                AdviceReports.advice(LanguageState.UNKNOWN, LanguageState.UNKNOWN, true, "Gemma 4"));

        startOn("gemma4:e4b", "en", "uk");
        awaitFx(() ->
                injector.getInstance(SettingsViewModel.class).model().get().equals("gemma4:e4b"));
        awaitShown("brief-quality-hint");

        assertThat(line("start-language-line")).isEmpty();
        assertThat(shown("start-language-line")).isFalse();
    }

    // The data says nothing of this pair for e4b, so the line is the quiet information line, not a warning.
    @Test
    void start_ruToUkOnGemma4E4b_showsInfoLineNotWarning() throws Exception {
        script(
                "gemma4:e4b",
                plain("Gemma 4", 4.0, null),
                AdviceReports.advice(LanguageState.UNKNOWN, LanguageState.UNKNOWN, false, "Gemma 4"));

        startOn("gemma4:e4b", "ru", "uk");
        awaitShown("start-language-line");

        assertThat(line("start-language-line"))
                .isEqualTo("The maker of Gemma 4 does not say which languages it supports."
                        + " Check a few paragraphs first.");
        assertThat(ThemeTestSupport.onFx(() -> required("start-language-line").getStyleClass()))
                .contains(Tone.INFO.statusClass())
                .doesNotContain(Tone.WARN.statusClass());
    }

    @Test
    void start_q4KmOnMidModel_noQuantLine() throws Exception {
        script("mid:12b", plain("Mid", 12.0, QuantTier.ACCEPTABLE), LanguageAdvice.NEUTRAL);

        startOn("mid:12b", "en", "uk");
        awaitShown("brief-quality-hint");
        awaitFx(() ->
                !injector.getInstance(SettingsViewModel.class).model().get().isBlank());

        assertThat(shown("start-quant-line")).isFalse();
    }

    @Test
    void start_q4KmOnSmallModel_showsQuantLineNamingTarget() throws Exception {
        script("llama3.2:3b", plain("Llama 3.2", 3.0, QuantTier.ACCEPTABLE), LanguageAdvice.NEUTRAL);

        startOn("llama3.2:3b", "ru", "uk");
        awaitShown("start-quant-line");

        assertThat(line("start-quant-line"))
                .isEqualTo("Slightly reduced precision. Usually fine, but names and word endings may drift,"
                        + " especially for Ukrainian; a larger version translates more reliably.");
    }

    @Test
    void start_licenceRow_showsNoteAndStartEnabled() throws Exception {
        script(
                "thomson:small",
                AdviceReports.report(
                        ModelReport.Match.EXACT,
                        null,
                        AdviceReports.identity("thomson", null, null),
                        AdviceReports.notes("Thomson 1.0 Small", "Thomson 1.0 Small"),
                        null,
                        List.of(ModelReport.WARNING_LICENCE_NOTE)),
                LanguageAdvice.NEUTRAL);

        startOn("thomson:small", "en", "uk");
        awaitShown("start-licence-line");

        assertThat(line("start-licence-line"))
                .isEqualTo("Thomson 1.0 Small is licensed for noncommercial use only (PolyForm Strict 1.0.0).");
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        assertThat(ThemeTestSupport.onFx(((Button) required("translating-start"))::isDisabled))
                .as("Start stays enabled")
                .isFalse();
    }

    @Test
    void start_hfGgufWithoutRenderer_showsTheThinkingHint() throws Exception {
        script(
                "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M",
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("qwen", 4.0, "Q4_K_M"),
                        new ModelNotes("Qwen3.5 4B", "Qwen3.5", "qwen3.5:4b", null, false),
                        null,
                        List.of()),
                LanguageAdvice.NEUTRAL);

        startOn("hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", "en", "uk");
        awaitShown("start-thinking-line");

        assertThat(line("start-thinking-line"))
                .isEqualTo("This download cannot turn thinking off in Ollama, so it is slower."
                        + " The library model qwen3.5:4b or a Modelfile with RENDERER and PARSER fixes it.");
    }
}
