package ua.bookloom.ui;

import com.google.inject.Injector;
import com.google.inject.Key;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.RunRecord;

/** Stored projects, runs and exports a Projects test lists; every title and file name is invented. */
public final class ProjectFixtures {

    /** The book most fixtures describe, with a source file of this name. */
    public static final String BOOK_FILE = "earth-gravity.epub";

    /** Its content hash; projects with the same hash are translations of one book. */
    public static final String BOOK_HASH = "0123456789abcdef0123456789abcdef";

    /** The segments its stored projects hold. */
    public static final int BOOK_SEGMENTS = 1240;

    private ProjectFixtures() {}

    /** The source path a project of {@code fileName} has; its id is the one {@link ScriptedProjectService} opens by. */
    public static Path source(final String fileName) {
        return Path.of("/books", fileName);
    }

    /** The id {@link ScriptedProjectService} opens the project of {@code fileName} by. */
    public static String idOf(final String fileName) {
        return ScriptedProjectService.storedProjectOf(source(fileName)).id();
    }

    /** A stored project of the invented book, translated {@code source} to {@code target}, last opened at {@code opened}. */
    public static Project project(
            final String id,
            final String fileName,
            final String hash,
            final String sourceLanguage,
            final String targetLanguage,
            final NameOrigin origin,
            final String name,
            final Instant opened) {
        return new Project(
                id,
                source(fileName).toAbsolutePath().normalize(),
                BookFormat.EPUB,
                hash,
                BookBrief.defaults(sourceLanguage).withLanguages(sourceLanguage, targetLanguage),
                name,
                origin,
                "Earth Gravity",
                Instant.parse("2026-10-01T10:00:00Z"),
                opened);
    }

    /** A summary of {@link #BOOK_SEGMENTS} segments with the given decisions, latest run and last export. */
    public static ProjectSummary summary(
            final Project project,
            final int decided,
            final @Nullable RunRecord run,
            final @Nullable ExportRecord export) {
        return new ProjectSummary(project, decided, BOOK_SEGMENTS, run, export);
    }

    /** A run of {@code state} by Ollama's {@code gemma4:e4b}, ended when its state is terminal. */
    public static RunRecord run(final String projectId, final JobState state) {
        final boolean ended = state == JobState.COMPLETED || state == JobState.CANCELLED || state == JobState.FAILED;
        return new RunRecord(
                "run-" + projectId,
                projectId,
                Instant.parse("2026-10-21T10:00:00Z"),
                ended ? Instant.parse("2026-10-21T12:00:00Z") : null,
                state,
                10,
                1,
                new ModelSelection("ollama", "gemma4:e4b"));
    }

    /** An export written at {@code at}. */
    public static ExportRecord export(final Instant at) {
        return new ExportRecord(
                at, Path.of("/out/earth-gravity-uk.epub"), 1200, 0, 40, 0, 1100, 100, 1200, java.util.List.of());
    }

    /** What opening a stored project answers: a book with English to Ukrainian chosen, so every step opens. */
    public static ImportedBook openedBook(final String projectId) {
        final ImportedBook base =
                BookFixtures.imported(projectId, BookFormat.EPUB, "en", "Earth Gravity", "A. Writer", 2, 3);
        return new ImportedBook(
                base.projectId(),
                base.inspection(),
                base.profile(),
                java.util.Objects.requireNonNull(base.brief()).withLanguages("en", "uk"));
    }

    /**
     * Waits until the injected background executor has nothing queued or running, with the FX thread drained on both
     * sides, so a test that asserts "nothing was opened" has seen every hop a launch or a click makes.
     */
    public static void awaitBackgroundIdle(final Injector injector) throws TimeoutException {
        final ThreadPoolExecutor pool =
                (ThreadPoolExecutor) injector.getInstance(Key.get(ExecutorService.class, BackgroundExecutor.class));
        for (int round = 0; round < 3; round++) {
            WaitForAsyncUtils.waitForFxEvents();
            WaitForAsyncUtils.waitFor(
                    10,
                    TimeUnit.SECONDS,
                    () -> pool.getActiveCount() == 0 && pool.getQueue().isEmpty());
        }
        WaitForAsyncUtils.waitForFxEvents();
    }
}
