package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.inject.Guice;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppRestart;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The core graph answers an {@link AppRestart} whose call does nothing until the window binds the real one. */
class NoOpAppRestartTest {

    @TempDir
    private Path dataDir;

    @Test
    void restart_coreGraph_resolvesAndDoesNothing() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));

        final AppRestart restart =
                Guice.createInjector(new CoreModules(startup)).getInstance(AppRestart.class);

        assertThatCode(restart::restart).doesNotThrowAnyException();
    }
}
