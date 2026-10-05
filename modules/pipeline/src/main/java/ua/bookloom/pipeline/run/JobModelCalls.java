package ua.bookloom.pipeline.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.RequestSummary;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The run's {@link ModelCalls}: every call goes through the job's guarded model, and each attempt of a call that
 * really goes out is announced with its kind, its segments, its number, its timeout and the request's size; each
 * attempt's end is announced too, with the time it took and either the tokens it cost or the code it failed with.
 *
 * <p>The guard is asked for a model per call. A call the guard refuses because a stop or a pause was requested sends
 * no attempt, so it announces nothing. The model reports its own attempts: the provider client's retry after a
 * timeout is a second attempt with its own clock, which is what lets a screen say "attempt 2 of 2" instead of one
 * clock counting across both.
 *
 * <p>A provider that reports no usage still gets a tokens-per-second figure: the reply's completion tokens are
 * estimated in the target language and its generation time is the attempt's wall time, so the screen never estimates.
 */
@Slf4j
public final class JobModelCalls implements ModelCalls {

    // A reply may take at most this share of the window; the prompt and the safety margin keep the rest.
    private static final int OUTPUT_WINDOW_DIVISOR = 2;

    private final Function<Runnable, ChatModel> guard;
    private final Consumer<JobEvent> announce;
    private final Clock clock;
    private final String targetLanguage;
    private final int window;

    /**
     * Creates a seam for calls made against the default window, such as a glossary scan before any run.
     *
     * @param guard see the full constructor
     * @param announce see the full constructor
     * @param clock see the full constructor
     * @param targetLanguage see the full constructor
     */
    public JobModelCalls(
            final Function<Runnable, ChatModel> guard,
            final Consumer<JobEvent> announce,
            final Clock clock,
            final String targetLanguage) {
        this(guard, announce, clock, targetLanguage, ContextBudget.DEFAULT_WINDOW);
    }

