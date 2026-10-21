package ua.bookloom.persistence.sqlite;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * The one thread that writes. SQLite allows a single writer, so every write is one JDBI transaction on this thread;
 * contention becomes queueing here instead of {@code SQLITE_BUSY} (persistence-sqlite.md).
 *
 * <p>The caller waits for its write without being interruptible: a submitted write runs to its commit or rollback
 * whatever the caller does, so answering an interrupted caller with an error would report a committed write as lost.
 * The interrupt is kept and set again once the answer is in. A write asked for from inside a write would wait for the
 * thread it runs on, so it is refused instead.
 */
@Slf4j
final class WriteExecutor implements AutoCloseable {

    private static final String THREAD_NAME = "bookloom-sqlite-writer";

    private final Jdbi jdbi;
    /** The thread this executor writes on, once it has started; a write asked for on it is nested. */
    private final AtomicReference<@Nullable Thread> writerThread = new AtomicReference<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        writerThread.set(thread);
        return thread;
    });

    WriteExecutor(final Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    <T> Result<T> execute(final String operation, final Function<Handle, T> work) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        if (Thread.currentThread().equals(writerThread.get())) {
            log.debug("Write refused, nested inside another write operation={}", operation);
            return failure(operation, new IllegalStateException("nested storage write " + operation));
        }
        log.debug("Write queued operation={}", operation);
        try {
            final T value = awaitUninterruptibly(executor.submit(() -> jdbi.inTransaction(work::apply)));
            log.debug("Write committed operation={}", operation);
            return Result.ok(value);
        } catch (ExecutionException e) {
            return failure(operation, e.getCause() == null ? e : e.getCause());
        } catch (RuntimeException e) {
            return failure(operation, e);
        }
    }

    private static <T> T awaitUninterruptibly(final Future<T> future) throws ExecutionException {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException e) {
                    log.debug("Write caller interrupted while waiting; waiting on for the commit");
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <T> Result<T> failure(final String operation, final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Storage failure", "The data could not be saved.", "storage " + operation, cause);
        log.error("Storage write failed operation={} code={}", operation, error.code(), cause);
        return Result.err(error);
    }

    @Override
    public void close() {
        log.debug("Write executor closing");
        executor.shutdown();
    }
}
