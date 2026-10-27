package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.dialog.RecordingOpenAnotherBookPrompt;

/**
 * What the import guard tests share: the real runner, mirror and import view model, a scripted project service that
 * lists the open project under its display name, and a recording "Open another book?" question.
 */
abstract class ImportGuardTestBase extends RunnerTestBase {

    static final Path FRANKENSTEIN = Path.of("Frankenstein.epub");
    static final Path DRACULA = Path.of("Dracula.epub");
    static final String OPEN_NAME = "Earth Gravity · EN → UK";
    /** The SHA-256 of the three bytes {@code abc}, so a file holding them has a known content hash. */
    static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path dir;

    ScriptedProjectService projects;
    RecordingOpenAnotherBookPrompt prompt;
    ImportViewModel imports;
    ImportGuard guard;
    RecordingToasts toasts;

    @BeforeEach
    void setUpGuard() {
        projects = new ScriptedProjectService();
        projects.on(FRANKENSTEIN, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(
                DRACULA, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Dracula", "Bram Stoker", 5)));
        projects.willList(List.of(summary("p1", OPEN_NAME, "other-hash")));
        prompt = new RecordingOpenAnotherBookPrompt();
        final CurrentProject current = new CurrentProject();
        toasts = new RecordingToasts();
        imports = onFx(() -> new ImportViewModel(
                projects,
                current,
                toasts,
                new RecordingErrorPresenter(),
                new DirectExecutor(),
                new ActivityTracker(new StateMirror())));
        guard = new ImportGuard(mirror, runner, imports, current, prompt, projects, new DirectExecutor(), toasts);
    }

    /** A stored project of {@code name} whose book content has {@code hash}. */
    static ProjectSummary summary(final String id, final String name, final String hash) {
        return new ProjectSummary(
                new Project(
                        id,
                        Path.of("/books/" + id + ".epub"),
                        BookFormat.EPUB,
                        hash,
                        BookBrief.defaults("en"),
                        name,
                        NameOrigin.AUTO,
                        "Earth Gravity",
                        Instant.EPOCH,
                        Instant.EPOCH),
                3,
                9,
                null,
                null);
    }

    /** Opens {@code Frankenstein.epub} as project {@code p1}, so a project is open when the guard is asked. */
    void openFirstBook() {
        onFx(() -> {
            imports.open(FRANKENSTEIN);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Writes a file holding the bytes {@code abc} and returns its path. */
    Path fileWithKnownContent(final String name) throws IOException {
        return Files.write(dir.resolve(name), new byte[] {'a', 'b', 'c'});
    }

    void requestImport(final Path source) {
        onFx(() -> {
            guard.requestImport(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void openNew() {
        onFx(() -> {
            prompt.openNew();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void replace() {
        onFx(() -> {
            prompt.replace();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    ImportState shown() {
        return onFx(() -> imports.state().get());
    }
}
