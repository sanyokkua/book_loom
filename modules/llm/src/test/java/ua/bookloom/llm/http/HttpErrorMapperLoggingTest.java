package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** A provider's failure body reaches the log at TRACE only, never at DEBUG or above. */
class HttpErrorMapperLoggingTest {

    private static final List<String> LOGGERS =
            List.of("ua.bookloom.llm.http.HttpErrorMapper", "ua.bookloom.llm.http.ErrorRows");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Map<Logger, @Nullable Level> previousLevels = new HashMap<>();

    // The provider's sentence can quote the book, so it is written at TRACE only, never at DEBUG or above.
    @Test
    void map_unknownBody_logsBodyOnlyAtTrace() {
        final String body = "{\"error\":\"the lantern-keeper refused politely\"}";
        captureAtTrace();

        HttpErrorMapper.map(
                new HttpReply(400, Map.of(), body),
                config(ProviderKind.OPENAI_COMPATIBLE),
                HttpErrorMapper.CallPurpose.CHAT,
                "test-model");

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.TRACE)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(line -> assertThat(line).contains("lantern-keeper refused politely"));
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneSatisfy(line -> assertThat(line).contains("lantern-keeper"))
                .anySatisfy(line -> assertThat(line).contains("no error row matched"));
    }

    private void captureAtTrace() {
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        LOGGERS.forEach(name -> {
            final Logger logger = (Logger) LoggerFactory.getLogger(name);
            previousLevels.put(logger, logger.getLevel());
            logger.setLevel(Level.TRACE);
            logger.addAppender(appender);
        });
    }

    @AfterEach
    void releaseCapture() {
        previousLevels.forEach((logger, level) -> {
            logger.detachAppender(appender);
            logger.setLevel(level);
        });
        previousLevels.clear();
        appender.stop();
    }

    private static ProviderConfig config(ProviderKind kind) {
        return new ProviderConfig(
                "test-provider",
                kind,
                URI.create("http://provider.test:1234/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }
}
