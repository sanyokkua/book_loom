package ua.bookloom.pipeline.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The run's {@link ModelCalls}: every call goes through the job's guarded model, the start of a call that really goes
 * out is announced with its own kind, and its reply is announced with the time it took and the tokens it cost.
 *
 * <p>The guard is asked for a model per call, given the hook it must run at the moment the request leaves. A call the
 * guard refuses because a stop or a pause was requested never runs the hook, so it announces nothing. The provider
 * client's own retries happen inside the one call, so they are timed with it and never announced apart.
 *
 * <p>A provider that reports no usage still gets a tokens-per-second figure: the reply's completion tokens are
 * estimated in the target language and its generation time is the call's wall time, so the screen never estimates.
 * A call answered with an error has no reply to count and announces no finish; the pause or flag that follows it
 * says what happened.
 */
@Slf4j
public final class JobModelCalls implements ModelCalls {

    private final Function<Runnable, ChatModel> guard;
    private final Consumer<JobEvent> announce;
    private final Clock clock;
    private final String targetLanguage;

    /**
     * Creates the run's model-call seam.
     *
     * @param guard the non-null factory of the guarded model; the runnable it receives is run once when a request
     *     goes out
     * @param announce the non-null receiver of each start and finish
     * @param clock the non-null clock a call is timed by
     * @param targetLanguage the non-null language tag a reply is written in, which a usage estimate counts by
     */
    public JobModelCalls(
            final Function<Runnable, ChatModel> guard,
            final Consumer<JobEvent> announce,
            final Clock clock,
            final String targetLanguage) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
    }

    @Override
    public Result<ChatResponse> call(final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(request, "request");
        final AtomicReference<@Nullable Instant> sentAt = new AtomicReference<>();
        final Result<ChatResponse> result = guard.apply(() -> {
                    sentAt.set(clock.instant());
                    log.debug("Announcing model call kind={} segmentId={}", kind, segmentId);
                    announce.accept(new ModelCallStarted(segmentId, kind));
                })
                .chat(request);
        final Instant started = sentAt.get();
        if (started == null) {
            log.debug("Model call refused before it went out kind={} segmentId={}", kind, segmentId);
        } else {
            answered(kind, segmentId, Duration.between(started, clock.instant()), result);
        }
        return result;
    }

    private void answered(
            final CallKind kind,
            @Nullable final String segmentId,
            final Duration elapsed,
            final Result<ChatResponse> result) {
        final ChatResponse reply = result.data();
        if (reply != null) {
            announce.accept(finished(kind, segmentId, elapsed, reply));
            return;
        }
        log.debug(
                "Model call answered an error kind={} segmentId={} elapsedMs={} code={}; no finish is announced",
                kind,
                segmentId,
                elapsed.toMillis(),
                Objects.requireNonNull(result.error(), "error").code());
    }

    private ModelCallFinished finished(
            final CallKind kind, @Nullable final String segmentId, final Duration elapsed, final ChatResponse reply) {
        final String content = reply.content();
        final int outputChars = content.codePointCount(0, content.length());
        final TokenUsage reported = reply.usage();
        final boolean estimated = reported == null;
        final TokenUsage usage = reported == null
                ? new TokenUsage(null, TokenEstimator.estimate(content, targetLanguage), elapsed)
                : reported;
        final Duration generation = usage.generation();
        log.debug(
                "Model call finished kind={} segmentId={} elapsedMs={} usage={} promptTokens={} completionTokens={}"
                        + " generationMs={} outputChars={}",
                kind,
                segmentId,
                elapsed.toMillis(),
                estimated ? "estimated" : "reported",
                usage.prompt(),
                usage.completion(),
                generation == null ? null : generation.toMillis(),
                outputChars);
        return new ModelCallFinished(segmentId, kind, elapsed, usage, outputChars, estimated);
    }
}
