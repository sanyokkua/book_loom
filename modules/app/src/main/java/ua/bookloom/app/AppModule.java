package ua.bookloom.app;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.app.bootstrap.ResolvedTraceFile;
import ua.bookloom.app.bootstrap.ReviewModeSource;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.BuildVersion;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.IoExecutor;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The application-scoped bindings: the two executors, the already-resolved startup state and the review mode.
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
    private final EnvironmentVariables environment;

    /**
     * Creates the module over state the launcher already resolved.
     *
     * @param startup the resolved paths and environment, from the pre-injector launcher
     * @param environment the one variable lookup, shared with the request headers
     */
    AppModule(StartupContext startup, EnvironmentVariables environment) {
        this.startup = Objects.requireNonNull(startup, "startup");
        this.environment = Objects.requireNonNull(environment, "environment");
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
        // The same session instance the detailed log's header reads, so what the window records is what a rotated
        // file names; and the log settings the launcher resolved before logging existed, for the About dialog.
        bind(SessionInfo.class).toInstance(startup.session());
        bind(EnvironmentVariables.class).toInstance(environment);
        bind(AppRestart.class).to(NoOpAppRestart.class).in(Singleton.class);
        bind(DiagnosticLog.class).toInstance(diagnosticLog());
        bind(ReviewModeChoice.class).to(ReviewModeSource.class);
    }

    private DiagnosticLog diagnosticLog() {
        final String decidedBy =
                switch (startup.detailedLogSource()) {
                    case ENVIRONMENT -> "BOOKLOOM_TRACE_FILE";
                    case PROPERTY -> "bookloom.trace.file";
                    case MARKER, DEFAULT -> null;
                };
        return new DiagnosticLog(
                startup.paths().logDir(),
                startup.detailedLog(),
                startup.paths().dataDir().resolve(AppPaths.DETAILED_LOG_MARKER),
                decidedBy,
                startup.detailedLog() && startup.detailedLogSource() == ResolvedTraceFile.Source.DEFAULT);
    }

    /**
     * The review mode a run starts with, read at each request so a change in Settings reaches the next run.
     *
     * @param source the launch flag, else the stored choice
     * @return the mode now; deliberately unscoped, so a {@code Provider<ReviewMode>} asks again each time
     */
    @Provides
    ReviewMode reviewMode(final ReviewModeSource source) {
        return source.current();
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
