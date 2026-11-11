package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** A failure no code caught reaches the log, with its thread and stack, instead of standard error only. */
class UncaughtFailuresTest {

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(UncaughtFailures.class);
    private Thread.@Nullable UncaughtExceptionHandler previous;

    @BeforeEach
    void attach() {
        previous = Thread.getDefaultUncaughtExceptionHandler();
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logged);
        Thread.setDefaultUncaughtExceptionHandler(previous);
    }

    // IF nothing were installed, THEN a throw on the FX thread would be printed to standard error and never logged.
    @Test
    void install_threadWithoutHandlerThrows_logsOneErrorWithThreadNameAndCause() throws InterruptedException {
        UncaughtFailures.install();
        final IllegalStateException failure = new IllegalStateException("invented failure");

        final Thread thread = new Thread(
                () -> {
                    throw failure;
                },
                "invented-thread");
        thread.start();
        thread.join();

        assertThat(logged.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo("uncaught failure on thread invented-thread");
                    assertThat(event.getThrowableProxy()).isNotNull();
                    assertThat(event.getThrowableProxy().getMessage()).isEqualTo("invented failure");
                });
    }

    // JavaFX on macOS throws this from its dispatcher thread when a later-invocation races the toolkit's exit: once the
    // app is shutting down it is that known race, not a defect of ours, so it is one WARN line.
    @Test
    void handler_detachedMainThreadAfterShutdownStarted_logsOneWarn() {
        final UncaughtFailures handler = UncaughtFailures.install();
        handler.shutdownStarted();

        handler.uncaughtException(
                new Thread(() -> {}, "InvokeLaterDispatcher"), new RuntimeException("Main Java thread is detached."));

        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    // The same message before shutdown is not the quit race: it stays an ERROR with its stack.
    @Test
    void handler_detachedMainThreadBeforeShutdown_logsOneError() {
        final UncaughtFailures handler = UncaughtFailures.install();

        handler.uncaughtException(
                new Thread(() -> {}, "InvokeLaterDispatcher"), new RuntimeException("Main Java thread is detached."));

        assertOneErrorWithStack();
    }

    // Any other failure during shutdown is still a failure.
    @Test
    void handler_otherFailureAfterShutdownStarted_logsOneError() {
        final UncaughtFailures handler = UncaughtFailures.install();
        handler.shutdownStarted();

        handler.uncaughtException(
                new Thread(() -> {}, "InvokeLaterDispatcher"), new IllegalStateException("invented failure"));

        assertOneErrorWithStack();
    }

    private void assertOneErrorWithStack() {
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
    }
}
