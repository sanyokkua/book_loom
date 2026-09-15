package ua.bookloom.app.bootstrap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Configures Logback programmatically, once, at startup step 5.
 *
 * <p>There is deliberately <strong>no {@code logback.xml} file appender</strong>. An XML-declared appender binds its
 * path when the logging backend initializes, which happens at the first {@code LoggerFactory.getLogger(...)} call —
 * before the resolved log directory is known. The result is not a crash but something worse: the application runs
 * perfectly and writes its log somewhere nobody looks (DD-39, ADR-0015).
 *
 * <p>This is the project's <strong>only</strong> carve-out for concrete Logback types. Everywhere else logs through
 * the SLF4J facade, and {@code api-is-framework-free} bans {@code ch.qos.logback..} from {@code :api} outright.
 * Programmatic configuration cannot be expressed through the facade — {@link LoggerContext} and
 * {@link RollingFileAppender} are the configuration API — so the carve-out is exactly one class, and it lives here,
 * inside the package ArchUnit polices for pre-logging discipline.
 */
public final class LoggingBootstrap {

    private static final String LOG_FILE_NAME = "bookloom.log";
    private static final String ARCHIVE_PATTERN = "bookloom.%d{yyyy-MM-dd}.%i.log";
    private static final String PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [job=%X{job}] [%thread] %logger{36} - %msg%n";

    private static final String MAX_FILE_SIZE = "10MB";
    private static final String TOTAL_SIZE_CAP = "200MB";
    private static final int MAX_HISTORY_DAYS = 14;

    private LoggingBootstrap() {
        // Static entry point only.
    }

    /**
     * Points logging at the resolved directory and starts the rolling file appender.
     *
     * <p>Must be called before the first {@code LoggerFactory.getLogger(...)} in the process. Nothing on the
     * bootstrap path may hold a static logger, which is what makes that orderable at all, and the ArchUnit rule
     * {@code bootstrap-no-static-logger} is what keeps it true as the package grows.
     *
     * @param logDir the resolved, already-created log directory
     * @param devConsole whether to add a console appender as well; useful from an IDE or Gradle, noise in a
     *     packaged application whose stdout nobody sees
     * @param resolvedLevel immutable level metadata resolved before application startup
     * @return {@code true} when the real Logback binding was configured, {@code false} when the SLF4J provider did
     *     not resolve at all — in which case logging is a no-op and the startup line the boot smoke asserts will be
     *     missing, which is precisely how that smoke detects an unresolved JPMS service binding
     */
    public static boolean configure(Path logDir, boolean devConsole, ResolvedLogLevel resolvedLevel) {
        Objects.requireNonNull(logDir, "logDir");
        Objects.requireNonNull(resolvedLevel, "resolvedLevel");

        final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            // A `requires ch.qos.logback.classic` that satisfies javac can still leave the provider unresolved at
            // runtime, giving SLF4J's no-op factory. Reported rather than thrown: throwing here would replace a
            // diagnosable missing log line with a startup crash whose own message could not be logged.
            return false;
        }

        context.reset();
        final PatternLayoutEncoder encoder = encoder(context);
        final RollingFileAppender<ILoggingEvent> fileAppender = fileAppender(context, logDir, encoder);

        final ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
        root.addAppender(fileAppender);
        context.getLogger("ua.bookloom").setLevel(toLogbackLevel(resolvedLevel.level()));
        if (devConsole) {
            root.addAppender(consoleAppender(context, encoder));
        }
        final ch.qos.logback.classic.Logger bootstrapLogger = context.getLogger(LoggingBootstrap.class);
        bootstrapLogger.setLevel(Level.INFO);
        bootstrapLogger.info("logging configured level={} source={}", resolvedLevel.level(), resolvedLevel.source());
        resolvedLevel.rejectedValue().ifPresent(value -> bootstrapLogger.warn("rejected log level value={}", value));
        return true;
    }

    private static Level toLogbackLevel(org.slf4j.event.Level level) {
        if (level == org.slf4j.event.Level.TRACE) {
            return Level.TRACE;
        }
        if (level == org.slf4j.event.Level.DEBUG) {
            return Level.DEBUG;
        }
        if (level == org.slf4j.event.Level.INFO) {
            return Level.INFO;
        }
        if (level == org.slf4j.event.Level.WARN) {
            return Level.WARN;
        }
        return Level.ERROR;
    }

    private static PatternLayoutEncoder encoder(LoggerContext context) {
        final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(PATTERN);
        encoder.start();
        return encoder;
    }

    private static RollingFileAppender<ILoggingEvent> fileAppender(
            LoggerContext context, Path logDir, PatternLayoutEncoder encoder) {
        final RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setFile(logDir.resolve(LOG_FILE_NAME).toString());
        appender.setEncoder(encoder);

        // Time AND size: time alone lets one runaway day fill a disk, size alone loses the ability to say "the log
        // from Tuesday". The dev/production folder split guarantees two instances never contend for one file,
        // which on Windows would fail rollover on the file lock.
        final SizeAndTimeBasedRollingPolicy<ILoggingEvent> policy = new SizeAndTimeBasedRollingPolicy<>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setFileNamePattern(logDir.resolve(ARCHIVE_PATTERN).toString());
        policy.setMaxFileSize(FileSize.valueOf(MAX_FILE_SIZE));
        policy.setMaxHistory(MAX_HISTORY_DAYS);
        policy.setTotalSizeCap(FileSize.valueOf(TOTAL_SIZE_CAP));
        policy.start();

        appender.setRollingPolicy(policy);
        appender.start();
        return appender;
    }

    private static ConsoleAppender<ILoggingEvent> consoleAppender(LoggerContext context, PatternLayoutEncoder encoder) {
        final ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }
}
