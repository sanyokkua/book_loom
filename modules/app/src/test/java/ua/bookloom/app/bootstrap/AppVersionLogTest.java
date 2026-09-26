package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.app.BookLoomApplication;
import ua.bookloom.app.StartupContext;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The build version reaches the log exactly once, at startup, and changing screens never repeats it.
 *
 * <p>The version is the first thing asked when a bug is reported, and the log is one of its two homes, so the failure
 * this guards against is a well-meaning "window shown, version X" line added to the shell that would then be written
 * on every start of every screen path. The startup line is emitted by {@code Launcher.logStartup}, exactly as the
 * launcher does before {@code Application.launch}; the application is then booted for real and driven through several
 * screens. Like {@link LoggingBootstrapTest} it reconfigures the JVM-wide logger context, so it resets it afterwards.
 */
class AppVersionLogTest {

    private static final long FX_TIMEOUT_SECONDS = 10;

    /** The version field as a whole word, case-sensitive, so a field such as {@code environment=DEV} never counts. */
    private static final Pattern VERSION_FIELD = Pattern.compile("\\bversion=dev\\b");

    @TempDir
    private Path dataDir;

    @AfterEach
    void restoreLogging() throws Exception {
        FxToolkit.cleanupStages();
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
            context.reset();
        }
    }

    /** Stops the context so the appender flushes and releases the file, then reads what it wrote. */
    private static List<String> logLines(final Path logFile) throws IOException {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
        return Files.readAllLines(logFile);
    }

    private static void onFx(final Runnable action) throws Exception {
        WaitForAsyncUtils.asyncFx(action).get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // WHEN the application starts and changes screens, THEN exactly one INFO line carries the build version `dev`
    // and no later line repeats it.
    @Test
    void startup_thenScreenChanges_logsTheBuildVersionExactlyOnce() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final AppPaths paths = AppPaths.of(dataDir, logDir);
        LoggingBootstrap.configure(
                logDir, false, new ResolvedLogLevel(Level.INFO, ResolvedLogLevel.Source.DEFAULT, null));
        Launcher.logStartup(paths, AppEnvironment.DEV);
        StartupContext.publish(new StartupContext(paths, AppEnvironment.DEV));
        FxToolkit.registerPrimaryStage();

        final BookLoomApplication app = (BookLoomApplication) FxToolkit.setupApplication(BookLoomApplication.class);
        final AppShellView shell = app.injector().getInstance(AppShellView.class);
        final Navigator navigator = app.injector().getInstance(Navigator.class);
        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        onFx(() -> navigator.navigate(ViewNames.SETTINGS));
        onFx(() -> shell.activate(ViewNames.IMPORT));

        assertThat(logLines(logDir.resolve("bookloom.log")))
                .filteredOn(line -> VERSION_FIELD.matcher(line).find())
                .hasSize(1)
                .allMatch(line -> line.contains("INFO"));
    }
}
