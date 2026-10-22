package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The packaged smoke's resolve-all step, run against the same composition root the image boots. */
class ResolveAllTest {

    private static final String PROPERTY = "bookloom.smoke.resolveAll";

    @TempDir
    private Path dataDir;

    @BeforeEach
    void setProperty() {
        System.setProperty(PROPERTY, "true");
    }

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty(PROPERTY);
        FxToolkit.cleanupStages();
    }

    private StartupContext startup() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
    }

    private BookLoomApplication boot() throws Exception {
        StartupContext.publish(startup());
        FxToolkit.registerPrimaryStage();
        return (BookLoomApplication) FxToolkit.setupApplication(BookLoomApplication.class);
    }

    private static ResolveAll.Outcome onFx(final Injector injector, final ResolveAll.Navigation navigation)
            throws Exception {
        return WaitForAsyncUtils.asyncFx(() -> ResolveAll.check(injector, navigation, ViewNames.PROJECTS))
                .get();
    }

    private static final List<String> AVAILABLE = Arrays.stream(ViewNames.values())
            .filter(ViewNames::isAvailable)
            .map(ViewNames::name)
            .toList();

    @Test
    void check_bootedApplication_loadsEveryAvailableViewAndResolvesTheCorePorts() throws Exception {
        final Injector injector = boot().injector();
        final Navigator navigator = injector.getInstance(Navigator.class);

        final ResolveAll.Outcome outcome = onFx(injector, ResolveAll.navigation(navigator));

        assertThat(outcome.failures()).isEmpty();
        assertThat(outcome.loadedViews()).containsExactlyElementsOf(AVAILABLE);
        assertThat(outcome.resolvedPorts())
                .contains("ReviewDesk", "ExportService", "GlossaryService", "ProjectService", "TranslationEngine");
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.PROJECTS);
    }

    @Test
    void check_reviewDeskProviderThrows_namesReviewDeskAndPassesTheRest() throws Exception {
        FxToolkit.registerPrimaryStage();
        final Injector injector = Guice.createInjector(
                Stage.DEVELOPMENT,
                Modules.override(new CoreModules(startup(), new InMemoryPersistenceModule()), new UiModule())
                        .with(new ThrowingReviewDesk()));

        final ResolveAll.Outcome outcome = ResolveAll.resolvePorts(injector);

        assertThat(outcome.failures()).hasSize(1).first().asString().contains("ReviewDesk");
        assertThat(outcome.resolvedPorts()).contains("ExportService", "GlossaryService", "ProjectService");
        assertThat(outcome.resolvedPorts()).doesNotContain("ReviewDesk");
    }

    @Test
    void check_exportScreenMissing_namesExportAsFailed() throws Exception {
        final Injector injector = boot().injector();
        final Navigator navigator = injector.getInstance(Navigator.class);
        final ResolveAll.Navigation real = ResolveAll.navigation(navigator);
        final ResolveAll.Navigation noExport = view -> view == ViewNames.EXPORT
                ? Optional.ofNullable(navigator.currentView().get())
                : real.show(view);

        final ResolveAll.Outcome outcome = onFx(injector, noExport);

        assertThat(outcome.failures()).hasSize(1).first().asString().contains("EXPORT");
        assertThat(outcome.loadedViews()).doesNotContain("EXPORT").contains("SETTINGS", "TRANSLATING");
    }

    private static final class ThrowingReviewDesk extends AbstractModule {
        @Provides
        ReviewDesk reviewDesk() {
            throw new IllegalStateException("review desk cannot be built");
        }
    }
}
