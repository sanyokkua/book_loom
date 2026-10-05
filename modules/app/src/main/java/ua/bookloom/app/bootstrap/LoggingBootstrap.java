package ua.bookloom.app.bootstrap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.FileSize;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import ua.bookloom.util.paths.AppPaths;

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
 * {@link RollingFileAppender} are the configuration API — so the carve-out is exactly one class (with its two nested
 * helpers), and it lives here, inside the package ArchUnit polices for pre-logging discipline.
 *
 * <p><strong>Two files.</strong> {@code bookloom.log} keeps the resolved level. When the detailed log is on,
 * {@code bookloom-trace.log} receives every BookLoom line down to TRACE — book text included — size-bounded and
 * gzip-rotated, with a session header at the top of each file. Raising the BookLoom loggers to TRACE for the second
 * file would flood the first, so the ordinary appenders then filter BookLoom lines back to the resolved level; third
 * party loggers stay at WARN in both.
 */
public final class LoggingBootstrap {

    /** The name the detailed-log appender is registered under, so a test can force its rollover. */
    static final String TRACE_APPENDER_NAME = "TRACE_FILE";

    private static final String BOOKLOOM_LOGGERS = "ua.bookloom";
    private static final String LOG_FILE_NAME = "bookloom.log";
    private static final String ARCHIVE_PATTERN = "bookloom.%d{yyyy-MM-dd}.%i.log";
    private static final String PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [job=%X{job} segment=%X{segment}] [%thread] %logger{36} - %msg%n";

    // One line per event: a line break inside a message (a prompt, a reply) is written as a visible mark instead, so
    // a line-oriented reader (grep, a log viewer) never mistakes a continuation for a new event.
    private static final String TRACE_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [job=%X{job} segment=%X{segment}]"
            + " [%thread] %logger{36} - %replace(%msg){'\\r?\\n', ' ⏎ '}%n";
    private static final String TRACE_ARCHIVE_PATTERN = AppPaths.TRACE_LOG_PREFIX + ".%i.log.gz";

    private static final String MAX_FILE_SIZE = "10MB";
    private static final String TOTAL_SIZE_CAP = "200MB";
    private static final int MAX_HISTORY_DAYS = 14;

    // 20 MB active plus four gzip archives: a whole long run's TRACE in a bounded footprint.
    private static final String TRACE_MAX_FILE_SIZE = "20MB";
    private static final int TRACE_ARCHIVES = 4;

    private LoggingBootstrap() {
        // Static entry point only.
    }

    /**
     * Points logging at the resolved directory with only the ordinary file; the detailed log stays off.
     *
     * @param logDir the resolved, already-created log directory
     * @param devConsole whether to add a console appender as well
     * @param resolvedLevel immutable level metadata resolved before application startup
     * @return {@code true} when the real Logback binding was configured, {@code false} when the SLF4J provider did
     *     not resolve at all
     * @see #configure(Path, boolean, ResolvedLogLevel, ResolvedTraceFile, Supplier)
     */
    public static boolean configure(Path logDir, boolean devConsole, ResolvedLogLevel resolvedLevel) {
        final ResolvedTraceFile off = new ResolvedTraceFile(false, ResolvedLogLevel.Source.DEFAULT, null);
        return configure(logDir, devConsole, resolvedLevel, off, () -> "");
    }

    /**
     * Points logging at the resolved directory and starts the rolling file appenders.
     *
     * <p>Must be called before the first {@code LoggerFactory.getLogger(...)} in the process. Nothing on the
     * bootstrap path may hold a static logger, which is what makes that orderable at all, and the ArchUnit rule
     * {@code bootstrap-no-static-logger} is what keeps it true as the package grows.
     *
     * @param logDir the resolved, already-created log directory
     * @param devConsole whether to add a console appender as well; useful from an IDE or Gradle, noise in a
     *     packaged application whose stdout nobody sees
     * @param resolvedLevel immutable level metadata resolved before application startup
     * @param trace whether the detailed log is written
     * @param header renders the block written at the top of each detailed-log file; asked again after every rotation
     * @return {@code true} when the real Logback binding was configured, {@code false} when the SLF4J provider did
     *     not resolve at all — in which case logging is a no-op and the startup line the boot smoke asserts will be
     *     missing, which is precisely how that smoke detects an unresolved JPMS service binding
     */
    public static boolean configure(
            Path logDir,
            boolean devConsole,
            ResolvedLogLevel resolvedLevel,
            ResolvedTraceFile trace,
            Supplier<String> header) {
        Objects.requireNonNull(logDir, "logDir");
        Objects.requireNonNull(resolvedLevel, "resolvedLevel");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(header, "header");

        final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            // A `requires ch.qos.logback.classic` that satisfies javac can still leave the provider unresolved at
            // runtime, giving SLF4J's no-op factory. Reported rather than thrown: throwing here would replace a
            // diagnosable missing log line with a startup crash whose own message could not be logged.
            return false;
        }

