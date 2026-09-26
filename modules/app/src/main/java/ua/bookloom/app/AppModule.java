package ua.bookloom.app;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.BuildVersion;
import ua.bookloom.ui.IoExecutor;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The application-scoped bindings: the two executors and the already-resolved startup state.
 *
 * <p>Constructor injection only. Nothing here is a static holder or a service locator — the one static handoff in
 * this module ({@link StartupContext}) exists because the JavaFX launcher constructs the application reflectively,
 * and it stops at this boundary: everything the injector builds receives its dependencies as constructor arguments.
 *
 * <p>Two executors, because they are for different work. The {@link BackgroundExecutor} pool runs bounded, cancellable
 * application work off the FX Application Thread; it is <strong>daemon</strong>, so a lingering task can never keep
 * the JVM alive after the last window closes. The {@link IoExecutor} executor uses virtual threads, which suit blocking I/O
 * fan-out and are wrong for anything CPU-bound or gated — inference in particular is single-flight and must not be
 * fanned out at all (`threading-concurrency.md`).
 */
public final class AppModule extends AbstractModule {

    private final StartupContext startup;

    /**
     * Creates the module over state the launcher already resolved.
     *
     * @param startup the resolved paths and environment, from the pre-injector launcher
     */
    public AppModule(StartupContext startup) {
        this.startup = Objects.requireNonNull(startup, "startup");
    }

    @Override
    protected void configure() {
        // Bound as instances rather than provided: both were resolved before the injector existed, and re-deriving
        // either here would give a second answer to a question already settled at startup.
        bind(AppPaths.class).toInstance(startup.paths());
        bind(AppEnvironment.class).toInstance(startup.environment());
        bind(StartupContext.class).toInstance(startup);
        // The one read of the build version: the launcher's startup line and the About dialog must report the same
        // string, and `:ui` cannot see this module's reader, so the value crosses the edge as a binding.
        bind(String.class).annotatedWith(BuildVersion.class).toInstance(AppVersion.current());
    }

    /**
     * The daemon pool for background application work.
     *
     * @return a fixed-size daemon executor sized to the machine
     */
    @Provides
    @Singleton
    @BackgroundExecutor
    ExecutorService backgroundExecutor() {
        final AtomicLong counter = new AtomicLong();
        final ThreadFactory factory = runnable -> {
            final Thread thread = new Thread(runnable, "bookloom-bg-" + counter.incrementAndGet());
            // Daemon so a stuck background task cannot outlive the last window and leave an invisible process
            // holding the single-instance lock — which would make the application unstartable until a kill.
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), factory);
    }

    /**
     * The virtual-thread executor for blocking I/O fan-out.
     *
     * @return a virtual-thread-per-task executor
     */
    @Provides
    @Singleton
    @IoExecutor
    ExecutorService ioExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
