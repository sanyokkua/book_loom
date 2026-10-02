package ua.bookloom.pipeline;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Collects the ERROR lines, and the WARN lines with a stack trace, of a soak run that no injected fault explains: a line whose exception, or any cause of it,
 * carries {@link FaultyModel#INJECTED} is the run doing what it should with a step that threw. At most a hundred are
 * kept; the first one already fails the run.
 */
final class SoakLog extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private static final int KEPT = 100;

    private final Logger logger = (Logger) LoggerFactory.getLogger("ua.bookloom");
    private final List<String> unexpected = new CopyOnWriteArrayList<>();

    static SoakLog attach() {
        final SoakLog log = new SoakLog();
        log.setContext(log.logger.getLoggerContext());
        log.start();
        log.logger.addAppender(log);
        return log;
    }

    @Override
    protected void append(final ILoggingEvent event) {
        // An ERROR line, or any line with a stack trace, that no injected fault explains.
        final boolean serious = event.getLevel().isGreaterOrEqual(Level.ERROR)
                || (event.getLevel().isGreaterOrEqual(Level.WARN) && event.getThrowableProxy() != null);
        if (serious && !injected(event.getThrowableProxy()) && unexpected.size() < KEPT) {
            unexpected.add(event.getLoggerName() + " " + event.getFormattedMessage() + " "
                    + describe(event.getThrowableProxy()));
        }
    }

    List<String> unexpectedErrors() {
        return List.copyOf(unexpected);
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }

    private static boolean injected(final @Nullable IThrowableProxy thrown) {
        return thrown != null && (FaultyModel.INJECTED.equals(thrown.getMessage()) || injected(thrown.getCause()));
    }

    private static String describe(final @Nullable IThrowableProxy thrown) {
        return thrown == null ? "" : thrown.getClassName() + ": " + thrown.getMessage();
    }
}
