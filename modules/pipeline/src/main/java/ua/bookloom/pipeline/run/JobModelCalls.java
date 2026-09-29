package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The run's {@link ModelCalls}: every call goes through the job's guarded model, and the start of a call that really
 * goes out is announced with its own kind.
 *
 * <p>The guard is asked for a model per call, given the hook it must run at the moment the request leaves. A call the
 * guard refuses because a stop or a pause was requested never runs the hook, so it announces nothing.
 */
@Slf4j
public final class JobModelCalls implements ModelCalls {

    private final Function<Runnable, ChatModel> guard;
    private final Consumer<ModelCallStarted> announce;

    /**
     * Creates the run's model-call seam.
     *
     * @param guard the non-null factory of the guarded model; the runnable it receives is run once when a request
     *     goes out
     * @param announce the non-null receiver of each start
     */
    public JobModelCalls(final Function<Runnable, ChatModel> guard, final Consumer<ModelCallStarted> announce) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.announce = Objects.requireNonNull(announce, "announce");
    }

    @Override
    public Result<ChatResponse> call(final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(request, "request");
        final AtomicBoolean sent = new AtomicBoolean();
        final Result<ChatResponse> result = guard.apply(() -> {
                    sent.set(true);
                    log.debug("Announcing model call kind={} segmentId={}", kind, segmentId);
                    announce.accept(new ModelCallStarted(segmentId, kind));
                })
                .chat(request);
        if (!sent.get()) {
            log.debug("Model call refused before it went out kind={} segmentId={}", kind, segmentId);
        }
        return result;
    }
}
