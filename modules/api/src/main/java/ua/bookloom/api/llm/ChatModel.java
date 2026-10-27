package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;

/**
 * The provider-neutral seam through which a caller sends one chat request.
 */
public interface ChatModel {

    /**
     * Sends the ordered conversation to this already-bound model.
     *
     * @param request the non-null conversation to send
     * @return the model response, or a typed failure
     */
    Result<ChatResponse> chat(ChatRequest request);

    /**
     * Sends the conversation and reports each attempt to {@code attempts}. A model that never sends a request twice
     * reports its one call as a single attempt with no timeout of its own.
     *
     * @param request the non-null conversation to send
     * @param attempts the non-null listener of each attempt
     * @return the model response, or a typed failure
     */
    default Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attempts, "attempts");
        final CallAttempt only = new CallAttempt(1, 1, null, request.maxOutputTokens());
        attempts.started(only);
        final Result<ChatResponse> result = chat(request);
        final AppError error = result.error();
        if (error != null) {
            attempts.failed(only, error.code());
        }
        return result;
    }

    /**
     * The context window the person set for this model, which replaces the one detected from the program.
     *
     * @return the stored window in tokens, or null when none is set
     */
    default @Nullable Integer contextWindowOverride() {
        return null;
    }
}
