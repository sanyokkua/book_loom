package ua.bookloom.persistence.sqlite;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * The one thread that writes. SQLite allows a single writer, so every write is one JDBI transaction on this thread;
 * contention becomes queueing here instead of {@code SQLITE_BUSY} (persistence-sqlite.md).
 */
@Slf4j
final class WriteExecutor implements AutoCloseable {

    private static final String THREAD_NAME = "bookloom-sqlite-writer";

    private final Jdbi jdbi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    WriteExecutor(final Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
    }

    <T> Result<T> execute(final String operation, final Function<Handle, T> work) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        log.debug("Write queued operation={}", operation);
        try {
            final T value =
                    executor.submit(() -> jdbi.inTransaction(work::apply)).get();
            log.debug("Write committed operation={}", operation);
            return Result.ok(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(operation, e);
        } catch (ExecutionException e) {
            return failure(operation, e.getCause() == null ? e : e.getCause());
        } catch (RuntimeException e) {
            return failure(operation, e);
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
