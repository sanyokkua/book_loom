package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The stored review mode reaches FX-thread readers (the ready card) after one off-thread read at startup. */
class ReviewModeSeedTest {

    private static final long FX_WAIT_SECONDS = 10;

    @TempDir
    private Path dataDir;

    // IF MANUAL is stored and startup reads the mode once off the FX thread, THEN the FX thread sees MANUAL.
    @Test
    void current_seededOffTheFxThread_fxThreadReadSeesTheStoredMode() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        final Injector injector =
                Guice.createInjector(new CoreModules(startup, BookLoomApplication.windowPersistence(startup)));
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        injector.getInstance(SettingsRepository.class).put(AppSetting.REVIEW_MODE, ReviewMode.MANUAL);
        final ReviewModeSource source = injector.getInstance(ReviewModeSource.class);

        source.current();
        final ReviewMode onFx = readOnFxThread(source);
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(onFx).isEqualTo(ReviewMode.MANUAL);
    }

    private static ReviewMode readOnFxThread(final ReviewModeSource source) throws Exception {
        try {
            final CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(FX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit is shared by the test JVM; a running one is what is wanted.
        }
        final CompletableFuture<ReviewMode> read = new CompletableFuture<>();
        Platform.runLater(() -> read.complete(source.current()));
        return read.get(FX_WAIT_SECONDS, TimeUnit.SECONDS);
    }
}
