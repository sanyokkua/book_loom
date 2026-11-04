package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.AdviceReports;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * Building and showing the Book brief and the Translating screen for a project whose model was stored at an earlier
 * launch asks the provider nothing: the advice lines come from the name and the facts the session already holds.
 */
class AdviceNoNetworkOnBuildTest extends BookBriefScreenTestBase {

    private static final String PROVIDER = "ollama";
    private static final String MODEL = "llama3.1:8b";

    private final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        final MemorySettings settings = new MemorySettings();
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection(PROVIDER, MODEL));
        capabilities.advise(
                PROVIDER,
                MODEL,
                AdviceReports.report(
                        ModelReport.Match.FAMILY,
                        null,
                        AdviceReports.identity("Llama 3.1", 8.0, "Q4_K_M"),
                        AdviceReports.notes("Llama 3.1", "Llama 3.1"),
                        null,
                        List.of()),
                AdviceReports.advice(LanguageState.NOT_DECLARED, LanguageState.NOT_DECLARED, false, "Llama 3.1"));
        return UiTestInjector.builder(locale)
                .projects(projects)
                .prompt(prompt)
                .errorPresenter(errors)
                .settings(settings)
                .capabilities(capabilities)
                .build();
    }

    // IF a screen read the model's facts when it was built, THEN reopening the last project would call the provider
    // at every launch, with nobody having asked for it (offline-and-privacy: provider calls are user-triggered).
    @Test
    void buildScreens_storedModelAndOpenProject_asksTheProviderNothing() throws Exception {
        projects.on(FRANKENSTEIN, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(FRANKENSTEIN);
        onFx(() -> {
            injector.getInstance(BookBriefViewModel.class).setSourceLanguage("ru");
            injector.getInstance(BookBriefViewModel.class).setTargetLanguage("uk");
        });
        awaitFx(() ->
                injector.getInstance(SettingsViewModel.class).model().get().equals(MODEL));
        showBrief();
        awaitShown("start-language-line");
        final String briefLine = text("start-language-line");
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        awaitShown("translating-ready-language-line");

        assertThat(briefLine)
                .as("the line is there, from what is held offline")
                .contains("Llama 3.1 does not list Russian and Ukrainian");
        assertThat(capabilities.asked()).as("no report asked of the provider").isEmpty();
    }

    private String text(final String id) {
        return ThemeTestSupport.onFx(((Labeled) required(id))::getText);
    }

    private void awaitShown(final String id) throws Exception {
        awaitFx(() -> {
            final Node node = scene.getRoot().lookup("#" + id);
            return node != null && node.isVisible() && node.isManaged();
        });
    }
}
