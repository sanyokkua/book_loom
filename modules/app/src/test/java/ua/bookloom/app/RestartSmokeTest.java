package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** A stored project survives a restart in the window's graph, and the command line's graph writes no database. */
class RestartSmokeTest {

    @TempDir
    private Path dataDir;

    @Test
    void windowGraph_savedProject_isListedAfterASecondBoot() throws Exception {
        final StartupContext startup = startup();

        final Injector first = windowInjector(startup);
        assertThat(first.getInstance(ProjectRepository.class).save(project()).isOk())
                .isTrue();
        first.getInstance(SqliteDatabase.class).close();

        final Injector second = windowInjector(startup);
        final List<Project> stored =
                second.getInstance(ProjectRepository.class).all().data();
        second.getInstance(SqliteDatabase.class).close();

        assertThat(stored).extracting(Project::id).containsExactly("p1");
        assertThat(startup.paths().databaseFile()).exists();
    }

    @Test
    void commandLineGraph_savedProject_writesNoDatabaseFile() throws Exception {
        final StartupContext startup = startup();

        final Injector injector = Guice.createInjector(new CoreModules(startup, new InMemoryPersistenceModule()));
        new AppLifecycle().phaseOne(injector);
        injector.getInstance(ProjectRepository.class).save(project());

        assertThat(startup.paths().databaseFile()).doesNotExist();
    }

    private static Injector windowInjector(final StartupContext startup) {
        final Injector injector =
                Guice.createInjector(new CoreModules(startup, BookLoomApplication.windowPersistence(startup)));
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        return injector;
    }

    private StartupContext startup() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
    }

    private static Project project() {
        return new Project(
                "p1",
                Path.of("book.epub"),
                BookFormat.EPUB,
                "hash-1",
                BookBrief.defaults("en").withLanguages("en", "uk"),
                "Book",
                NameOrigin.USER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
    }
}
