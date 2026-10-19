package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** A broken image throws an Error while the production injector is built; the smoke must say so, not hang. */
class SmokeInjectorFailureTest {

    private static final Duration BOUND = Duration.ofSeconds(10);
    private static final Executor OTHER_THREAD =
            task -> Thread.ofPlatform().daemon().start(task);

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BookLoomApplication.class);

    @BeforeEach
    void watch() {
        logger.setLevel(Level.INFO);
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void unwatch() {
        logger.detachAppender(log);
        log.stop();
    }

    @Test
    void smokeInjector_errorOnTheBuildingThread_failsFastWithTheResolveAllLine() {
        assertTimeoutPreemptively(
                BOUND,
                () -> assertThatThrownBy(() -> BookLoomApplication.smokeBuild(OTHER_THREAD, () -> {
                            throw new NoClassDefFoundError("ua/bookloom/missing/Port");
                        }))
                        .isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(NoClassDefFoundError.class));

        assertThat(log.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("resolve-all FAILED: injector build: NoClassDefFoundError");
    }

    @Test
    void smokeInjector_builtInjector_isReturned() {
        final Injector built = com.google.inject.Guice.createInjector();

        assertThat(assertTimeoutPreemptively(BOUND, () -> BookLoomApplication.smokeBuild(OTHER_THREAD, () -> built)))
                .isSameAs(built);
    }
}
