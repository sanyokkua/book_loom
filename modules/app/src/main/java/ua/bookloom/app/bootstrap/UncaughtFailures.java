package ua.bookloom.app.bootstrap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

/**
 * The one process-wide handler for a throwable no code caught. Without it the JVM prints such a failure to standard
 * error only, so a failure on the JavaFX thread (an event handler or a list listener that threw) never reached the
 * log a person shares. Installed once logging is configured; it takes its logger at call time, like every class on
 * the bootstrap path.
 *
 * <p>One failure is known and not ours: JavaFX on macOS throws {@value #DETACHED_AT_QUIT} from its later-invocation
 * dispatcher when a queued invocation races the toolkit's exit. Once the launcher says the application has stopped,
 * exactly that message is written as one WARN line without its stack; every other failure, and that one before the
 * stop, stays an ERROR with its stack.
 */
final class UncaughtFailures implements Thread.UncaughtExceptionHandler {

    /** The message of the framework's quit-time race, exactly as macOS's glass toolkit throws it. */
    static final String DETACHED_AT_QUIT = "Main Java thread is detached.";

    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private UncaughtFailures() {}

    /**
     * Makes a new handler the default of every thread that has no handler of its own.
     *
     * @return the handler installed, which the launcher tells when the application stopped
     */
    static UncaughtFailures install() {
        final UncaughtFailures handler = new UncaughtFailures();
        Thread.setDefaultUncaughtExceptionHandler(handler);
        LoggerFactory.getLogger(UncaughtFailures.class).debug("uncaught-failure handler installed");
        return handler;
    }

    /** Marks that the application has stopped, so the framework's quit-time race is expected from now on. */
    void shutdownStarted() {
        shuttingDown.set(true);
        LoggerFactory.getLogger(UncaughtFailures.class).debug("uncaught-failure handler: the application stopped");
    }

    /** Writes the failure, with its thread and its stack, as one ERROR line, unless it is the known quit race. */
    @Override
    public void uncaughtException(final Thread thread, final Throwable failure) {
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(failure, "failure");
        if (shuttingDown.get() && DETACHED_AT_QUIT.equals(failure.getMessage())) {
            LoggerFactory.getLogger(UncaughtFailures.class)
                    .warn(
                            "JavaFX quit-time race on thread {} after the application stopped: {}",
                            thread.getName(),
                            DETACHED_AT_QUIT);
            return;
        }
        LoggerFactory.getLogger(UncaughtFailures.class)
                .error("uncaught failure on thread {}", thread.getName(), failure);
    }
}
