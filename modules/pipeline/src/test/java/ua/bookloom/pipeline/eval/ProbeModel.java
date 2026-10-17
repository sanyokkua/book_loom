package ua.bookloom.pipeline.eval;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/**
 * The last link before the provider: it sees the request after the run's seam sized it, remembers it with the reply and
 * the wall time, and sends it on. With no model behind it (a dry probe) it answers every call with a cancellation, so the
 * eval class under the probe runs to its end without any call going out.
 */
final class ProbeModel implements ChatModel {

    /** One request that reached the model, with the answer (null when dry) and the wall time of the call. */
    record Exchange(ChatRequest request, @Nullable Result<ChatResponse> reply, long wallMs) {}

    private final @Nullable ChatModel delegate;
    private final List<Exchange> exchanges = new ArrayList<>();

    /** A recorder over {@code delegate}, or a dry one when it is null. */
    ProbeModel(@Nullable final ChatModel delegate) {
        this.delegate = delegate;
    }

    boolean isDry() {
        return delegate == null;
    }

    /** The exchanges since the last {@link #reset()}, in call order. */
    List<Exchange> exchanges() {
        return List.copyOf(exchanges);
    }

    void reset() {
        exchanges.clear();
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return record(request, () -> delegate == null ? null : delegate.chat(request));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        return record(request, () -> delegate == null ? null : delegate.chat(request, attempts));
    }

    private Result<ChatResponse> record(
            final ChatRequest request, final Supplier<@Nullable Result<ChatResponse>> send) {
        final Instant started = Instant.now();
        final Result<ChatResponse> reply = send.get();
        exchanges.add(new Exchange(
                request, reply, Duration.between(started, Instant.now()).toMillis()));
        return reply == null
                ? Result.err(
                        new AppError(ErrorCode.cancelled, "Dry probe", "dry run: no call was made", null, false, null))
                : reply;
    }
}
