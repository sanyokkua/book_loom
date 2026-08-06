package ua.bookloom.app.bootstrap;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * The process-wide single-instance lock, held for the lifetime of the run.
 *
 * <p>Acquired <strong>before the injector is built and before any database is opened</strong>, which is the entire
 * point: two processes writing one SQLite file is the corruption this exists to prevent, so the guard has to be in
 * place before anything can open it. It is an {@code :app}/{@code :util} concern and never a {@code :persistence}
 * one — a DAO cannot guard a file it is itself the first to touch.
 *
 * <p>A second launch that fails to acquire <strong>shows a dialog and exits</strong>. It does not focus, signal, or
 * raise the first window: there is no IPC in this application, and adding a channel between instances to solve a
 * problem the lock already solves would be a network-shaped feature in an offline product.
 *
 * <p>The lock is released by the operating system on exit or crash, so a killed process does not leave the
 * application permanently unstartable. {@link #close()} exists for orderly shutdown and for tests.
 */
public final class SingleInstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private SingleInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to take the lock without blocking.
     *
     * <p>Non-blocking by design. Waiting would turn "BookLoom is already running" into a launcher that appears to
     * hang, and there is nothing to wait for: the first instance holds the lock until the user closes it.
     *
     * @param lockFile the lock file, inside the resolved data directory
     * @return the held lock, {@code busy} when another instance holds it, or {@code validation} when the lock file
     *     cannot be opened at all
     */
    public static Result<SingleInstanceLock> acquire(Path lockFile) {
        Objects.requireNonNull(lockFile, "lockFile");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(
                    lockFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            final FileLock lock = channel.tryLock();
            if (lock == null) {
                closeQuietly(channel);
                return alreadyRunning();
            }
            return Result.ok(new SingleInstanceLock(channel, lock));
        } catch (OverlappingFileLockException e) {
            // Another lock is held on this file by THIS JVM. Reachable when a test acquires twice in one process;
            // in production it means the same thing to the user as a second process would.
            closeQuietly(channel);
            return alreadyRunning();
        } catch (IOException | SecurityException e) {
            closeQuietly(channel);
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Cannot start BookLoom",
                    "BookLoom could not open its lock file, so it cannot safely start.",
                    null,
                    e));
        }
    }

    private static Result<SingleInstanceLock> alreadyRunning() {
        return Result.err(AppError.of(
                ErrorCode.busy,
                "BookLoom is already running",
                "Another copy of BookLoom is already open. Switch to that window to continue working."));
    }

    /** Releases the lock and closes the channel. The OS would do both on exit; this makes shutdown orderly. */
    @Override
    public void close() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            // Nothing useful to do: the process is ending and the OS releases the lock regardless. Rethrowing
            // would turn a clean shutdown into a crash over a lock that is about to be released anyway.
        }
        closeQuietly(channel);
    }

    private static void closeQuietly(@org.jspecify.annotations.Nullable FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            // Same reasoning as close(): there is no recovery, and no logger to report it to yet.
        }
    }
}
