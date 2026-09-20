package ua.bookloom.app.bootstrap;

import java.util.Objects;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.app.AppVersion;
import ua.bookloom.app.BookLoomApplication;
import ua.bookloom.app.StartupContext;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;
import ua.bookloom.util.paths.AppPathsResolver;

/**
 * The process entry point: everything that must happen before the injector, in the order it must happen.
 *
 * <p><strong>It does not extend {@code Application}, and that is not a stylistic choice.</strong> When the main
 * class extends {@code Application}, the JavaFX launcher takes over module-path and toolkit initialization before
 * any application code runs — fragile under jpackage, and impossible to sequence against. It also puts the toolkit
 * ahead of the single-instance lock, so a second launch would boot a UI toolkit and touch the data directory before
 * discovering it is not allowed to run at all.
 *
 * <pre>
 * 1. resolve isDev                  pure function over env + system properties
 * 2. resolve dataDir + logDir       pure function over os.name + home + env
 * 3. createDirectories              first failure that can reach a user (EC-ENV-2)
 * 4. tryLock(dataDir/bookloom.lock) a second launch exits here (EC-ENV-3)
 * 5. configure Logback              publish logDir, THEN start the appender
 * ─────────────────────────────── from here on, logging works ───────────────────────────────
 * </pre>
 *
 * <p>Steps 1–4 emit no log line at all, by construction rather than by discipline: {@code :util} does not require
 * SLF4J, and every class here sits in the package {@code bootstrap-no-static-logger} polices.
 */
public final class Launcher {

    /** A deliberate refusal is not a failure: a second launch has done nothing wrong and reports success. */
    private static final int EXIT_ALREADY_RUNNING = 0;

    private static final int EXIT_STARTUP_FAILED = 1;

    private Launcher() {
        // Entry point only.
    }

    /**
     * Runs the pre-injector startup sequence.
     *
     * @param args command-line arguments, forwarded to the JavaFX application
     */
    public static void main(String[] args) {
        final AppPathsResolver resolver = new AppPathsResolver(System::getenv, System::getProperty);

        // Steps 2 and 3. flatMap short-circuits: if resolution fails there is nothing to create, and `prepare`
        // never sees a value that does not exist.
        final Result<AppPaths> prepared = resolver.resolve().flatMap(resolver::prepare);
        if (prepared.isErr()) {
            StartupFailureDialog.showAndExit(Objects.requireNonNull(prepared.error()), EXIT_STARTUP_FAILED);
            return;
        }
        final AppPaths paths = Objects.requireNonNull(prepared.data());

        // Step 4, before anything can open the database this lock exists to protect.
        final Result<SingleInstanceLock> acquired = SingleInstanceLock.acquire(paths.lockFile());
        if (acquired.isErr()) {
            final AppError error = Objects.requireNonNull(acquired.error());
            final int exitCode = error.code() == ErrorCode.busy ? EXIT_ALREADY_RUNNING : EXIT_STARTUP_FAILED;
            StartupFailureDialog.showAndExit(error, exitCode);
            return;
        }

        // try-with-resources rather than a shutdown hook: `Application.launch` blocks until the last window closes,
        // so the lock is held for exactly the process's useful lifetime and released on the way out. The OS would
        // release it on a crash regardless, which is what keeps a killed process from leaving the app unstartable.
        final SingleInstanceLock lock = Objects.requireNonNull(acquired.data());
        try (lock) {
            // Step 5. Nothing above this line may take a logger; everything below it may.
            final AppEnvironment environment = AppEnvironment.resolve(System::getenv, System::getProperty);
            final ResolvedLogLevel logLevel =
                    LoggingLevelResolver.resolve(System::getenv, System::getProperty, environment);
            LoggingBootstrap.configure(paths.logDir(), environment.isDev(), logLevel);

            warnIfOnNetworkFilesystem(paths);
            logStartup(paths, environment);

            StartupContext.publish(new StartupContext(paths, environment));
            Application.launch(BookLoomApplication.class, args);

            // Reached when the last window closes. The lock is released by the try-with-resources immediately
            // after, so the line is emitted while it is still held — which is the order a reader would expect.
            LoggerFactory.getLogger(Launcher.class).info("app stopped, releasing the single-instance lock");
        }
    }

    /**
     * The first log line of the process, and one of only two consumers DD-50 permits for the version string.
     *
     * <p>It carries the resolved data directory as well as the version, because that is what makes a missing
     * {@code --java-options "-Dbookloom.env=prod"} stamp visible: an image built without it resolves the
     * {@code -Dev} folder and works perfectly, just against the wrong data. The packaging smoke asserts this line
     * names the production folder for exactly that reason.
     *
     * <p>It is also the cheapest possible check that the SLF4J provider really resolved: a {@code requires
     * ch.qos.logback.classic} that satisfies javac can still leave the binding unresolved at runtime, and a no-op
     * logger emits nothing at all.
     */
    static void logStartup(AppPaths paths, AppEnvironment environment) {
        final Logger log = LoggerFactory.getLogger(Launcher.class);
        log.info(
                "app started version={} environment={} dataDir={} logDir={}",
                AppVersion.current(),
                environment,
                paths.dataDir(),
                paths.logDir());
    }

    /**
     * Emits the EC-ENV-8 warning, if the resolver flagged one.
     *
     * <p>The resolver detects the condition but cannot report it: it runs at step 2, and there is no logger until
     * step 5. So the fact travels as data on the resolved value and is spoken for here — its own {@code warn} line
     * rather than a field on the startup line, because a warning buried inside a normal-looking startup message is
     * read straight past, and this one has to survive being skim-read in a support thread.
     *
     * <p>Detected and reported, never acted on: the {@code PRAGMA journal_mode=TRUNCATE} fallback it implies needs
     * a SQLite connection, which does not exist until the local-storage change.
     */
    static void warnIfOnNetworkFilesystem(AppPaths paths) {
        if (!paths.onNetworkFilesystem()) {
            return;
        }
        // Taken here rather than held in a field: a static logger in this class is exactly what rule 8 forbids,
        // because class initialization can happen before step 5.
        final Logger log = LoggerFactory.getLogger(Launcher.class);
        log.warn(
                "data directory {} appears to be on a network filesystem; SQLite WAL locking is unreliable on "
                        + "network shares and local storage is the supported configuration",
                paths.dataDir());
    }
}
