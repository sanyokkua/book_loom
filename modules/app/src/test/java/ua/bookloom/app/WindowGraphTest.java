package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.app.bootstrap.ResolvedTraceFile;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The window's graph restarts for real; the command line's does not. */
class WindowGraphTest {

    @TempDir
    private Path dataDir;

    @Test
    void appRestart_windowGraph_isDesktopRestart() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));

        final Injector injector = Guice.createInjector(BookLoomApplication.windowCore(startup));
        final AppRestart restart = injector.getInstance(AppRestart.class);
        final AppRestart again = injector.getInstance(AppRestart.class);
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(restart).isInstanceOf(DesktopAppRestart.class);
        assertThat(again).isSameAs(restart);
    }

    @Test
    void reviewModeChoice_windowGraph_isReviewModeSource() throws Exception {
        final Injector injector = Guice.createInjector(BookLoomApplication.windowCore(startup(null)));

        final ReviewModeChoice choice = injector.getInstance(ReviewModeChoice.class);
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(choice).isSameAs(injector.getInstance(ReviewModeSource.class));
    }

    @Test
    void diagnosticLog_windowGraph_carriesMarkerAndTheVariableThatDecided() throws Exception {
        final Injector injector =
                Guice.createInjector(BookLoomApplication.windowCore(startup(ResolvedTraceFile.Source.ENVIRONMENT)));

        final DiagnosticLog log = injector.getInstance(DiagnosticLog.class);
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(log.marker()).isEqualTo(dataDir.resolve("detailed-log.on"));
        assertThat(log.decidedBy()).isEqualTo("BOOKLOOM_TRACE_FILE");
    }

    private StartupContext startup(final ResolvedTraceFile.@Nullable Source source) throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null),
                false,
                new SessionInfo(),
                new PendingRestart(),
                source == null ? ResolvedTraceFile.Source.DEFAULT : source);
    }
}
