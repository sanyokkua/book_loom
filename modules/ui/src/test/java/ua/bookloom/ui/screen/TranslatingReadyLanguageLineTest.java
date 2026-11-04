package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.AdviceReports;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.Tone;

/** The ready card's language line under the model row: the same advice as before Start, and never a block. */
class TranslatingReadyLanguageLineTest extends TranslatingScreenTestBase {

    private static final String LLAMA = "llama3.1:8b";

    private final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .models(models)
                .engine(engine)
                .glossary(glossary)
                .lexicon(lexicon)
                .namesPass(namesPass)
                .reviewDesk(desk)
                .capabilities(capabilities)
                .build();
    }

    // IF the ready card said nothing, THEN a person would press Start on a model that never lists the language.
    @Test
    void readyCard_modelWithoutTheTargetLanguage_warnsAndStartStaysEnabled() throws Exception {
        capabilities.advise(
                "ollama",
                LLAMA,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("llama", 8.0, "Q4_K_M"),
                        AdviceReports.notes("Llama 3.1 8B", "Llama 3.1"),
                        null,
                        List.of()),
                AdviceReports.advice(LanguageState.DECLARED, LanguageState.NOT_DECLARED, false, "Llama 3.1"));
        readyToStart();
        onFx(() -> injector.getInstance(SettingsViewModel.class).reloadProviders());
        awaitFx(() -> !injector.getInstance(SettingsViewModel.class).providers().isEmpty());
        onFx(() -> injector.getInstance(SettingsViewModel.class).adopt(new ModelSelection("ollama", LLAMA)));

        showTranslating();
        awaitFx(() -> optional("translating-ready-language-line") != null
                && !((Labeled) optional("translating-ready-language-line"))
                        .getText()
                        .isEmpty());

        final Labeled line = (Labeled) required("translating-ready-language-line");
        assertThat(ThemeTestSupport.onFx(line::getText))
                .isEqualTo("Llama 3.1 does not list Ukrainian among its languages. Translation may have problems.");
        assertThat(ThemeTestSupport.onFx(line::getStyleClass)).contains(Tone.WARN.statusClass());
        assertThat(ThemeTestSupport.onFx(((Button) required("translating-start"))::isDisabled))
                .isFalse();
    }
}
