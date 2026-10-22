package ua.bookloom.ui.state;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingBookFileChooser;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingRowOpener;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.dialog.RecordingFindBookPrompt;
import ua.bookloom.ui.dialog.RecordingRenamePrompt;
import ua.bookloom.ui.dialog.RecordingSamePairPrompt;
import ua.bookloom.ui.dialog.RecordingTranslateIntoPrompt;

/** The scripted service, the recording prompts and the fixtures shared by the row-action tests. */
abstract class ProjectActionsTestBase extends ShellTestBase {

    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");
    static final Path FOUND = Path.of("/moved/earth-gravity.epub");

    static final Project UK = book("p-uk", "uk", NameOrigin.AUTO, "Earth Gravity · EN → UK", ProjectFixtures.BOOK_HASH);
    static final Project DE = book("p-de", "de", NameOrigin.USER, "Земне тяжіння", ProjectFixtures.BOOK_HASH);
    static final Project OTHER = book("p-x", "uk", NameOrigin.USER, "Shared name", "hash-other-book");

    final ScriptedProjectService service = new ScriptedProjectService();
    final RecordingRenamePrompt rename = new RecordingRenamePrompt();
    final RecordingSamePairPrompt samePair = new RecordingSamePairPrompt();
    final RecordingTranslateIntoPrompt translate = new RecordingTranslateIntoPrompt();
    final RecordingFindBookPrompt findBook = new RecordingFindBookPrompt();
    final RecordingBookFileChooser chooser = new RecordingBookFileChooser();
    final RecordingRowOpener opener = new RecordingRowOpener();
    final RecordingErrorPresenter errors = new RecordingErrorPresenter();

    static Project book(
            final String id, final String target, final NameOrigin origin, final String name, final String hash) {
        return ProjectFixtures.project(id, ProjectFixtures.BOOK_FILE, hash, "en", target, origin, name, OPENED);
    }

    static ProjectRow rowOf(final Project project) {
        return ProjectFixtures.row(project, 0, null);
    }

    static ProjectSummary summaryOf(final Project project) {
        return ProjectFixtures.summary(project, 0, null, null);
    }

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(service)
                .renamePrompt(rename)
                .samePairPrompt(samePair)
                .translateIntoPrompt(translate)
                .findBookPrompt(findBook)
                .bookFileChooser(chooser)
                .rowOpener(opener)
                .errorPresenter(errors)
                .build();
    }

    ProjectActions actions() {
        return injector.getInstance(ProjectActions.class);
    }

    void listStored(final Project... projects) {
        service.willList(
                Arrays.stream(projects).map(ProjectActionsTestBase::summaryOf).toList());
    }

    void settle() throws Exception {
        ProjectFixtures.awaitBackgroundIdle(injector);
    }

    void renameRowOfUk() throws Exception {
        onFx(() -> actions().rename(rowOf(UK)));
        settle();
    }
}
