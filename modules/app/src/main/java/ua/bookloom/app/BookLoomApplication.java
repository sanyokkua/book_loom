package ua.bookloom.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.persistence.SqlitePersistenceModule;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.ExitGuard;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.OpenStepsModule;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;

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

    private final AppLifecycle lifecycle = new AppLifecycle();

    private @Nullable Injector injector;

    /** Constructed reflectively by the JavaFX launcher, which is why {@link StartupContext} exists. */
    public BookLoomApplication() {
        // Nothing here: init() runs before start() and is the supported place for real work.
    }

    @Override
    public void init() {
        final StartupContext startup = StartupContext.take();

        injector = ResolveAll.isEnabled()
                ? smokeBuild(Platform::runLater, () -> smokeInjector(startup))
                : developmentInjector(startup);

        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        // init() is not the FX thread: this read seeds the stored review mode the FX-thread readers (ready card) see.
        log.debug(
                "seeded review mode {}",
                injector.getInstance(ReviewModeSource.class).current());
        log.info("injector built and two-phase init complete");
        // The toolkit is up by init(), so its version is known now and not when the launcher configured logging.
        injector.getInstance(SessionInfo.class)
                .update(Map.of("javafx", System.getProperty(JAVAFX_VERSION_PROPERTY, "unknown")));
    }

    /** The window keeps its work in the database file of the data folder; the command line never does. */
    static Module windowPersistence(final StartupContext startup) {
        return new SqlitePersistenceModule(startup.paths().databaseFile(), Clock.system(ZoneId.systemDefault()));
    }

    private static Injector developmentInjector(final StartupContext startup) {
        return Guice.createInjector(
                new CoreModules(startup, windowPersistence(startup)), new UiModule(), new DesktopModule());
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
        return Guice.createInjector(
                com.google.inject.Stage.PRODUCTION,
                new CoreModules(startup, windowPersistence(startup)),
                ui,
                new DesktopModule());
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
        }
    }

    /** The first entry with a screen, so the window never opens on an empty screen area while any screen exists. */
    private static Optional<ViewNames> initialView() {
        return Arrays.stream(ViewNames.values()).filter(ViewNames::isAvailable).findFirst();
    }

    @Override
    public void stop() {
        log.info("window closing, releasing resources");
        lifecycle.close();
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
