package ua.bookloom.app.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.app.AppLifecycle;
import ua.bookloom.app.AppVersion;
import ua.bookloom.app.CoreModules;
import ua.bookloom.app.StartupContext;
import ua.bookloom.app.cli.ShutdownCancellation;
import ua.bookloom.app.cli.TranslateCommand;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;
import ua.bookloom.util.paths.AppPathsResolver;

/** The file-only command-line bootstrap, ordered like the desktop launch without starting JavaFX. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TranslateLauncher {

    private static final int EXIT_FAILURE = 1;

    /** Starts the command-line process and delegates its status to the operating system. */
    public static void main(String[] args) {
        System.exit(run(args, System::getenv, System::getProperty, System.out));
    }

    /** Runs the command with an argument list and injectable environment, properties and output. */
    public static int run(
            List<String> args,
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            PrintStream out) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");
        Objects.requireNonNull(out, "out");
        try {
            return runBeforeLogging(args, getEnv, getProperty, out);
        } catch (Throwable cause) {
            return printError(startupFailure(cause), out);
        }
    }

    /** Array overload used by {@link #main(String[])} while tests can pass a list without touching process state. */
    public static int run(
            String[] args,
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            PrintStream out) {
        Objects.requireNonNull(args, "args");
        return run(List.of(args), getEnv, getProperty, out);
    }

    private static int runBeforeLogging(
            List<String> args,
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            PrintStream out) {
        final AppEnvironment environment = AppEnvironment.resolve(getEnv, getProperty);
        final AppPathsResolver resolver = new AppPathsResolver(getEnv, getProperty);
        final Result<AppPaths> prepared = resolver.resolve().flatMap(resolver::prepare);
        if (prepared.isErr()) {
            return printError(errorOf(prepared), out);
        }
        final AppPaths paths = dataOf(prepared);
        final Result<SingleInstanceLock> acquired = SingleInstanceLock.acquire(paths.lockFile());
        if (acquired.isErr()) {
            return printError(errorOf(acquired), out);
        }
        final SingleInstanceLock lock = dataOf(acquired);
        try (lock) {
            return runAfterLock(args, getEnv, getProperty, out, paths, environment);
        }
    }

    private static int runAfterLock(
            List<String> args,
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            PrintStream out,
            AppPaths paths,
            AppEnvironment environment) {
        final ResolvedLogLevel level = configureLogging(getEnv, getProperty, paths, environment);
        final Logger log = LoggerFactory.getLogger(TranslateLauncher.class);
        log.info(
                "translate launcher started arguments={} dataDir={} logDir={} level={} levelSource={}",
                args,
                paths.dataDir(),
                paths.logDir(),
                level.level(),
                level.source());
        try {
            final ResolvedReviewMode reviewMode = resolveIgnoredReviewMode(log, getEnv, getProperty);
            final Injector injector =
                    Guice.createInjector(new CoreModules(new StartupContext(paths, environment, reviewMode)));
            final AppLifecycle lifecycle = new AppLifecycle();
            lifecycle.phaseOne(injector);
            lifecycle.phaseTwo(injector);
            final int exit = runWithShutdownHook(injector, args, out);
            log.info("translate launcher exitCode={}", exit);
            return exit;
        } catch (Throwable cause) {
            final AppError error = startupFailure(cause);
            log.error("Unexpected translate launcher failure code={}", error.code(), cause);
            final int exit = printError(error, out);
            log.info("translate launcher exitCode={}", exit);
            return exit;
        }
    }

    /** Configures logging as the desktop launch does, with the detailed log when it is switched on. */
    private static ResolvedLogLevel configureLogging(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppPaths paths,
            AppEnvironment environment) {
        final ResolvedLogLevel level = LoggingLevelResolver.resolve(getEnv, getProperty, environment);
        final ResolvedTraceFile trace = TraceFileResolver.resolve(getEnv, getProperty, environment);
        // The command line opens no window, so its session facts stay empty; the header still names the build.
        final SessionHeader header =
                new SessionHeader(AppVersion.current(), environment, level, trace, getProperty, Map::of);
        LoggingBootstrap.configure(paths.logDir(), false, level, trace, header::render);
        return level;
    }

    /** A run here has no window to pause in, so a configured mode changes nothing; say so once instead of silently. */
    private static ResolvedReviewMode resolveIgnoredReviewMode(
            Logger log, Function<String, @Nullable String> getEnv, Function<String, @Nullable String> getProperty) {
        final ResolvedReviewMode reviewMode = ReviewModeResolver.resolve(getEnv, getProperty);
        final String rejected = reviewMode.rejectedValue();
        if (rejected != null) {
            log.info("review mode ignored by the command line value={}", rejected);
        } else if (reviewMode.source() != ResolvedReviewMode.Source.DEFAULT) {
            log.info(
                    "review mode ignored by the command line value={}",
                    reviewMode.mode().name().toLowerCase(Locale.ROOT));
        }
        return reviewMode;
    }

    /** Lets Ctrl+C cancel the running job or export and wait for it, then removes the hook so a caller can run again. */
    private static int runWithShutdownHook(Injector injector, List<String> args, PrintStream out) {
        final Thread hook = new Thread(injector.getInstance(ShutdownCancellation.class), "translate-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            return injector.getInstance(TranslateCommand.class).run(args, out);
        } finally {
            removeHook(hook);
        }
    }

    private static void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException shuttingDown) {
            // The JVM is already stopping and is running the hook itself; nothing is left to remove.
        }
    }

    private static AppError startupFailure(Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Translation command failed",
                "BookLoom could not start the translation command.",
                null,
                cause);
    }

    private static int printError(AppError error, PrintStream out) {
        out.println(error.title() + ": " + error.message());
        return EXIT_FAILURE;
    }

    private static <T> T dataOf(Result<T> result) {
        return Objects.requireNonNull(result.data(), "successful result data");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "failed result error");
    }
}
