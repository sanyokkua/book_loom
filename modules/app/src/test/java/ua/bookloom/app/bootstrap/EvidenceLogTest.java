package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import ua.bookloom.util.paths.AppPaths;

/** The evidence log keeps a flagged segment's TRACE after the detailed log has rotated past it. */
class EvidenceLogTest {

    private static final ResolvedTraceFile TRACE_ON =
            new ResolvedTraceFile(true, ResolvedLogLevel.Source.ENVIRONMENT, null);

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

    // IF the evidence rode in the rolling file, THEN a long run's rotation would drop the flagged segments' TRACE
    // lines hours before anyone asks why they were flagged (the 8.3 hour run kept 3.6 hours).
    @Test
    void configure_flaggedSegmentKept_itsTraceSurvivesTheDetailedLogRotatingPastIt() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session");
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");
        traceFor("seg-flagged", logger, "prompt text of the flagged segment");
        traceFor("seg-accepted", logger, "prompt text of the accepted segment");
        keep("seg-flagged", logger);

        for (int rotation = 0; rotation < 6; rotation++) {
            logger.trace("filler line {}", rotation);
            traceAppender().rollover();
        }

        assertThat(logLines(logDir.resolve("bookloom-trace.log"))).noneMatch(line -> line.contains("flagged segment"));
        assertThat(logLines(logDir.resolve(AppPaths.EVIDENCE_LOG_FILE_NAME)))
                .anyMatch(line -> line.contains("prompt text of the flagged segment"))
                .noneMatch(line -> line.contains("accepted segment"));
    }

    // IF a segment's lines before its decision were not held back, THEN the file would hold every segment, not
    // only the flagged ones, and be as large as the rolling log it exists to outlive.
    @Test
    void configure_segmentNeverKept_writesNothingToTheEvidenceLog() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session");

        traceFor("seg-accepted", LoggerFactory.getLogger("ua.bookloom.test"), "an accepted draft");

        assertThat(logLines(logDir.resolve(AppPaths.EVIDENCE_LOG_FILE_NAME)))
                .noneMatch(line -> line.contains("accepted draft"));
    }

    // IF the evidence log were written with the detailed log off, THEN an installed app would keep book text the
    // person never asked it to.
    @Test
    void configure_detailedLogOff_writesNoEvidenceLog() {
        final Path logDir = tempDir.resolve("logs");
        logDir.toFile().mkdirs();
        LoggingBootstrap.configure(logDir, false, resolved(Level.TRACE));

        keep("seg-flagged", LoggerFactory.getLogger("ua.bookloom.test"));

        assertThat(logDir.resolve(AppPaths.EVIDENCE_LOG_FILE_NAME)).doesNotExist();
    }

    private static void traceFor(final String segment, final Logger logger, final String text) {
        MDC.put("segment", segment);
        try {
            logger.trace(text);
        } finally {
            MDC.remove("segment");
        }
    }

    private static void keep(final String segment, final Logger logger) {
        MDC.put("segment", segment);
        try {
            logger.debug(MarkerFactory.getMarker("EVIDENCE_KEEP"), "segment decided flagged");
        } finally {
            MDC.remove("segment");
        }
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
