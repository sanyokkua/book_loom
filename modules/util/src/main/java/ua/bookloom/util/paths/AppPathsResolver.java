package ua.bookloom.util.paths;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * Resolves where this process keeps its data and its logs, and creates those directories.
 *
 * <p>Startup steps 1–3. It runs before the injector exists and before Logback is configured, so it is constructed
 * directly rather than injected, and it cannot log: a failure is returned as {@code Result.err}, never thrown and
 * never written to a logger that does not exist yet.
 *
 * <p><strong>Nothing here calls {@code System.getenv} or {@code System.getProperty}.</strong> Both arrive as
 * functions, which is the seam that makes all three operating systems and both environments testable from one
 * machine: {@code os.name=Windows 11} plus {@code LOCALAPPDATA=C:\Users\x\AppData\Local} is a two-entry map, not a
 * CI runner.
 *
 * <p>The work is split in two on purpose. {@link #resolve()} is a pure decision — which base directory, which
 * app-name segment, which layout — and touches no filesystem. {@link #prepare(AppPaths)} performs the I/O that
 * decision implies. Keeping them apart is what lets the per-OS matrix be asserted without creating a single folder,
 * and what lets the creation failure (EC-ENV-2) be tested without simulating an operating system.
 */
public final class AppPathsResolver {

    /** Explicit override; when absolute it becomes the data dir verbatim, bypassing the per-OS layout (EC-ENV-9). */
    private static final String DATA_DIR_OVERRIDE = "BOOKLOOM_DATA_DIR";

    private static final String LOCALAPPDATA = "LOCALAPPDATA";
    private static final String USERPROFILE = "USERPROFILE";
    private static final String XDG_DATA_HOME = "XDG_DATA_HOME";
    private static final String XDG_STATE_HOME = "XDG_STATE_HOME";
    private static final String USER_HOME = "user.home";

    private static final String APP_NAME = "BookLoom";
    private static final String APP_NAME_DEV = "BookLoom-Dev";
    private static final String APP_NAME_POSIX = "bookloom";
    private static final String APP_NAME_POSIX_DEV = "bookloom-dev";

    private static final String LOG_SUBDIR = "logs";

    /** Filesystem type names that indicate a network share; WAL locking is unreliable on all of them. */
    private static final Set<String> NETWORK_FILESYSTEMS =
            Set.of("nfs", "nfs4", "cifs", "smbfs", "smb", "smb2", "afpfs", "webdav", "fuse.sshfs", "9p");

    private final Function<String, @Nullable String> getEnv;
    private final Function<String, @Nullable String> getProperty;

    /**
     * Creates a resolver over the given environment seams.
     *
     * @param getEnv reads an environment variable, returning {@code null} when unset
     * @param getProperty reads a system property, returning {@code null} when unset
     */
    public AppPathsResolver(Function<String, @Nullable String> getEnv, Function<String, @Nullable String> getProperty) {
        this.getEnv = Objects.requireNonNull(getEnv, "getEnv");
        this.getProperty = Objects.requireNonNull(getProperty, "getProperty");
    }

    /**
     * Decides the data and log directories. Touches no filesystem.
     *
     * @return the resolved paths, or a {@code validation} failure when no usable home exists (EC-ENV-7)
     */
    public Result<AppPaths> resolve() {
        final OsFamily family = OsFamily.from(getProperty);
        return layout().flatMap(layout -> toPaths(family, layout));
    }

    /**
     * The decision alone, as platform-shaped strings, before any {@code Path} is built.
     *
     * <p>Package-private because it exists to be asserted. It is what makes the claim "all three operating systems
     * are testable on one machine" true rather than aspirational: {@link #resolve()} must convert to {@link Path},
     * and a Windows path on a Unix JVM is not a path at all, so the layer that can be checked cross-platform is this
     * one.
     *
     * @return the resolved layout, or a {@code validation} failure when no usable home exists
     */
    Result<PathLayout> layout() {
        return layout(OsFamily.from(getProperty));
    }

    /**
     * Creates the resolved directories and probes the data directory's filesystem.
     *
     * <p>Idempotent: an existing directory is left alone. The network-filesystem probe (EC-ENV-8) is recorded on the
     * returned value and <em>not</em> acted on — the {@code PRAGMA journal_mode=TRUNCATE} fallback it implies needs a
     * SQLite connection, which does not exist until the local-storage change. A probe that itself fails is not a
     * startup failure: an unknown filesystem type means "assume local", not "refuse to start".
     *
     * @param paths the resolved paths from {@link #resolve()}
     * @return the paths with the probe result attached, or a {@code validation} failure when creation fails
     *     (EC-ENV-2)
     */
    public Result<AppPaths> prepare(AppPaths paths) {
        Objects.requireNonNull(paths, "paths");
        try {
            Files.createDirectories(paths.dataDir());
            Files.createDirectories(paths.logDir());
        } catch (IOException | SecurityException e) {
            // No `details`: a filesystem path is not on the safe-details allowlist, and the allowlist is the
            // allowlist. The path is not lost — the IOException carries it, and `cause` is exactly the channel for
            // detail that belongs in a log but not on a screen.
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Cannot create application folders",
                    "BookLoom could not create its data or log folder, so it cannot start.",
                    null,
                    e));
        }
        return Result.ok(paths.withNetworkFilesystem(isOnNetworkFilesystem(paths.dataDir())));
    }

    private Result<PathLayout> layout(OsFamily family) {
        final String override = getEnv.apply(DATA_DIR_OVERRIDE);
        if (family.isAbsolute(override)) {
            final String dataDir = Objects.requireNonNull(override).trim();
            return Result.ok(new PathLayout(dataDir, family.join(dataDir, LOG_SUBDIR)));
        }
        final boolean dev = AppEnvironment.resolve(getEnv, getProperty).isDev();
        return switch (family) {
            case WINDOWS -> windowsLayout(dev);
            case MACOS -> macOsLayout(dev);
            case LINUX -> linuxLayout(dev);
        };
    }

    /**
     * Windows: {@code %LOCALAPPDATA%\BookLoom\} with logs in a {@code logs\} child.
     *
     * <p>{@code %LOCALAPPDATA%} and never {@code %APPDATA%}: the WAL database is a large, memory-mapped, machine-local
     * file, and a roaming profile would both risk corrupting it and bloat profile sync. The two fallbacks are
     * EC-ENV-6 — resolution must not dead-end because one environment variable is unset.
     */
    private Result<PathLayout> windowsLayout(boolean dev) {
        final String base = firstAbsolute(
                OsFamily.WINDOWS,
                getEnv.apply(LOCALAPPDATA),
                appDataLocal(getEnv.apply(USERPROFILE)),
                appDataLocal(getProperty.apply(USER_HOME)));
        if (base == null) {
            return noUsableHome();
        }
        final String dataDir = OsFamily.WINDOWS.join(base, dev ? APP_NAME_DEV : APP_NAME);
        return Result.ok(new PathLayout(dataDir, OsFamily.WINDOWS.join(dataDir, LOG_SUBDIR)));
    }

    /** macOS: data under {@code ~/Library/Application Support/}, logs under the separate {@code ~/Library/Logs/}. */
    private Result<PathLayout> macOsLayout(boolean dev) {
        final String home = firstAbsolute(OsFamily.MACOS, getProperty.apply(USER_HOME));
        if (home == null) {
            return noUsableHome();
        }
        final String appName = dev ? APP_NAME_DEV : APP_NAME;
        return Result.ok(new PathLayout(
                OsFamily.MACOS.join(home, "Library", "Application Support", appName),
                OsFamily.MACOS.join(home, "Library", "Logs", appName)));
    }

    /**
     * Linux: XDG base directories, with logs under the <em>state</em> dir rather than the data dir.
     *
     * <p>A blank or relative {@code XDG_*} value is ignored in favour of the {@code ~/.local/...} fallback, per the
     * Base Directory specification (EC-ENV-1).
     */
    private Result<PathLayout> linuxLayout(boolean dev) {
        final String home = firstAbsolute(OsFamily.LINUX, getProperty.apply(USER_HOME));
        if (home == null) {
            return noUsableHome();
        }
        final String appName = dev ? APP_NAME_POSIX_DEV : APP_NAME_POSIX;
        final String dataHome = firstAbsoluteOr(
                OsFamily.LINUX, OsFamily.LINUX.join(home, ".local", "share"), getEnv.apply(XDG_DATA_HOME));
        final String stateHome = firstAbsoluteOr(
                OsFamily.LINUX, OsFamily.LINUX.join(home, ".local", "state"), getEnv.apply(XDG_STATE_HOME));
        return Result.ok(new PathLayout(
                OsFamily.LINUX.join(dataHome, appName), OsFamily.LINUX.join(stateHome, appName, LOG_SUBDIR)));
    }

    private Result<AppPaths> toPaths(OsFamily family, PathLayout layout) {
        final Path dataDir = Path.of(layout.dataDir());
        if (!dataDir.isAbsolute()) {
            // Only reachable when the injected os.name names a different platform than the JVM is running on — a
            // test feeding Windows values to a Unix JVM. Reported rather than thrown so the envelope holds at this
            // boundary too; production cannot enter this branch, because there the host is the target.
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unusable application folder",
                    "The resolved data folder is not absolute on this platform (" + family + ").",
                    null,
                    null));
        }
        return Result.ok(AppPaths.of(dataDir, Path.of(layout.logDir())));
    }

    private static <T> Result<T> noUsableHome() {
        return Result.err(AppError.of(
                ErrorCode.validation,
                "No usable home folder",
                "BookLoom could not determine where to store its data, so it will not start. "
                        + "Set BOOKLOOM_DATA_DIR to choose a folder explicitly.",
                null,
                null));
    }

    private @Nullable String appDataLocal(@Nullable String base) {
        return base == null ? null : OsFamily.WINDOWS.join(base, "AppData", "Local");
    }

    private static @Nullable String firstAbsolute(OsFamily family, @Nullable String... candidates) {
        for (final String candidate : candidates) {
            if (family.isAbsolute(candidate)) {
                return Objects.requireNonNull(candidate).trim();
            }
        }
        return null;
    }

    private static String firstAbsoluteOr(OsFamily family, String fallback, @Nullable String candidate) {
        final String absolute = firstAbsolute(family, candidate);
        return absolute == null ? fallback : absolute;
    }

    private boolean isOnNetworkFilesystem(Path dataDir) {
        try {
            final FileStore store = Files.getFileStore(dataDir);
            return NETWORK_FILESYSTEMS.contains(store.type().toLowerCase(Locale.ROOT));
        } catch (IOException | SecurityException e) {
            // An unreadable store type is not a startup problem; local is the safe assumption, and the only cost of
            // being wrong is that a warning is not printed.
            return false;
        }
    }

    /** The decision's output before it becomes {@code Path}s: platform-shaped strings, host-independent. */
    record PathLayout(String dataDir, String logDir) {}
}
