package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The navigation column's completed-step marks and its footer, driven the way the application drives them: a book
 * opened through the import view model, a run published through the state mirror, a model typed in settings.
 */
class AppShellNavigationTest extends ShellTestBase {

    private static final Path BOOK = Path.of("Frankenstein.epub");
    private static final long WAIT_SECONDS = 10;

    private final ScriptedProjectService projects = new ScriptedProjectService();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale).projects(projects).build();
    }

    private void openBook() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        onFx(() -> viewModel.open(BOOK));
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(() -> !viewModel.opening().get()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<String> doneEntryIds() {
        return scene.getRoot().lookupAll(".nav-step-done").stream()
                .map(Node::getId)
                .toList();
    }

    private String footerText() {
        return ((Label) required("nav-footer")).getText();
    }

    @Test
    void navigation_underUkrainian_showsEachHeadingUpperCasedInTheUkrainianLocale() {
        // IF the heading were not upper-cased in the display locale, THEN the capitals would use English rules.
        useLocale(Locale.forLanguageTag("uk"));

        assertThat(scene.getRoot().lookupAll(".nav-label"))
                .extracting(heading -> ((Label) heading).getText())
                .containsExactly("РОБОЧИЙ ПРОЦЕС", "ЗАСТОСУНОК");
    }

    @Test
    void navigation_noBookOpen_marksNoStepDone() {
        // IF a step looked done before anything happened, THEN the marks would not say where the person is.
        assertThat(doneEntryIds()).isEmpty();
    }

    @Test
    void navigation_bookOpened_marksOnlyTheImportDone() throws TimeoutException {
        // IF opening a book marked more than the import, THEN the brief would look finished before it was read.
        openBook();

        assertThat(doneEntryIds()).containsExactly("nav-import");
        assertThat(((Label) required("nav-import").lookup(".nav-step")).getText())
                .isEmpty();
    }

    @Test
    void navigation_briefStructureAndRunStarted_marksThoseStepsButNotTranslatingOrExport() throws TimeoutException {
        // IF translating were marked while the run is only started, THEN a running book would look finished.
        openBook();
        final WorkflowProgress progress = injector.getInstance(WorkflowProgress.class);
        onFx(() -> {
            progress.markDone(ViewNames.BOOK_BRIEF);
            progress.markDone(ViewNames.STRUCTURE);
        });
        injector.getInstance(StateMirror.class).publishRunStarted("Frankenstein.epub", null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(doneEntryIds()).containsExactly("nav-import", "nav-book-brief", "nav-structure", "nav-names-style");
    }

    @Test
    void navigation_runCompleted_marksTranslatingDone() throws TimeoutException {
        // IF completion did not reach the column, THEN a finished run would look unfinished.
        openBook();
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishRunState(RunState.COMPLETED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(doneEntryIds()).contains("nav-translating").doesNotContain("nav-export");
    }

    @Test
    void navigation_footerWithAModelChosen_namesTheProviderAndFollowsTheModel() {
        // IF the footer were not bound, THEN a person would have to open Settings to see which model a run would use.
        final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);

        onFx(() -> settings.model().set("gemma4:26b"));
        assertThat(footerText()).isEqualTo("Ollama · gemma4:26b");

        onFx(() -> settings.model().set("qwen3:8b"));
        assertThat(footerText()).isEqualTo("Ollama · qwen3:8b");
    }

    @Test
    void navigation_footerWithNoModel_saysSo() {
        // IF an empty model were joined to the provider, THEN the footer would read "Ollama · ".
        assertThat(footerText()).isEqualTo("No model chosen");
    }

    @Test
    void navigation_exportMarkedDone_marksTheExportStep() throws TimeoutException {
        // IF the export could not be marked done, THEN a written book would leave its step unmarked once the interim
        // derivation goes.
        openBook();
        final WorkflowProgress progress = injector.getInstance(WorkflowProgress.class);

        onFx(() -> progress.markDone(ViewNames.EXPORT));

        assertThat(doneEntryIds()).contains("nav-export");
    }
}
