package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.state.AutomationSettings;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** A mode chosen in Settings reaches the next run start through the real source and the real store. */
class AutomationReviewModeTest {

    private static final long WAIT_SECONDS = 10;

    @TempDir
    private Path dataDir;

    private Injector launch(final Function<String, @Nullable String> env) throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir), AppEnvironment.DEV, ReviewModeResolver.resolve(env, name -> null));
        final Injector injector = Guice.createInjector(BookLoomApplication.windowCore(startup), new UiModule());
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        startToolkit();
        return injector;
    }

    private static void startToolkit() throws InterruptedException {
        try {
            final CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit is shared by the test JVM; a running one is what is wanted.
        }
    }

    private static void waitUntilStored(final SettingsRepository settings, final ReviewMode wanted)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline
                && !Optional.of(wanted)
                        .equals(settings.get(AppSetting.REVIEW_MODE).data())) {
            Thread.sleep(20);
        }
    }

    // Reads on the FX thread again and again until the told mode arrives or the wait ends; returns the last read.
    private static ReviewMode readOnFxThreadUntil(final ReviewModeSource source, final ReviewMode wanted)
            throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        ReviewMode read = readOnFxThread(source);
        while (read != wanted && System.nanoTime() < deadline) {
            Thread.sleep(20);
            read = readOnFxThread(source);
        }
        return read;
    }

    private static ReviewMode readOnFxThread(final ReviewModeSource source) throws Exception {
        final CompletableFuture<ReviewMode> read = new CompletableFuture<>();
        Platform.runLater(() -> read.complete(source.current()));
        return read.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    // IF Settings did not tell the source, THEN a start on the FX thread would keep the old mode: the FX read never
    // touches the store, so only the told mode can reach it. No read off the FX thread may happen in between.
    @Test
    void choose_manual_nextOffFxReadAndFxReadAreManual() throws Exception {
        final Injector injector = launch(name -> null);
        final ReviewModeSource source = injector.getInstance(ReviewModeSource.class);

        injector.getInstance(AutomationSettings.class).chooseReviewMode(ReviewMode.MANUAL);
        final ReviewMode onFx = readOnFxThreadUntil(source, ReviewMode.MANUAL);
        waitUntilStored(injector.getInstance(SettingsRepository.class), ReviewMode.MANUAL);
        final ReviewMode offFx = source.current();
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(onFx).isEqualTo(ReviewMode.MANUAL);
        assertThat(offFx).isEqualTo(ReviewMode.MANUAL);
    }

    @Test
    void choose_manualWithEnvUnattended_runStillUnattended() throws Exception {
        final Injector injector = launch(name -> "BOOKLOOM_REVIEW_MODE".equals(name) ? "unattended" : null);
        final ReviewModeSource source = injector.getInstance(ReviewModeSource.class);

        injector.getInstance(AutomationSettings.class).chooseReviewMode(ReviewMode.MANUAL);
        waitUntilStored(injector.getInstance(SettingsRepository.class), ReviewMode.MANUAL);
        final ReviewMode offFx = source.current();
        final ReviewMode onFx = readOnFxThread(source);
        injector.getInstance(SqliteDatabase.class).close();

        assertThat(offFx).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(onFx).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(source.launchOverride()).contains("BOOKLOOM_REVIEW_MODE");
    }
}
