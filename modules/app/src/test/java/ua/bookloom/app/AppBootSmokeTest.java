package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.ui.IoExecutor;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The boot smoke: the whole composition root, started for real against a temporary data directory.
 *
 * <p>This is the test the change exists to buy. Java 25 + JPMS + JavaFX 26 + Guice are individually routine and
 * jointly the least-proven combination in the project, and every way they fail together fails at <em>runtime</em>: a
 * {@code requires} clause satisfies {@code javac} while a missing {@code opens … to com.google.guice} surfaces only
 * when Guice reflects, and only during startup. Nothing short of building the injector and showing a stage can
 * distinguish "compiles" from "runs".
 *
 * <p>Infrastructure: it proves an integration, not a product requirement.
 *
 * <p>It runs against a temp directory rather than the developer's real one for a reason that would otherwise bite
 * intermittently: the real data directory may hold a lock belonging to an actually-running BookLoom, and a smoke
 * that fights it would fail for a reason having nothing to do with the code under test.
 */
class AppBootSmokeTest {

    @TempDir
    private Path dataDir;

    @AfterEach
    void cleanup() throws Exception {
        FxToolkit.cleanupStages();
    }

    private AppPaths tempPaths() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return AppPaths.of(dataDir, logDir);
    }

    @Test
    void start_realApplication_buildsTheInjectorRunsBothPhasesAndShowsTheStage() throws Exception {
        StartupContext.publish(new StartupContext(tempPaths(), AppEnvironment.DEV));
        FxToolkit.registerPrimaryStage();

        final BookLoomApplication app = (BookLoomApplication) FxToolkit.setupApplication(BookLoomApplication.class);

        assertThat(app.injector())
                .as("an injector spanning eight JPMS modules either assembles or it does not; this is the signal")
                .isNotNull();

        assertThat(app.lifecycle().completedSteps())
                .as("the ordering is the reserved position change 8 fills, so it is asserted, not assumed")
                .containsExactly(AppLifecycle.PHASE_ONE, AppLifecycle.PHASE_TWO, AppLifecycle.SCENE);

        final Stage stage = FxToolkit.toolkitContext().getRegisteredStage();
        assertThat(stage.isShowing()).isTrue();
        assertThat(stage.getTitle()).isEqualTo("BookLoom");
        final Scene scene = stage.getScene();
        assertThat(scene.lookup("#shell-title-bar"))
                .as("the real chrome must actually be in the scene graph, not merely constructed")
                .isNotNull();
        assertThat(scene.lookup("#shell-nav")).isNotNull();
        assertThat(scene.lookup("#shell-breadcrumb")).isNotNull();
        assertThat(scene.lookup("#shell-content")).isNotNull();
    }

    // IF the real stage had no minimum, THEN a person could shrink the window until nothing was visible.
    @Test
    void start_realApplication_setsTheWindowMinimum() throws Exception {
        StartupContext.publish(new StartupContext(tempPaths(), AppEnvironment.DEV));
        FxToolkit.registerPrimaryStage();

        FxToolkit.setupApplication(BookLoomApplication.class);

        final Stage stage = FxToolkit.toolkitContext().getRegisteredStage();
        assertThat(stage.getMinWidth()).isEqualTo(960);
        assertThat(stage.getMinHeight()).isEqualTo(640);
    }

    // IF the composition root showed the window before navigating, THEN the person would open on an empty screen
    // area with no entry marked; the first available view is Import.
    @Test
    void start_realApplication_opensOnTheFirstAvailableViewWithItsBreadcrumb() throws Exception {
        StartupContext.publish(new StartupContext(tempPaths(), AppEnvironment.DEV));
        FxToolkit.registerPrimaryStage();

        FxToolkit.setupApplication(BookLoomApplication.class);

        final Scene scene = FxToolkit.toolkitContext().getRegisteredStage().getScene();
        assertThat(((Label) scene.lookup("#shell-breadcrumb")).getText())
                .isEqualTo("Workflow / Import book · step 1 of 7");
    }

    /**
     * The bindings that only exist if Guice really resolved the graph. Asking the injector for them is a stronger
     * check than "the injector is not null": an injector can be constructed and still fail on first use.
     */
    @Test
    void injector_afterBoot_suppliesTheApplicationScopedBindings() throws Exception {
        StartupContext.publish(new StartupContext(tempPaths(), AppEnvironment.DEV));
        FxToolkit.registerPrimaryStage();

        final BookLoomApplication app = (BookLoomApplication) FxToolkit.setupApplication(BookLoomApplication.class);

        assertThat(app.injector().getInstance(AppPaths.class).dataDir()).isEqualTo(dataDir);
        assertThat(app.injector().getInstance(AppEnvironment.class)).isEqualTo(AppEnvironment.DEV);
        assertThat(app.injector().getInstance(com.google.inject.Key.get(ExecutorService.class, IoExecutor.class)))
                .isNotNull();
        assertThat(app.injector().getInstance(TranslationEngine.class)).isNotNull();
        assertThat(app.injector().getInstance(ChatModelFactory.class)).isNotNull();
    }
}
