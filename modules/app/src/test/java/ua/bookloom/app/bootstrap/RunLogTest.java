package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ua.bookloom.util.paths.AppPaths;

/** The run log keeps a launch's INFO lifecycle lines whole, however far the rolling logs have moved past them. */
class RunLogTest {

    private static final ResolvedTraceFile TRACE_ON =
            new ResolvedTraceFile(true, ResolvedLogLevel.Source.ENVIRONMENT, null);
    private static final int ROTATIONS = 6;
    private static final int OLD_RUNS = 12;

    @TempDir
    private Path tempDir;

    @AfterEach
    void restoreLogging() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
            context.reset();
        }
    }

    private static List<Path> runLogs(final Path logDir) throws IOException {
        try (Stream<Path> files = Files.list(logDir)) {
            return files.filter(file -> file.getFileName().toString().startsWith(AppPaths.RUN_LOG_PREFIX))
                    .sorted()
                    .toList();
        }
    }

    private static String onlyRunLogText(final Path logDir) throws IOException {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }
        final List<Path> logs =
                runLogs(logDir).stream().filter(Files::isRegularFile).toList();
        return Files.readString(logs.get(logs.size() - 1));
    }

    // IF the run's first lines lived only in the rolling files, THEN a 9-hour run would have lost its start (names
    // pass, brief, first chapters) by the time anyone asks why it went wrong.
    @Test
    void configure_longRun_runLogKeepsTheFirstInfoLineAfterTheDetailedLogRolledPastIt() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session");
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");
        logger.info("names pass started for the invented book");
        IntStream.range(0, ROTATIONS).forEach(rotation -> {
            logger.trace("filler line {}", rotation);
            traceAppender().rollover();
        });
        logger.info("run summary final accepted=3");

        final String text = onlyRunLogText(logDir);

        assertThat(text)
                .startsWith("# session")
                .contains("names pass started for the invented book")
                .contains("run summary final accepted=3");
        assertThat(Files.readAllLines(logDir.resolve(AppPaths.TRACE_LOG_FILE_NAME)))
                .noneMatch(line -> line.contains("names pass started"));
    }

    // IF DEBUG or TRACE lines reached the run log, THEN book text (prompts, replies) would sit in a file the person
    // did not expect to hold any.
    @Test
    void configure_debugAndTraceLines_neverReachTheRunLog() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session");
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");

        logger.trace("prompt text of a segment");
        logger.debug("segment branch taken");
        logger.warn("segment flagged");

        assertThat(onlyRunLogText(logDir))
                .doesNotContain("prompt text of a segment")
                .doesNotContain("segment branch taken")
                .contains("segment flagged");
    }

    // IF the old run logs were never pruned, THEN the folder would grow with every launch.
    @Test
    void configure_moreThanTenRunLogs_dropsTheOldestFirstAndKeepsTen() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        IntStream.rangeClosed(1, OLD_RUNS).forEach(index -> write(logDir, "202001%02d-000000".formatted(index)));
        Files.writeString(logDir.resolve(AppPaths.RUN_LOG_PREFIX + "20190101-000000.log"), "the oldest");

        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO), TRACE_ON, () -> "# session");

        final List<Path> kept = runLogs(logDir);
        assertThat(kept).hasSize(AppPaths.RUN_LOGS_KEPT);
        assertThat(kept).noneMatch(file -> file.getFileName().toString().contains("20190101"));
    }

    // IF the run log depended on the detailed log, THEN an installed app (detailed log off) would lose the start of a
    // long run; it holds INFO lines only, so it carries no book text and is safe to keep always.
    @Test
    void configure_detailedLogOffAndLevelWarn_runLogStillHoldsInfoLinesAndOrdinaryLogDoesNot() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));

        LoggingBootstrap.configure(logDir, false, resolved(Level.WARN));
        final Logger logger = LoggerFactory.getLogger("ua.bookloom.test");
        logger.info("a lifecycle line");
        logger.debug("a branch line");

        assertThat(onlyRunLogText(logDir)).contains("a lifecycle line").doesNotContain("a branch line");
        assertThat(Files.readString(logDir.resolve("bookloom.log"))).doesNotContain("a lifecycle line");
    }

    // IF the ordinary level decided what the run log gets, THEN a quiet setting would blind it.
    @ParameterizedTest
    @CsvSource({"WARN,true", "WARN,false", "ERROR,true", "ERROR,false"})
    void configure_quietOrdinaryLevel_infoLineOnlyInRunLog(final Level level, final boolean detailed)
            throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        final ResolvedTraceFile trace = new ResolvedTraceFile(detailed, ResolvedLogLevel.Source.ENVIRONMENT, null);

        LoggingBootstrap.configure(logDir, false, resolved(level), trace, () -> "# session");
        LoggerFactory.getLogger("ua.bookloom.test").info("lifecycle marker line");

        assertThat(onlyRunLogText(logDir)).contains("lifecycle marker line");
        assertThat(Files.readString(logDir.resolve("bookloom.log"))).doesNotContain("lifecycle marker line");
    }

    // IF the file name were not logged, THEN a person reading bookloom.log could not tell which run log belongs to it.
    @Test
    void configure_started_logsTheRunLogFileNameOnceAtInfo() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));

        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));
        final String runLogName = runLogs(logDir).get(0).getFileName().toString();

        assertThat(onlyRunLogText(logDir)).containsOnlyOnce("run log file=" + runLogName);
    }

    // IF a run log that cannot be deleted vanished silently, THEN the folder would grow with no trace of why.
    @Test
    void configure_oldRunLogCannotBeDeleted_logsOneWarn() throws IOException {
        final Path logDir = Files.createDirectory(tempDir.resolve("logs"));
        IntStream.rangeClosed(1, OLD_RUNS).forEach(index -> write(logDir, "202001%02d-000000".formatted(index)));
        final Path stuck = logDir.resolve(AppPaths.RUN_LOG_PREFIX + "20200101-000000.log");
        Files.delete(stuck);
        Files.createDirectory(stuck);
        Files.writeString(stuck.resolve("inside"), "a directory with content cannot be deleted");

        LoggingBootstrap.configure(logDir, false, resolved(Level.INFO));

        assertThat(onlyRunLogText(logDir)).containsOnlyOnce("run log housekeeping failed for " + stuck.getFileName());
    }

    private static void write(final Path logDir, final String stamp) {
        try {
            Files.writeString(logDir.resolve(AppPaths.RUN_LOG_PREFIX + stamp + ".log"), "an older run");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
