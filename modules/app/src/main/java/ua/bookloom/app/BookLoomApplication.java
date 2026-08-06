package ua.bookloom.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.UiModule;

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
public final class BookLoomApplication extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(BookLoomApplication.class);

    private static final String TITLE = "BookLoom";
    private static final double INITIAL_WIDTH = 1024;
    private static final double INITIAL_HEIGHT = 700;

    private final AppLifecycle lifecycle = new AppLifecycle();

    private @Nullable Injector injector;

    /** Constructed reflectively by the JavaFX launcher, which is why {@link StartupContext} exists. */
    public BookLoomApplication() {
        // Nothing here: init() runs before start() and is the supported place for real work.
    }

    @Override
    public void init() {
        final StartupContext startup = StartupContext.take();

        injector = Guice.createInjector(
                new AppModule(startup),
                new DocumentModule(),
                new LlmModule(),
                new PersistenceModule(),
                new PipelineModule(),
                new UiModule());

        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        LOG.info("injector built and two-phase init complete");
    }

    @Override
    public void start(Stage stage) {
        Objects.requireNonNull(stage, "stage");

        final Scene scene = new Scene(AppShellView.create(), INITIAL_WIDTH, INITIAL_HEIGHT);
        // Attached at SCENE level, not on a node: that is what makes the `.root` token block cascade to every
        // descendant, and it is the one attachment point every later screen inherits.
        scene.getStylesheets().add(Theme.stylesheet());

        // Throws if phase 2 has not run — the scene must not be built before resources are open, because the first
        // settings read would otherwise silently return a default from a database nobody had opened.
        lifecycle.sceneBuilt();

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
        LOG.info("primary stage shown");
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
