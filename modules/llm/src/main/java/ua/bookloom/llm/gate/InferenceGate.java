package ua.bookloom.llm.gate;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** Serializes one provider operation at a time with a fair permit and unconditional release. */
@Slf4j
public final class InferenceGate {

    private final Semaphore permit = new Semaphore(1, true);

    /** Runs one result-returning operation after acquiring the fair permit. */
    public <T> Result<T> run(Supplier<Result<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        log.debug("Inference gate waiting queueLength={}", permit.getQueueLength());
        boolean acquired = false;
        try {
            permit.acquire();
            acquired = true;
            log.debug("Inference gate acquired queueLength={}", permit.getQueueLength());
            final Result<T> result = Objects.requireNonNull(operation.get(), "operation result");
            logOperationFinished(result);
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.debug("Inference gate acquisition interrupted; cancellation will be returned");
            return cancelled();
        } catch (Throwable failure) {
            log.error(
                    "Inference gate operation failed failureType={}",
                    failure.getClass().getSimpleName(),
                    failure);
            return failed(failure);
        } finally {
            if (acquired) {
                permit.release();
                log.debug("Inference gate released queueLength={}", permit.getQueueLength());
            }
        }
    }

    private void logOperationFinished(Result<?> result) {
        log.debug(
                "Inference gate operation finished code={} queueLength={}",
                result.error() == null ? "none" : result.error().code(),
                permit.getQueueLength());
    }

    private static <T> Result<T> cancelled() {
        return Result.err(AppError.of(
                ErrorCode.cancelled,
                "Inference cancelled",
                "The provider request was interrupted while waiting for the inference gate."));
    }

    private static <T> Result<T> failed(Throwable failure) {
        return Result.err(AppError.of(
                ErrorCode.internal, "Inference failed", "The provider request could not be completed.", null, failure));
    }
}