    /**
     * Creates the run's model-call seam.
     *
     * @param guard the non-null factory of the guarded model; the runnable it receives is run once when a request
     *     goes out
     * @param announce the non-null receiver of each start and finish
     * @param clock the non-null clock an attempt is timed by
     * @param targetLanguage the non-null language tag a reply is written in, which a usage estimate counts by
     * @param window the context window in tokens every sized request is sent with, and which bounds its reply cap;
     *     positive
     */
    public JobModelCalls(
            final Function<Runnable, ChatModel> guard,
            final Consumer<JobEvent> announce,
            final Clock clock,
            final String targetLanguage,
            final int window) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
        this.window = window;
    }

    @Override
    public Result<ChatResponse> call(final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
        return callAbout(kind, segmentId == null ? List.of() : List.of(segmentId), request);
    }

    @Override
    public Result<ChatResponse> callAbout(final CallKind kind, final List<String> segmentIds, final ChatRequest asked) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(segmentIds, "segmentIds");
        Objects.requireNonNull(asked, "request");
        final ChatRequest request = asked.contextWindow() == null
                ? asked
                : asked.sizedTo(window, Math.max(1, window / OUTPUT_WINDOW_DIVISOR));
        log.debug(
                "Model call sized kind={} window={} asked={} cap={}",
                kind,
                request.contextWindow(),
                asked.contextWindow(),
                request.maxOutputTokens());
        final Attempts attempts = new Attempts(new Call(kind, List.copyOf(segmentIds), summaryOf(request)));
        final Result<ChatResponse> result = guard.apply(
                        () -> log.debug("Model call going out kind={} segmentIds={}", kind, segmentIds))
                .chat(request, attempts);
        attempts.ended(result);
        return result;
    }

    @Override
    public void announce(final JobEvent event) {
        Objects.requireNonNull(event, "event");
        log.debug("Announcing a model-call step event type={}", event.getClass().getSimpleName());
        announce.accept(event);
    }

    private static RequestSummary summaryOf(final ChatRequest request) {
        final int chars = request.messages().stream()
                .map(ChatMessage::content)
                .mapToInt(String::length)
                .sum();
        return new RequestSummary(chars, request.contextWindow(), request.maxOutputTokens());
    }

    /**
     * What every attempt of one call shares.
     *
     * @param kind what the call is for
     * @param segmentIds the segments it is about
     * @param summary the request's size as first built; an attempt's own cap may be lower
     */
    private record Call(CallKind kind, List<String> segmentIds, RequestSummary summary) {

        @Nullable
        String soleSegment() {
            return segmentIds.size() == 1 ? segmentIds.getFirst() : null;
        }
    }

    /** Hears one call's attempts on the job thread and turns each into a start and a finish. */
    private final class Attempts implements CallAttemptListener {

        private final Call call;
        private @Nullable CallAttempt current;
        private @Nullable Instant startedAt;

        Attempts(final Call call) {
            this.call = call;
        }

        @Override
        public void started(final CallAttempt attempt) {
            current = attempt;
            startedAt = clock.instant();
            final RequestSummary summary = new RequestSummary(
                    call.summary().messageChars(), call.summary().contextWindow(), attempt.maxOutputTokens());
            log.debug(
                    "Announcing model call kind={} segmentIds={} attempt={} of {} timeout={} chars={} cap={}",
                    call.kind(),
                    call.segmentIds(),
                    attempt.number(),
                    attempt.maxAttempts(),
                    attempt.timeout(),
                    summary.messageChars(),
                    summary.maxOutputTokens());
            announce.accept(new ModelCallStarted(
                    call.soleSegment(),
                    call.kind(),
                    call.segmentIds(),
                    attempt.number(),
                    attempt.maxAttempts(),
                    attempt.timeout(),
                    summary));
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            final Duration elapsed = elapsed();
            current = null;
            log.debug(
                    "Model call attempt failed kind={} segmentIds={} attempt={} elapsedMs={} code={}",
                    call.kind(),
                    call.segmentIds(),
                    attempt.number(),
                    elapsed.toMillis(),
                    code);
            announce.accept(new ModelCallFinished(
                    call.soleSegment(),
                    call.kind(),
                    elapsed,
                    null,
                    0,
                    false,
                    call.segmentIds(),
                    attempt.number(),
                    code));
        }

        // An attempt that failed was announced by failed(); only an answered one is still current here.
        void ended(final Result<ChatResponse> result) {
            final CallAttempt attempt = current;
            final ChatResponse reply = result.data();
            if (attempt == null || reply == null) {
                log.debug(
                        "Model call ended kind={} segmentIds={} answered={} attemptOpen={}",
                        call.kind(),
                        call.segmentIds(),
                        reply != null,
                        attempt != null);
                return;
            }
            announce.accept(finished(attempt, elapsed(), reply));
        }

        private void logFinished(
                final CallAttempt attempt,
                final Duration elapsed,
                final TokenUsage usage,
                final boolean estimated,
                final int outputChars) {
            final Duration generation = usage.generation();
            final Duration promptEval = usage.promptEval();
            log.debug(
                    "Model call finished kind={} segmentIds={} attempt={} elapsedMs={} usage={} promptTokens={}"
                            + " completionTokens={} generationMs={} promptEvalMs={} cachedPromptTokens={} outputChars={}",
                    call.kind(),
                    call.segmentIds(),
                    attempt.number(),
                    elapsed.toMillis(),
                    estimated ? "estimated" : "reported",
                    usage.prompt(),
                    usage.completion(),
                    generation == null ? null : generation.toMillis(),
                    promptEval == null ? null : promptEval.toMillis(),
                    usage.cachedPrompt(),
                    outputChars);
        }

        private Duration elapsed() {
            final Instant started = startedAt;
            return started == null ? Duration.ZERO : Duration.between(started, clock.instant());
        }

        private ModelCallFinished finished(
                final CallAttempt attempt, final Duration elapsed, final ChatResponse reply) {
            final String content = reply.content();
            final int outputChars = content.codePointCount(0, content.length());
            final TokenUsage reported = reply.usage();
            final boolean estimated = reported == null;
            final TokenUsage usage = reported == null
                    ? new TokenUsage(null, TokenEstimator.estimate(content, targetLanguage), elapsed)
                    : reported;
            logFinished(attempt, elapsed, usage, estimated, outputChars);
            return new ModelCallFinished(
                    call.soleSegment(),
                    call.kind(),
                    elapsed,
                    usage,
                    outputChars,
                    estimated,
                    call.segmentIds(),
                    attempt.number(),
                    null);
        }
    }
}
