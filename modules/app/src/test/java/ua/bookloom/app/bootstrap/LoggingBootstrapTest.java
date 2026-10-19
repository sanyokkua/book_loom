package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ua.bookloom.app.AppVersion;
import ua.bookloom.ui.SessionInfo;
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

    private static final ResolvedTraceFile TRACE_ON =
            new ResolvedTraceFile(true, ResolvedLogLevel.Source.ENVIRONMENT, null);
    private static final Map<String, String> PROPERTIES = Map.of(
            "os.name", "Mac OS X",
            "os.version", "15.1",
            "os.arch", "aarch64",
            "java.vm.name", "OpenJDK 64-Bit Server VM",
            "java.runtime.version", "25+36");

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

    // The system SHALL write logs under the per-OS log directory it resolved, not a default
    // location chosen before that directory was known.
    @Test
    void configure_resolvedLogDir_writesThereRatherThanADefaultLocation() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));

        assertThat(LoggingBootstrap.configure(logDir, false, resolved(Level.INFO)))
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

        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));
        LoggerFactory.getLogger(LoggingBootstrapTest.class).info("file only");

        assertThat(logLines(logDir.resolve("bookloom.log"))).isNotEmpty();
    }

    /**
     * The other half of task 2.7's split. The resolver detects a network filesystem but cannot say so — it runs at
     * step 2, and there is no logger until step 5 — so the fact travels as data and is spoken for here. Without
     * this, the detection would be dead: flagged, carried, and silently dropped.
     */
    // IF the resolved data directory is on a network filesystem, THEN the system SHALL warn
    // at startup, because SQLite WAL locking is unreliable on network shares.
    @Test
    void warnIfOnNetworkFilesystem_flaggedPaths_writesItsOwnWarningLine() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));
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
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));
        final AppPaths paths = AppPaths.of(tempDir, logDir);

        Launcher.logStartup(paths, AppEnvironment.DEV);

        final List<String> lines = logLines(logDir.resolve("bookloom.log"));
        assertThat(lines).anyMatch(line -> line.contains("app started version=" + AppVersion.current()));
        assertThat(lines).anyMatch(line -> line.contains(tempDir.toString()));
    }

    @Test
    void warnIfOnNetworkFilesystem_localPaths_saysNothing() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));

        Launcher.warnIfOnNetworkFilesystem(AppPaths.of(tempDir, logDir));

        assertThat(logLines(logDir.resolve("bookloom.log"))).noneMatch(line -> line.contains("network filesystem"));
    }

    @Test
    void configure_traceLevel_writesTraceFromBookloomLogger() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.TRACE));

        LoggerFactory.getLogger("ua.bookloom.test").trace("trace canary");

        assertThat(logLines(logDir.resolve("bookloom.log"))).anyMatch(line -> line.contains("trace canary"));
    }

    @Test
    void configure_devDefault_writesDebugButNotTrace() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.DEBUG));
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");

        logger.debug("debug canary");
        logger.trace("trace canary");

        assertThat(logLines(logDir.resolve("bookloom.log"))).anyMatch(line -> line.contains("debug canary"));
        assertThat(logLines(logDir.resolve("bookloom.log"))).noneMatch(line -> line.contains("trace canary"));
    }

    @Test
    void configure_filtersInfoFromLoggerOutsideBookloom() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.DEBUG));

        LoggerFactory.getLogger("third.party").info("library info canary");

        assertThat(logLines(logDir.resolve("bookloom.log"))).noneMatch(line -> line.contains("library info canary"));
    }

    @Test
    void configure_pattern_includesJobMdcValue() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));

        try (var mdc = org.slf4j.MDC.putCloseable("job", "job-42")) {
            assertThat(mdc).isNotNull();
            LoggerFactory.getLogger("ua.bookloom.test").info("job canary");
        }

        assertThat(logLines(logDir.resolve("bookloom.log"))).anyMatch(line -> line.contains("[job=job-42 "));
    }

    // The segment id on a line is what lets one segment's decision be read out of a run's log.
    @Test
    void configure_pattern_includesSegmentMdcValue() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));

        try (var job = org.slf4j.MDC.putCloseable("job", "job-42");
                var segment = org.slf4j.MDC.putCloseable("segment", "Book.md:0")) {
            assertThat(job).isNotNull();
            assertThat(segment).isNotNull();
            LoggerFactory.getLogger("ua.bookloom.test").info("segment canary");
        }

        assertThat(logLines(logDir.resolve("bookloom.log")))
                .anyMatch(line -> line.contains("[job=job-42 segment=Book.md:0]"));
    }

    @Test
    void configure_rejectedLevel_writesOneWarningWithTheRejectedValue() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final ResolvedLogLevel rejected = new ResolvedLogLevel(Level.DEBUG, ResolvedLogLevel.Source.DEFAULT, "LOUD");

        LoggingBootstrap.configure(logDir, false, rejected);

        assertThat(logLines(logDir.resolve("bookloom.log")))
                .anyMatch(line -> line.contains("rejected log level value=LOUD"));
    }

    // A lowercase level name from the environment reaches the bootstrap as TRACE and switches TRACE lines on.
    @Test
    void configure_lowercaseTraceFromEnvironment_writesTraceLines() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final ResolvedLogLevel resolved = LoggingLevelResolver.resolve(
                Map.of("BOOKLOOM_LOG_LEVEL", "trace")::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        LoggingBootstrap.configure(logDir, false, resolved);
        LoggerFactory.getLogger("ua.bookloom.test").trace("lowercase trace canary");

        assertThat(logLines(logDir.resolve("bookloom.log"))).anyMatch(line -> line.contains("lowercase trace canary"));
    }

    // An unknown level in an installed app falls back to INFO and says so in exactly one WARN line naming the value.
    @Test
    void configure_unknownLevelInInstalledApp_fallsBackToInfoWithOneWarning() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final ResolvedLogLevel resolved = LoggingLevelResolver.resolve(
                Map.of("BOOKLOOM_LOG_LEVEL", "LOUD")::get, Map.<String, String>of()::get, AppEnvironment.PROD);

        LoggingBootstrap.configure(logDir, false, resolved);
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");
        logger.info("info canary");
        logger.debug("debug canary");

        final List<String> lines = logLines(logDir.resolve("bookloom.log"));
        assertThat(lines).anyMatch(line -> line.contains("info canary"));
        assertThat(lines).noneMatch(line -> line.contains("debug canary"));
        assertThat(lines)
                .filteredOn(line -> line.contains("rejected log level value=LOUD"))
                .singleElement()
                .satisfies(line -> assertThat(line).contains(" WARN "));
    }

    // The detailed log takes every BookLoom line down to TRACE while the ordinary log keeps the resolved level.
    @Test
    void configure_traceFileOn_writesTraceToTheTraceFileAndKeepsTheOrdinaryLogAtItsLevel() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# header");
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");

        logger.info("info canary");
        logger.debug("debug canary");
        logger.trace("trace canary");

        final List<String> trace = logLines(logDir.resolve("bookloom-trace.log"));
        assertThat(trace).anyMatch(line -> line.contains("info canary"));
        assertThat(trace).anyMatch(line -> line.contains(" DEBUG ") && line.contains("debug canary"));
        assertThat(trace).anyMatch(line -> line.contains(" TRACE ") && line.contains("trace canary"));
        final List<String> ordinary = logLines(logDir.resolve("bookloom.log"));
        assertThat(ordinary).anyMatch(line -> line.contains("info canary"));
        assertThat(ordinary).noneMatch(line -> line.contains("debug canary") || line.contains("trace canary"));
    }

    // Third-party libraries stay at WARN in the detailed log too, so it is BookLoom's story, not a library's.
    @Test
    void configure_traceFileOn_keepsThirdPartyInfoOutOfBothFiles() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.DEBUG), TRACE_ON, () -> "# header");

        LoggerFactory.getLogger("third.party").info("library info canary");
        LoggerFactory.getLogger("third.party").warn("library warn canary");

        final List<String> trace = logLines(logDir.resolve("bookloom-trace.log"));
        assertThat(trace).noneMatch(line -> line.contains("library info canary"));
        assertThat(trace).anyMatch(line -> line.contains("library warn canary"));
        assertThat(logLines(logDir.resolve("bookloom.log"))).noneMatch(line -> line.contains("library info canary"));
    }

    @Test
    void configure_traceFileOff_writesNoTraceFile() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final ResolvedTraceFile off = new ResolvedTraceFile(false, ResolvedLogLevel.Source.DEFAULT, null);
        LoggingBootstrap.configure(logDir, false, resolved(Level.DEBUG), off, () -> "# header");

        LoggerFactory.getLogger("ua.bookloom.test").trace("trace canary");
        LoggerFactory.getLogger("ua.bookloom.test").debug("debug canary");

        assertThat(logLines(logDir.resolve("bookloom.log")))
                .anyMatch(line -> line.contains("debug canary"))
                .noneMatch(line -> line.contains("trace canary"));
        assertThat(logDir.resolve("bookloom-trace.log")).doesNotExist();
    }

    // The header is the first thing in the file, so a shared file names its build and session before any line.
    @Test
    void configure_traceFileOn_writesTheSessionHeaderAtTheTop() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final Map<String, String> session = new LinkedHashMap<>();
        session.put("book", "Kobzar.fb2");
        final SessionHeader header = new SessionHeader(
                "1.2.3", AppEnvironment.PROD, resolved(Level.INFO), TRACE_ON, PROPERTIES::get, () -> session);

        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, header::render);
        LoggerFactory.getLogger("ua.bookloom.test").info("after the header");

        final List<String> trace = logLines(logDir.resolve("bookloom-trace.log"));
        assertThat(trace.getFirst()).startsWith("# ==== BookLoom diagnostic log");
        assertThat(trace)
                .contains("# version=1.2.3 environment=PROD")
                .contains("# os=Mac OS X 15.1 aarch64")
                .contains("# jvm=OpenJDK 64-Bit Server VM 25+36")
                .contains("# logLevel=INFO (DEFAULT) detailedLog=on (ENVIRONMENT)")
                .contains("# session book=Kobzar.fb2");
        assertThat(trace.indexOf("# session book=Kobzar.fb2")).isLessThan(indexOfFirst(trace, "after the header"));
    }

    // A rotated-in file starts with the header again, rendered with the session as it stands at that moment.
    @Test
    void configure_traceFileRolledOver_writesTheHeaderAgainWithTheCurrentSession() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final Map<String, String> session = new LinkedHashMap<>();
        LoggingBootstrap.configure(
                logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session " + SessionInfo.format(session));
        LoggerFactory.getLogger("ua.bookloom.test").info("before the rollover");
        session.put("model", "gemma4:e4b");

        traceAppender().rollover();
        LoggerFactory.getLogger("ua.bookloom.test").info("after the rollover");

        final List<String> active = logLines(logDir.resolve("bookloom-trace.log"));
        assertThat(active.getFirst()).isEqualTo("# session model=gemma4:e4b");
        assertThat(active).anyMatch(line -> line.contains("after the rollover"));
        assertThat(active).noneMatch(line -> line.contains("before the rollover"));
        assertThat(logDir.resolve("bookloom-trace.1.log.gz")).isRegularFile();
    }

    // One event, one line: a message with line breaks (a prompt, a reply) is not split into lines that look like
    // events.
    @Test
    void configure_traceFileOn_writesAMultiLineMessageOnOneLine() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# header");

        LoggerFactory.getLogger("ua.bookloom.test").trace("first line\nsecond line\r\nthird line");

        assertThat(logLines(logDir.resolve("bookloom-trace.log")))
                .anyMatch(line -> line.contains("first line ⏎ second line ⏎ third line"))
                .noneMatch(line -> line.startsWith("second line"));
    }

    private static int indexOfFirst(final List<String> lines, final String text) {
        return lines.stream()
                .filter(line -> line.contains(text))
                .findFirst()
                .map(lines::indexOf)
                .orElse(-1);
    }

    @SuppressWarnings("unchecked")
    private static RollingFileAppender<ILoggingEvent> traceAppender() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return (RollingFileAppender<ILoggingEvent>)
                context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender(LoggingBootstrap.TRACE_APPENDER_NAME);
    }

    private static ResolvedLogLevel resolved(Level level) {
        return new ResolvedLogLevel(level, ResolvedLogLevel.Source.DEFAULT, null);
    }
}
