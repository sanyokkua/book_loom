package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingProjectRepository;
import ua.bookloom.ui.ScriptedExportService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ExportViewModel;

/**
 * What the Export screen tests share: a window whose settings the test can read and seed, a project store that keeps
 * the last export, the scripted export service, and the steps that open a book and arrive on the screen. The book is
 * the one open project {@code p1}; its counts come from the scripted review desk.
 */
abstract class ExportScreenTestBase extends TranslatingScreenTestBase {

    final MemorySettings stored = new MemorySettings();

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
                .reviewMode(ReviewMode.UNATTENDED)
                .settings(stored)
                .build();
    }

    ScriptedExportService exportService() {
        return (ScriptedExportService) injector.getInstance(ExportService.class);
    }

    RecordingProjectRepository repository() {
        return (RecordingProjectRepository) injector.getInstance(ProjectRepository.class);
    }

    ExportViewModel exports() {
        return injector.getInstance(ExportViewModel.class);
    }

    /** Opens {@code source} as the epub fixture's book with Ukrainian as the target and shows the Export screen. */
    void openAndShowExport(final Path source) throws TimeoutException {
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(source);
        chooseTarget();
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Counts of 142 segments with {@code pending} not yet translated, 50 accepted untouched and 6 reviewed. */
    static ReviewCounts counts(final int pending) {
        return new ReviewCounts(142, 50, 0, 0, 6, pending, 0, 0, 0);
    }

    static ExportReport report(final Path destination, final List<Path> sideFiles, final ConsistencySummary summary) {
        return new ExportReport(destination, 10, 0, 0, 0, 10, 0, sideFiles, 10, summary, 0);
    }
}
