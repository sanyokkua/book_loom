package ua.bookloom.app;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Message;
import com.google.inject.util.Modules;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.app.bootstrap.StartupFailureDialog;
import ua.bookloom.persistence.SqlitePersistenceModule;
import ua.bookloom.persistence.StorageOpenException;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.ExitGuard;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.OpenStepsModule;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.ui.StartupMessages;
import ua.bookloom.ui.StoredPreferences;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.WindowLaunch;

/**
 * The JavaFX application: everything after the launcher, and the only place an injector is constructed.
 *
 * <p>It runs entirely on the far side of logging, so unlike the {@code bootstrap} package it may take a logger
 * freely.
 *
 * <p><strong>All six Guice modules are installed, even though four of them bind nothing yet.</strong> That is the
 * point of this change rather than an oversight: an injector that assembles a graph spanning eight JPMS modules is
 * the integration signal being bought here. A {@code requires} clause satisfies {@code javac} while a missing
 * {@code opens … to com.google.guice} fails only when Guice reflects, and only at startup — so wiring in just the
 * two modules with content would prove strictly less, and would prove it about the easy half.
 */
@Slf4j
public final class BookLoomApplication extends Application {

    private static final String TITLE = "BookLoom";
    private static final double INITIAL_WIDTH = 1024;
    private static final double INITIAL_HEIGHT = 700;
    private static final String JAVAFX_VERSION_PROPERTY = "javafx.runtime.version";
    private static final int EXIT_STORAGE_FAILED = 1;

    private final AppLifecycle lifecycle = new AppLifecycle();
    private final BiConsumer<AppError, Integer> startupFailure;

    private @Nullable Injector injector;
    private @Nullable Stage window;

    /** Constructed reflectively by the JavaFX launcher, which is why {@link StartupContext} exists. */
    public BookLoomApplication() {
        this(StartupFailureDialog::showOnRunningToolkitAndExit);
    }

    /**
     * For a test that watches where a startup failure goes instead of letting the process exit.
     *
     * @param startupFailure shows the failure and ends the process; a test's recorder returns instead
     */
    BookLoomApplication(final BiConsumer<AppError, Integer> startupFailure) {
        this.startupFailure = Objects.requireNonNull(startupFailure, "startupFailure");
    }

    @Override
    public void init() {
        final StartupContext startup = StartupContext.take();
        final Injector built = buildAndOpen(startup);
        injector = built;
        // init() is not the FX thread: this read seeds the stored review mode the FX-thread readers (ready card) see.
        log.debug(
                "seeded review mode {}",
                built.getInstance(ReviewModeSource.class).current());
        seedPreferences(built);
        log.info("injector built and two-phase init complete");
        // The toolkit is up by init(), so its version is known now and not when the launcher configured logging.
        built.getInstance(SessionInfo.class)
                .update(Map.of("javafx", System.getProperty(JAVAFX_VERSION_PROPERTY, "unknown")));
    }

    // A database that cannot be opened stops the start with its own message; any other failure stays a crash. The
    // database opens while the injector is built (an eager singleton reads the providers), not only in phase two, so
    // both sit inside the one catch.
    private Injector buildAndOpen(final StartupContext startup) {
        try {
            final Injector built = ResolveAll.isEnabled()
                    ? smokeBuild(Platform::runLater, () -> smokeInjector(startup))
                    : developmentInjector(startup);
            lifecycle.phaseOne(built);
            lifecycle.phaseTwo(built);
            return built;
        } catch (RuntimeException e) {
            final AppError storage = storageFailure(e).orElseThrow(() -> e);
            log.error("startup stopped: the database could not be opened, code={}", storage.code());
            startupFailure.accept(
                    StartupMessages.storageFailure(storage, startup.paths().dataDir(), Locale.getDefault()),
                    EXIT_STORAGE_FAILED);
            throw e;
        }
    }

