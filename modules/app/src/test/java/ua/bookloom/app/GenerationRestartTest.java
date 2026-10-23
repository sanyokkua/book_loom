package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.state.GenerationSettings;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** A Generation choice made in one launch is what the Generation tab shows in the next. */
class GenerationRestartTest {

    private static final long WAIT_SECONDS = 10;

    @TempDir
    private Path dataDir;

    private Injector launch() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
        final Injector injector = Guice.createInjector(BookLoomApplication.windowCore(startup), new UiModule());
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
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

    private void chooseLowAndTuned(final Injector injector) throws Exception {
        startToolkit();
        final GenerationSettings generation = injector.getInstance(GenerationSettings.class);
        final CompletableFuture<Void> chosen = new CompletableFuture<>();
        Platform.runLater(() -> {
            generation.choose(ReasoningSetting.LOW);
            generation.choose(SamplingSetting.TUNED);
            chosen.complete(null);
        });
        chosen.get(WAIT_SECONDS, TimeUnit.SECONDS);
        final ExecutorService background =
                injector.getInstance(Key.get(ExecutorService.class, BackgroundExecutor.class));
        background.shutdown();
        assertThat(background.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static ReasoningSetting reasoningShownAfterLoad(final GenerationSettings generation) throws Exception {
        final CompletableFuture<ReasoningSetting> shown = new CompletableFuture<>();
        Platform.runLater(() -> {
            generation
                    .loadedProperty()
                    .addListener((observed, was, now) ->
                            shown.complete(generation.reasoningProperty().get()));
            generation.load();
        });
        return shown.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void load_lowStoredBeforeRestart_newInjectorShowsLow() throws Exception {
        final Injector first = launch();
        chooseLowAndTuned(first);
        first.getInstance(SqliteDatabase.class).close();

        final Injector second = launch();
        final ReasoningSetting shown = reasoningShownAfterLoad(second.getInstance(GenerationSettings.class));
        second.getInstance(SqliteDatabase.class).close();

        assertThat(shown).isEqualTo(ReasoningSetting.LOW);
    }
}
