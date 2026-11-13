package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The language advice in the real window over a fake Ollama: a Russian-to-Ukrainian project on a model whose maker
 * lists neither language reads the warning on the ready card and again under the estimate on the Book brief, and Start stays open.
 */
// `slow`: an import and a start in the real window over a database file and a fake server.
@Tag("slow")
class StartLanguageLineTest extends WorkspaceTestBase {

    private static final long LINE_WAIT_SECONDS = 8;
    private static final String READY_ID = "#translating-ready-language-line";
    private static final String START_ID = "#start-language-line";

    // IF the advice were missing, THEN a person would start a long ru-to-uk run on a model that never lists Ukrainian
    // and learn it from the output; the line is advice only, so Start must stay enabled.
    @Test
    void startLines_ruToUkOnLlama31_namesUkrainianNotDeclared() throws Exception {
        try (OllamaWireMock ollama = new OllamaWireMock()) {
            prepareProject(ollama);

            settle(() -> shell.root().lookup(READY_ID) != null
                    && !((Labeled) shell.root().lookup(READY_ID)).getText().isEmpty());
            final Node ready = onFx(() -> shell.root().lookup(READY_ID));
            assertThat(ready).as("the ready card's language line").isNotNull();
            assertThat(onFx(((Labeled) Objects.requireNonNull(ready))::getText))
                    .contains("Llama 3.1 does not list Russian and Ukrainian among its languages");
            final Button start = (Button) onFx(() -> shell.root().lookup("#translating-start"));
            assertThat(onFx(start::isDisabled)).as("Start stays enabled").isFalse();

            onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
            settle(() -> shell.root().lookup(START_ID) != null
                    && !((Labeled) shell.root().lookup(START_ID)).getText().isEmpty());
            final Node line = onFx(() -> shell.root().lookup(START_ID));
            assertThat(line)
                    .as("the Book brief's language line under the estimate")
                    .isNotNull();
            assertThat(onFx(((Labeled) Objects.requireNonNull(line))::getText)).contains("Ukrainian");
            assertThat(runState()).as("nothing starts by itself").isEqualTo(RunState.IDLE);
        }
    }

    private void prepareProject(final OllamaWireMock ollama) throws Exception {
        startStoredWindow();
        assertThat(injector.getInstance(ProviderConfigs.class)
                        .save(ollama.provider())
                        .isOk())
                .isTrue();
        final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);
        onFx(settings::reloadProviders);
        waitUntil(() -> !settings.providers().isEmpty());
        final Path book = Files.writeString(
                booksDir.resolve("Lantern.md"),
                "# Утро\n\nСтарик поставил лампу на стол.\n\nДверь тихо скрипнула.\n\nОн долго смотрел на огонь.\n",
                StandardCharsets.UTF_8);
        onFx(() -> injector.getInstance(ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(CurrentProject.class).book().get() != null);
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        onFx(() -> brief.setSourceLanguage("ru"));
        onFx(() -> brief.setTargetLanguage("uk"));
        waitUntil(() -> !brief.saving().get());
        onFx(() -> settings.adopt(new ModelSelection(OllamaWireMock.PROVIDER_ID, OllamaWireMock.LLAMA)));
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
    }

    // Waits a short while for a screen state; a state that never comes is the assertion's failure, not a timeout's.
    private static void settle(final Supplier<Boolean> condition) {
        try {
            WaitForAsyncUtils.waitFor(LINE_WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::get));
        } catch (Exception _) {
            // The assertion that follows names what was missing.
        }
    }
}
