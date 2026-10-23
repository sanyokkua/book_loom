package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.app.bootstrap.ResolvedTraceFile;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.bootstrap.TraceFileResolver;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.state.DetailedLogSwitch;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The Settings switch writes the marker the next launch's resolver reads. */
class DetailedLogMarkerTest {

    private static final long WAIT_SECONDS = 10;

    @TempDir
    private Path dataDir;

    private Injector launch() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.PROD,
                ReviewModeResolver.resolve(name -> null, name -> null));
        final Injector injector = Guice.createInjector(BookLoomApplication.windowCore(startup), new UiModule());
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        try {
            final CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit is shared by the test JVM; a running one is what is wanted.
        }
        return injector;
    }

    private static void waitFor(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(20);
        }
    }

    private ResolvedTraceFile nextLaunch() {
        return TraceFileResolver.resolve(
                Map.<String, String>of()::get,
                Map.<String, String>of()::get,
                AppEnvironment.PROD,
                dataDir.resolve(AppPaths.DETAILED_LOG_MARKER));
    }

    @Test
    void switchOn_nextLaunchResolve_detailedOnFromMarker() throws Exception {
        final Injector injector = launch();

        injector.getInstance(DetailedLogSwitch.class).choose(true);
        waitFor(() -> Files.exists(dataDir.resolve(AppPaths.DETAILED_LOG_MARKER)));
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(nextLaunch()).isEqualTo(new ResolvedTraceFile(true, ResolvedTraceFile.Source.MARKER, null));
    }

    @Test
    void switchOff_markerDeleted_nextLaunchOff() throws Exception {
        final Injector injector = launch();
        final DetailedLogSwitch detailed = injector.getInstance(DetailedLogSwitch.class);
        detailed.choose(true);
        waitFor(() -> Files.exists(dataDir.resolve(AppPaths.DETAILED_LOG_MARKER)));

        detailed.choose(false);
        waitFor(() -> !Files.exists(dataDir.resolve(AppPaths.DETAILED_LOG_MARKER)));
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(nextLaunch()).isEqualTo(new ResolvedTraceFile(false, ResolvedTraceFile.Source.DEFAULT, null));
    }
}
