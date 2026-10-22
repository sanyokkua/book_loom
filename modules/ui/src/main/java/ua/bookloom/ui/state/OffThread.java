package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** Runs a service call on the daemon executor and hands its answer back on the FX thread, never throwing. */
@Slf4j
@RequiredArgsConstructor
final class OffThread {

    private final ExecutorService executor;

    <T> void call(final String what, final Supplier<Result<T>> work, final Consumer<Result<T>> onFx) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(onFx, "onFx");
        executor.execute(() -> {
            log.debug("{} on {}", what, Thread.currentThread().getName());
            Result<T> answer;
            try {
                answer = work.get();
            } catch (Throwable thrown) {
                log.error("the project service threw instead of returning a result while trying to {}", what, thrown);
                answer = Result.err(AppError.of(
                        ErrorCode.internal,
                        "Unexpected error",
                        "The action could not be finished because of an unexpected error.",
                        null,
                        thrown));
            }
            final Result<T> published = answer;
            Platform.runLater(() -> onFx.accept(published));
        });
    }
}