    // Guice reports a failure while it builds the graph as a CreationException whose messages hold the causes.
    static Optional<AppError> storageFailure(final Throwable thrown) {
        final Optional<AppError> direct = StorageOpenException.errorIn(thrown);
        if (direct.isPresent() || !(thrown instanceof CreationException creation)) {
            return direct;
        }
        return creation.getErrorMessages().stream()
                .map(Message::getCause)
                .filter(Objects::nonNull)
                .map(StorageOpenException::errorIn)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Reads the stored language and theme once, off the FX thread, before any window is built. {@code Messages}
     * decides its language at first use, so this must run before {@code start()} and before the first message is
     * resolved.
     *
     * @param injector the built injector, with phase two done
     */
    static void seedPreferences(final Injector injector) {
        injector.getInstance(StoredPreferences.class).load();
        log.debug("seeded the stored language and theme");
    }

    /** The window keeps its work in the database file of the data folder; the command line never does. */
    static Module windowPersistence(final StartupContext startup) {
        return new SqlitePersistenceModule(startup.paths().databaseFile(), Clock.system(ZoneId.systemDefault()));
    }

    /** The shared graph with the window's own bindings laid over it: the real keep-awake and the real restart. */
    static Module windowCore(final StartupContext startup) {
        return Modules.override(new CoreModules(startup, windowPersistence(startup)))
                .with(new DesktopModule());
    }

    private static Injector developmentInjector(final StartupContext startup) {
        return Guice.createInjector(windowCore(startup), new UiModule());
    }

    /**
     * {@code init()} runs on the launcher thread, and the production stage builds every singleton at once, including
     * controls that only construct on the FX thread. A broken image throws an {@code Error} there, which must reach
     * the waiting thread or {@code init()} never returns and the smoke only times out.
     */
    static Injector smokeBuild(final Executor fxThread, final Supplier<Injector> build) {
        final CompletableFuture<Injector> built = new CompletableFuture<>();
        fxThread.execute(() -> {
            try {
                built.complete(build.get());
            } catch (Throwable e) {
                built.completeExceptionally(e);
            }
        });
        try {
            return built.join();
        } catch (CompletionException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("resolve-all FAILED: injector build: {}", cause.getClass().getSimpleName(), cause);
            throw e;
        }
    }

    /** Production stage, so every singleton is built now, and every step open so the smoke can load every screen. */
    private static Injector smokeInjector(final StartupContext startup) {
        log.info("resolve-all smoke: building the injector in the production stage");
        final Module ui = Modules.override(new UiModule()).with(new OpenStepsModule());
        return Guice.createInjector(com.google.inject.Stage.PRODUCTION, windowCore(startup), ui);
    }

    @Override
    public void start(Stage stage) {
        Objects.requireNonNull(stage, "stage");

        final AppShellView shell = injector().getInstance(AppShellView.class);
        // The shell attaches the theme at SCENE level, not on a node: that is what makes the `.root` token block
        // cascade to every descendant, and it is the one attachment point every later screen inherits.
        final Scene scene = shell.createScene(INITIAL_WIDTH, INITIAL_HEIGHT);

        // Throws if phase 2 has not run — the scene must not be built before resources are open, because the first
        // settings read would otherwise silently return a default from a database nobody had opened.
        lifecycle.sceneBuilt();

        final Optional<ViewNames> initial = initialView();
        initial.ifPresent(shell::activate);

        stage.setTitle(TITLE);
        shell.applyWindowLimits(stage);
        placeWindow(stage);
        // Closing with work under way asks first; stage.close() is not a close request, so confirming does not loop.
        final ExitGuard exitGuard = injector().getInstance(ExitGuard.class);
        stage.setOnCloseRequest(event -> {
            if (!exitGuard.requestClose(stage::close)) {
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.show();
        log.info(
                "window shown, initialView={}",
                injector().getInstance(Navigator.class).currentView().get());
        if (ResolveAll.isEnabled() && initial.isPresent()) {
            ResolveAll.run(injector(), injector().getInstance(Navigator.class), initial.get());
        } else {
            final WindowLaunch launch = injector().getInstance(WindowLaunch.class);
            launch.announceStorageSetAside();
            launch.openLastProject();
            launch.lookForLocalModel();
        }
    }

    /** Projects, the way into the workflow: with no project open every numbered step is locked. */
    private static Optional<ViewNames> initialView() {
        return Optional.of(ViewNames.PROJECTS);
    }

    @Override
    public void stop() {
        log.info("window closing, releasing resources");
        keepWindowForRestart();
        lifecycle.close();
    }

    // Set by the copy that closed for "Restart now"; an ordinary launch has none and opens at the default size.
    private void placeWindow(final Stage stage) {
        WindowPlacement.parse(System.getenv(WindowPlacement.ENVIRONMENT))
                .ifPresent(placement -> WindowPlacement.apply(stage, placement));
        window = stage;
    }

    private void keepWindowForRestart() {
        final Stage shown = window;
        if (shown == null || injector == null) {
            return;
        }
        final PendingRestart restart =
                injector.getInstance(StartupContext.class).pendingRestart();
        if (restart.requested().isPresent()) {
            log.debug("restart asked: the window's place and size go to the next copy");
            restart.keepWindow(WindowPlacement.of(shown));
        }
    }

    /**
     * The assembled injector.
     *
     * @return the injector built during {@code init()}
     * @throws IllegalStateException if called before {@code init()} has run
     */
    public Injector injector() {
        return Objects.requireNonNull(injector, "injector is built in init(); this was called before it ran");
    }

    /**
     * The startup steps that have completed, in order.
     *
     * @return the lifecycle record, for the boot smoke to assert against
     */
    public AppLifecycle lifecycle() {
        return lifecycle;
    }
}
