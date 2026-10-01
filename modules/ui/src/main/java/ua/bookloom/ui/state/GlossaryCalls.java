package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * Runs a call of the glossary service on the background executor and hands its answer to the FX thread.
 *
 * <p>A service that throws, or an executor that refuses the work, still ends in an answer, an {@code internal} error,
 * so the screen can say so in place instead of waiting for one that never comes.
 */
@Slf4j
final class GlossaryCalls {

    private final ExecutorService executor;

    GlossaryCalls(final ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    <T> void run(final String what, final Supplier<Result<T>> work, final Consumer<Result<T>> publish) {
        try {
            executor.execute(task(what, work, publish));
        } catch (RuntimeException rejected) {
            refused(what, rejected, publish);
        }
    }

    /**
     * Runs one call that a person may stop.
     *
     * @return the submitted work, which {@link Future#cancel(boolean)} interrupts; or null when the executor refused
     *     it, which is answered at once
     */
    @Nullable
    <T> Future<?> start(final String what, final Supplier<Result<T>> work, final Consumer<Result<T>> publish) {
        try {
            return executor.submit(task(what, work, publish));
        } catch (RuntimeException rejected) {
            refused(what, rejected, publish);
            return null;
        }
    }

    private static <T> Runnable task(
            final String what, final Supplier<Result<T>> work, final Consumer<Result<T>> publish) {
        return () -> {
            final Result<T> answer = attempt(what, work);
            Platform.runLater(() -> publish.accept(answer));
        };
    }

    private static <T> void refused(
            final String what, final RuntimeException rejected, final Consumer<Result<T>> publish) {
        log.warn("the glossary {} could not be submitted", what, rejected);
        publish.accept(Result.err(unavailable()));
    }

    private static <T> Result<T> attempt(final String what, final Supplier<Result<T>> work) {
        try {
            return work.get();
        } catch (Throwable thrown) {
            log.error("the glossary service threw instead of returning a result for the {}", what, thrown);
            return Result.err(unavailable());
        }
    }

    private static AppError unavailable() {
        return AppError.of(ErrorCode.internal, "Glossary unavailable", "The glossary could not be reached.");
    }
}
