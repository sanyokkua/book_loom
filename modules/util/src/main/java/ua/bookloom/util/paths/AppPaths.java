package ua.bookloom.util.paths;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The resolved filesystem locations this process will use, as values rather than lazy lookups.
 *
 * <p>There is no config directory: preferences live in the SQLite typed key-value {@code settings} table, so only
 * data and logs need an operating-system root
 * ({@code 02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#directory-kinds}).
 *
 * <p>This type sits on the <strong>pre-logging</strong> startup path — it is produced at step 2, and Logback is not
 * configured until step 5 — so nothing in this package may hold a static SLF4J logger. That is enforced, not merely
 * intended: the ArchUnit rule {@code bootstrap-no-static-logger} polices {@code ua.bookloom.util.paths..} by package.
 *
 * @param dataDir the directory holding the database and the lock file
 * @param logDir the directory holding rolling log files
 * @param lockFile the single-instance lock file, always inside {@code dataDir}
 * @param databaseFile the SQLite database file, always inside {@code dataDir}
 * @param onNetworkFilesystem whether {@code dataDir} appears to live on a network share (EC-ENV-8); reported, never
 *     acted on here — the {@code PRAGMA journal_mode=TRUNCATE} fallback it implies needs a SQLite connection that
 *     does not exist until the local-storage change
 */
public record AppPaths(Path dataDir, Path logDir, Path lockFile, Path databaseFile, boolean onNetworkFilesystem) {

    /** The single-instance lock file's fixed name inside the data directory. */
    public static final String LOCK_FILE_NAME = "bookloom.lock";

    /** The SQLite database's fixed name inside the data directory. */
    public static final String DATABASE_FILE_NAME = "bookloom.db";

    /**
     * Validates that the two roots are absolute and that the two files sit inside the data directory.
     *
     * <p>A relative data directory would resolve against the working directory, which for a double-clicked
     * application is wherever the launcher happened to start — {@code /} on macOS. That is precisely the "wrote the
     * database somewhere arbitrary" failure the resolver exists to prevent, so it is rejected here as well as at the
     * point each candidate path is chosen.
     */
    public AppPaths {
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(logDir, "logDir");
        Objects.requireNonNull(lockFile, "lockFile");
        Objects.requireNonNull(databaseFile, "databaseFile");
        requireAbsolute(dataDir, "dataDir");
        requireAbsolute(logDir, "logDir");
        if (!lockFile.equals(dataDir.resolve(LOCK_FILE_NAME))) {
            throw new IllegalArgumentException("lockFile must be " + LOCK_FILE_NAME + " inside dataDir");
        }
        if (!databaseFile.equals(dataDir.resolve(DATABASE_FILE_NAME))) {
            throw new IllegalArgumentException("databaseFile must be " + DATABASE_FILE_NAME + " inside dataDir");
        }
    }

    /**
     * Builds the resolved set from its two roots, deriving the lock and database paths.
     *
     * <p>The derived paths are not parameters because they are not decisions: their names are fixed and their
     * location is "inside the data directory". Deriving them here means the lock a second launch tests and the
     * database that lock protects cannot drift apart.
     *
     * @param dataDir the absolute data directory
     * @param logDir the absolute log directory
     * @return the resolved paths, with {@code onNetworkFilesystem} not yet probed
     */
    public static AppPaths of(Path dataDir, Path logDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        return new AppPaths(
                dataDir, logDir, dataDir.resolve(LOCK_FILE_NAME), dataDir.resolve(DATABASE_FILE_NAME), false);
    }

    /**
     * Returns a copy carrying the result of the network-filesystem probe.
     *
     * @param onNetwork whether the data directory appears to be on a network share
     * @return this instance when the flag is unchanged, otherwise a copy
     */
    public AppPaths withNetworkFilesystem(boolean onNetwork) {
        return onNetwork == onNetworkFilesystem
                ? this
                : new AppPaths(dataDir, logDir, lockFile, databaseFile, onNetwork);
    }

    private static void requireAbsolute(Path path, String name) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute, but was: " + path);
        }
    }
}
