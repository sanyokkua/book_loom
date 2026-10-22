package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javafx.scene.control.Label;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingRowOpener;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.dialog.RecordingRenamePrompt;
import ua.bookloom.ui.dialog.RecordingSamePairPrompt;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.RunState;

/**
 * What the Book Brief tests that depend on a run share: the translating graph (a recording job a real Start reaches),
 * the recording rename and same-pair questions, the stored speed of the model, and a stored project whose id is the
 * open book's, so the brief's project line, estimate and pair guard have a project to read.
 */
abstract class BriefRunScreenTestBase extends TranslatingScreenTestBase {

    static final String PROJECT_ID = "p1";
    static final String AUTOMATIC_NAME = "Frankenstein · EN → UK";
    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");

    final RecordingSamePairPrompt samePair = new RecordingSamePairPrompt();
    final RecordingRenamePrompt rename = new RecordingRenamePrompt();
    final RecordingRowOpener opener = new RecordingRowOpener();
    final ScriptedModelSettings modelSettings = new ScriptedModelSettings();

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
                .samePairPrompt(samePair)
                .renamePrompt(rename)
                .rowOpener(opener)
                .modelSettings(modelSettings)
                .build();
    }

    /** The stored project of the open book, as the service lists it. */
    static ProjectSummary stored(final String target, final NameOrigin origin, final String name) {
        final Project project = ProjectFixtures.project(
                PROJECT_ID, "Frankenstein.epub", ProjectFixtures.BOOK_HASH, "en", target, origin, name, OPENED);
        return ProjectFixtures.summary(project, 0, null, null);
    }

    /** The stored project of another translation of the same book, which holds the pair a second one may not take. */
    static ProjectSummary otherTranslation(final String target) {
        final Project project = ProjectFixtures.project(
                "p2", "Frankenstein.epub", ProjectFixtures.BOOK_HASH, "en", target, NameOrigin.AUTO, "Other", OPENED);
        return ProjectFixtures.summary(project, 0, null, null);
    }

    /** Opens the book with the automatic name stored, a model chosen and Ukrainian as the target; shows the brief. */
    void openAndShowBrief() throws TimeoutException {
        projects.willList(List.of(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME)));
        readyToStart();
        showBrief();
    }

    /** Shows the brief afresh, the way a person arrives from another screen. */
    void showBrief() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Starts a real run through the Start button, so the recording job is what Pause and Stop reach. */
    void startRunThenShowBrief() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME)));
        readyToStart();
        showTranslating();
        onFx(() -> button("translating-start").fire());
        job.awaitRunStarted();
        awaitFx(() -> labelText("shell-run-state").startsWith("0 % done"));
        showBrief();
    }

    /** Publishes a run of {@code state} with {@code decided} of 100 segments decided, then lets the screen follow. */
    void publishRun(final RunState state, final int decided) {
        mirror().publishRunStarted("Frankenstein.epub", null);
        mirror().publishProgress(ua.bookloom.ui.ProgressFixtures.progress(7, 11, decided, 0, 100 - decided));
        mirror().publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Reads a value on the FX thread. */
    <T> T read(final Supplier<T> value) {
        return ThemeTestSupport.onFx(value);
    }

    BookBrief brief() {
        return read(() -> injector.getInstance(CurrentProject.class).brief().get());
    }

    @SuppressWarnings("unchecked")
    SearchableCombo<String> box(final String id) {
        return (SearchableCombo<String>) required(id);
    }

    BookBriefViewModel briefViewModel() {
        return injector.getInstance(BookBriefViewModel.class);
    }

    String textOfLabel(final String id) {
        return ((Label) required(id)).getText();
    }
}
