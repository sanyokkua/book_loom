package ua.bookloom.pipeline.eval;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/**
 * Gives every request that has no seed of its own the repeat's seed, so a repeat can be sent again exactly; a request
 * that already carries one (a retry after a timeout) keeps it. Every other method of {@link ChatModel} is forwarded, so
 * the decorated model sizes its window as the inner one would.
 *
 * @param model the model under test
 * @param seed the seed stamped on an unseeded request
 */
@Slf4j
record SeededModel(ChatModel model, int seed) implements ChatModel {

    /** Rejects a missing model. */
    SeededModel {
        Objects.requireNonNull(model, "model");
    }

    /**
     * The decorator for repeat {@code repeat} of a run whose first repeat uses {@code base}.
     *
     * @param model the non-null model under test
     * @param base the seed of repeat 0
     * @param repeat the 0-based repeat
     * @return the model that stamps {@code base + repeat}
     */
    static SeededModel of(final ChatModel model, final int base, final int repeat) {
        log.debug("Seeded model base={} repeat={}", base, repeat);
        return new SeededModel(model, base + repeat);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return model.chat(seeded(request));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        return model.chat(seeded(request), attempts);
    }

    @Override
    public @Nullable Integer contextWindowOverride() {
        return model.contextWindowOverride();
    }

    private ChatRequest seeded(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        return request.seed() == null ? request.withSeed(seed) : request;
    }
}
