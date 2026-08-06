package ua.bookloom.app;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * What the pre-injector launcher already resolved, handed to the JavaFX application that cannot resolve it again.
 *
 * <p><strong>Why a static holder exists here at all.</strong> {@code Application.launch(Class, args)} constructs the
 * application <em>reflectively, through its no-argument constructor</em>. There is no supported way to pass a
 * constructed value into it, and the values in question — the resolved paths and the held single-instance lock —
 * cannot simply be recomputed on the other side: re-resolving is harmless, but re-acquiring the lock is not, since
 * the launcher already holds it and a second acquisition in the same process fails.
 *
 * <p>This is therefore a <em>lifecycle handoff</em>, not dependency injection, and it is shaped to stay that way:
 * write-once ({@link #publish} refuses a second call), read-once ({@link #take} clears the reference), and carrying
 * only what the framework's constructor cannot. Everything downstream of the injector uses constructor injection,
 * which is what {@code java-coding-style.md} is about when it forbids static singletons.
 *
 * @param paths the resolved, created data and log directories
 * @param environment whether this is a development or a packaged production run
 */
public record StartupContext(AppPaths paths, AppEnvironment environment) {

    private static final AtomicReference<StartupContext> PENDING = new AtomicReference<>();

    /** Validates that both halves are present. */
    public StartupContext {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(environment, "environment");
    }

    /**
     * Stores the context for the application about to be launched.
     *
     * @param context the resolved startup state
     * @throws IllegalStateException if a context is already pending, which would mean two launches in one process
     */
    public static void publish(StartupContext context) {
        Objects.requireNonNull(context, "context");
        if (!PENDING.compareAndSet(null, context)) {
            throw new IllegalStateException("a startup context is already pending; launch happens once per process");
        }
    }

    /**
     * Consumes the pending context, clearing it.
     *
     * @return the context published by the launcher
     * @throws IllegalStateException if none was published, which means the application was started without going
     *     through the launcher — and therefore without a lock, a log directory, or a created data folder
     */
    public static StartupContext take() {
        final StartupContext context = PENDING.getAndSet(null);
        if (context == null) {
            throw new IllegalStateException(
                    "no startup context published; the application must be started through Launcher");
        }
        return context;
    }
}
