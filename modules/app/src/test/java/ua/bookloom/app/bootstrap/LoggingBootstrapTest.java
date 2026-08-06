package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.app.AppVersion;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * Programmatic logging configuration — startup step 5 — and the warning that depends on it.
 *
 * <p>The assertion that matters is not "logging works" but "logging writes <em>here</em>". A misconfigured appender
 * does not crash: the application runs perfectly and puts its log somewhere nobody looks, which is why this failure
 * mode reaches users rather than tests unless something explicitly checks the destination.
 *
 * <p>These tests reconfigure the JVM-wide {@link LoggerContext}, so {@link #restoreLogging()} resets it afterwards
 * rather than leaving a temp directory wired up for whatever runs next.
 */
class LoggingBootstrapTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreLogging() {
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
        return Files.exists(logFile) ? Files.readAllLines(logFile) : List.of();
    }

    // Covers: FR-PERSIST-03 — The system SHALL write logs under the per-OS log directory it resolved, not a default
    // location chosen before that directory was known.
    @Test
    void configure_resolvedLogDir_writesThereRatherThanADefaultLocation() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));

        assertThat(LoggingBootstrap.configure(logDir, false))
                .as("a false return means the SLF4J provider did not resolve at all, so nothing below is meaningful")
                .isTrue();
        LoggerFactory.getLogger(LoggingBootstrapTest.class).info("configured appender canary");

        assertThat(logLines(logDir.resolve("bookloom.log")))
                .as("the appender must follow the resolved directory, which is the whole reason it is built in code")
                .anyMatch(line -> line.contains("configured appender canary"));
    }

    @Test
    void configure_noConsoleRequested_stillWritesToTheFile() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));

        LoggingBootstrap.configure(logDir, false);
        LoggerFactory.getLogger(LoggingBootstrapTest.class).info("file only");

        assertThat(logLines(logDir.resolve("bookloom.log"))).isNotEmpty();
    }

    /**
     * The other half of task 2.7's split. The resolver detects a network filesystem but cannot say so — it runs at
     * step 2, and there is no logger until step 5 — so the fact travels as data and is spoken for here. Without
     * this, the detection would be dead: flagged, carried, and silently dropped.
     */
    // Covers: FR-PERSIST-03 — IF the resolved data directory is on a network filesystem, THEN the system SHALL warn
    // at startup, because SQLite WAL locking is unreliable on network shares.
    @Test
    void warnIfOnNetworkFilesystem_flaggedPaths_writesItsOwnWarningLine() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false);
        final AppPaths flagged = AppPaths.of(tempDir, logDir).withNetworkFilesystem(true);

        Launcher.warnIfOnNetworkFilesystem(flagged);

        final List<String> lines = logLines(logDir.resolve("bookloom.log"));
        assertThat(lines)
                .as("its own line, not a field on the startup message: a warning buried in a normal-looking line "
                        + "is read straight past")
                .anyMatch(line -> line.contains("WARN") && line.contains("network filesystem"));
        assertThat(lines).anyMatch(line -> line.contains(tempDir.toString()));
    }

    /**
     * The startup line, asserted for its own sake.
     *
     * <p>A no-op logger emits nothing, so the presence of this line is the cheapest available proof that the SLF4J
     * service binding really resolved under JPMS — a failure mode a {@code requires ch.qos.logback.classic} that
     * satisfies {@code javac} does nothing to rule out. It also carries the resolved data directory, which is what
     * makes a packaged image built without the {@code -Dbookloom.env=prod} stamp visible rather than merely wrong.
     */
    @Test
    void logStartup_afterLoggingIsConfigured_writesTheVersionAndTheResolvedDataDir() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false);
        final AppPaths paths = AppPaths.of(tempDir, logDir);

        Launcher.logStartup(paths, AppEnvironment.DEV);

        final List<String> lines = logLines(logDir.resolve("bookloom.log"));
        assertThat(lines).anyMatch(line -> line.contains("app started version=" + AppVersion.current()));
        assertThat(lines).anyMatch(line -> line.contains(tempDir.toString()));
    }

    @Test
    void warnIfOnNetworkFilesystem_localPaths_saysNothing() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false);

        Launcher.warnIfOnNetworkFilesystem(AppPaths.of(tempDir, logDir));

        assertThat(logLines(logDir.resolve("bookloom.log"))).noneMatch(line -> line.contains("network filesystem"));
    }
}
