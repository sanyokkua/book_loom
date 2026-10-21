package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/**
 * The model of a names pass: it refuses to send a call once the calling thread has been interrupted, because Stop
 * interrupts the thread and a model that never looks at the flag would otherwise be asked again by the next step.
 * A call already in flight is the provider client's to cancel; this class only closes the gap between two calls.
 */
@Slf4j
@RequiredArgsConstructor
final class StoppableModel implements ChatModel {

    private final ChatModel inner;

    /**
     * The error a stopped pass answers.
     *
     * @return a {@link ErrorCode#cancelled} error
     */
    static AppError stopped() {
        return AppError.of(ErrorCode.cancelled, "Stopped", "The names pass was stopped.");
    }

    @Override
    public @Nullable Integer contextWindowOverride() {
        return inner.contextWindowOverride();
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        if (Thread.currentThread().isInterrupted()) {
            log.debug("Names pass sends no further call: the calling thread was interrupted");
            return Result.err(stopped());
        }
        return inner.chat(request);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attempts, "attempts");
        if (Thread.currentThread().isInterrupted()) {
            log.debug("Names pass sends no further call: the calling thread was interrupted");
            return Result.err(stopped());
        }
        return inner.chat(request, attempts);
    }
}
