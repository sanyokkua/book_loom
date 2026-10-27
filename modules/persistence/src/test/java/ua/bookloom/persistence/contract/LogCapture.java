package ua.bookloom.persistence.contract;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Every {@code ua.bookloom} line, down to TRACE, written while it is open; restores the level when closed. */
final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final @Nullable Level savedLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(final Logger logger) {
        this.logger = logger;
        this.savedLevel = logger.getLevel();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    static LogCapture start() {
        return new LogCapture((Logger) LoggerFactory.getLogger("ua.bookloom"));
    }

    /** The captured lines, each formatted with its arguments and the message of any throwable. */
    String text() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        + (event.getThrowableProxy() == null
                                ? ""
                                : " " + event.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(savedLevel);
        appender.stop();
    }
}