        context.reset();
        final Level ordinary = toLogbackLevel(resolvedLevel.level());
        final ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
        attachOrdinary(context, root, logDir, devConsole, trace.enabled() ? ordinary : null);
        if (trace.enabled()) {
            root.addAppender(traceAppender(context, logDir, header));
            root.addAppender(EvidenceAppender.create(context, logDir, headerEncoder(context, header)));
        }
        context.getLogger(BOOKLOOM_LOGGERS).setLevel(trace.enabled() ? Level.TRACE : ordinary);
        final ch.qos.logback.classic.Logger bootstrapLogger = context.getLogger(LoggingBootstrap.class);
        bootstrapLogger.setLevel(Level.INFO);
        bootstrapLogger.info(
                "logging configured level={} source={} detailedLog={} detailedLogSource={}",
                resolvedLevel.level(),
                resolvedLevel.source(),
                trace.enabled() ? "on" : "off",
                trace.source());
        warnIfRejected(bootstrapLogger, resolvedLevel, trace);
        return true;
    }

    /**
     * Adds the ordinary file appender, and the console in a development run.
     *
     * @param threshold the level BookLoom lines must reach to be written there, or {@code null} when the BookLoom
     *     loggers already sit at that level and no filter is needed
     */
    private static void attachOrdinary(
            LoggerContext context,
            ch.qos.logback.classic.Logger root,
            Path logDir,
            boolean devConsole,
            @Nullable Level threshold) {
        final PatternLayoutEncoder encoder = encoder(context);
        root.addAppender(filtered(fileAppender(context, logDir, encoder), threshold));
        if (devConsole) {
            root.addAppender(filtered(consoleAppender(context, encoder), threshold));
        }
    }

    private static Appender<ILoggingEvent> filtered(Appender<ILoggingEvent> appender, @Nullable Level threshold) {
        if (threshold != null) {
            final BookloomThreshold filter = new BookloomThreshold(threshold);
            filter.setContext(appender.getContext());
            filter.start();
            appender.addFilter(filter);
        }
        return appender;
    }

    private static void warnIfRejected(
            ch.qos.logback.classic.Logger logger, ResolvedLogLevel resolvedLevel, ResolvedTraceFile trace) {
        final String rejectedValue = resolvedLevel.rejectedValue();
        if (rejectedValue != null) {
            logger.warn("rejected log level value={}", rejectedValue);
        }
        final String rejectedTrace = trace.rejectedValue();
        if (rejectedTrace != null) {
            logger.warn(
                    "rejected detailed log switch value={}; using {}", rejectedTrace, trace.enabled() ? "on" : "off");
        }
    }

    // An if-chain, not a switch: a switch over the SLF4J enum compiles to a synthetic class whose static initializer
    // touches SLF4J, which the bootstrap-no-static-logger rule forbids on this path.
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

    /**
     * The detailed log: size-only rotation into a fixed window of gzip archives, because what matters when sharing
     * it is "the last N megabytes of this run", not a calendar day.
     */
    private static RollingFileAppender<ILoggingEvent> traceAppender(
            LoggerContext context, Path logDir, Supplier<String> header) {
        final RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setName(TRACE_APPENDER_NAME);
        appender.setFile(logDir.resolve(AppPaths.TRACE_LOG_FILE_NAME).toString());
        appender.setEncoder(headerEncoder(context, header));

        final FixedWindowRollingPolicy rolling = new FixedWindowRollingPolicy();
        rolling.setContext(context);
        rolling.setParent(appender);
        rolling.setFileNamePattern(logDir.resolve(TRACE_ARCHIVE_PATTERN).toString());
        rolling.setMinIndex(1);
        rolling.setMaxIndex(TRACE_ARCHIVES);
        rolling.start();

        final SizeBasedTriggeringPolicy<ILoggingEvent> trigger = new SizeBasedTriggeringPolicy<>();
        trigger.setContext(context);
        trigger.setMaxFileSize(FileSize.valueOf(TRACE_MAX_FILE_SIZE));
        trigger.start();

        appender.setRollingPolicy(rolling);
        appender.setTriggeringPolicy(trigger);
        appender.start();
        return appender;
    }

    private static LayoutWrappingEncoder<ILoggingEvent> headerEncoder(LoggerContext context, Supplier<String> header) {
        final HeaderLayout layout = new HeaderLayout(header);
        layout.setContext(context);
        layout.setPattern(TRACE_PATTERN);
        layout.start();
        final LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.start();
        return encoder;
    }

    private static ConsoleAppender<ILoggingEvent> consoleAppender(LoggerContext context, PatternLayoutEncoder encoder) {
        final ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }

    /**
     * A pattern layout whose file header is rendered when the file is opened, not when the layout is built.
     *
     * <p>Logback writes the encoder's header bytes each time an output stream is set — at start and again after every
     * rollover — so asking the supplier there is what puts the session as it stands at the top of each file.
     */
    private static final class HeaderLayout extends PatternLayout {

        private final Supplier<String> header;

        HeaderLayout(Supplier<String> header) {
            this.header = header;
        }

        @Override
        public @Nullable String getFileHeader() {
            final String rendered = header.get();
            return rendered.isEmpty() ? null : rendered;
        }
    }

    /**
     * Keeps a BookLoom line out of an ordinary appender below the resolved level, once the BookLoom loggers sit at
     * TRACE for the detailed log. Lines of other loggers pass: the root's WARN already bounds them. The bootstrap's
     * own logger is exempt, because its "logging configured" line is always written, as before.
     */
    private static final class BookloomThreshold extends Filter<ILoggingEvent> {

        private final Level threshold;

        BookloomThreshold(Level threshold) {
            this.threshold = threshold;
        }

        @Override
        public FilterReply decide(ILoggingEvent event) {
            final String name = event.getLoggerName();
            final boolean bookloom = name.equals(BOOKLOOM_LOGGERS) || name.startsWith(BOOKLOOM_LOGGERS + ".");
            final boolean exempt = !bookloom || name.equals(LoggingBootstrap.class.getName());
            return exempt || event.getLevel().isGreaterOrEqual(threshold) ? FilterReply.NEUTRAL : FilterReply.DENY;
        }
    }
}
