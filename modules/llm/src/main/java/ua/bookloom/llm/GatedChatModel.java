package ua.bookloom.llm;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.llm.CallRecorder.TimedCall;
import ua.bookloom.llm.RetryChoice.Resend;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.RetryRule;
import ua.bookloom.llm.profile.ObservedReasoning;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.provider.RejectedCapability;
import ua.bookloom.llm.provider.SettingDrops;
import ua.bookloom.llm.retry.RetryPolicy;

/** Binds one model id to a provider client and retries typed failures outside the shared inference gate. */
@Slf4j
public final class GatedChatModel implements ChatModel {
    // A retry after a timeout asks for at most three quarters of the original cap.
    private static final int RETRY_CAP_NUMERATOR = 3;
    private static final int RETRY_CAP_DENOMINATOR = 4;

    private final ProviderClient client;
    private final String modelId;
    private final InferenceGate gate;
    private final RetryPolicy retryPolicy;
    private final String providerId;
    private final @org.jspecify.annotations.Nullable RefusalMemory refusals;
    private final CallRecorder recorder;
    private final BuiltWrongLog builtWrong;
    private final ObservedReasoning thinking;
    private final BoundSettings bound;
    private final StructuredRungs rungs = new StructuredRungs();

    /** Captures the concrete provider client, model id, shared gate, and retry policy for each call. */
    public GatedChatModel(ProviderClient client, String modelId, InferenceGate gate, RetryPolicy retryPolicy) {
        this(client, "", modelId, gate, retryPolicy, ModelSettings::defaults);
    }

    /** As the four-argument form, applying the model's own settings, read afresh for every call. */
    public GatedChatModel(
            ProviderClient client,
            String providerId,
            String modelId,
            InferenceGate gate,
            RetryPolicy retryPolicy,
            BiFunction<String, String, ModelSettings> settings) {
        this(client, providerId, modelId, gate, retryPolicy, settings, null);
    }

    /** As the six-argument form, remembering the settings a model refuses, or not when {@code refusals} is null. */
    public GatedChatModel(
            ProviderClient client,
            String providerId,
            String modelId,
            InferenceGate gate,
            RetryPolicy retryPolicy,
            BiFunction<String, String, ModelSettings> settings,
            @org.jspecify.annotations.Nullable RefusalMemory refusals) {
        this(
                client,
                providerId,
                modelId,
                gate,
                retryPolicy,
                new ModelSettingsSource(settings, refusals, BoundSettings.unrowed(client, modelId)),
                CallObservers.none());
    }

    /**
     * The full form: the model's settings, refusals and profile come from {@code source}, and the records of its calls
     * are kept by {@code observers}.
     */
    GatedChatModel(
            ProviderClient client,
            String providerId,
            String modelId,
            InferenceGate gate,
            RetryPolicy retryPolicy,
            ModelSettingsSource source,
            CallObservers observers) {
        this.builtWrong = Objects.requireNonNull(observers, "observers").builtWrong();
        this.thinking = observers.thinking();
        this.refusals = Objects.requireNonNull(source, "source").refusals();
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.bound = new BoundSettings(providerId, modelId, source.settings(), source.refusals(), source.profiles());
        this.client = Objects.requireNonNull(client, "client");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.recorder =
                new CallRecorder(observers.ledger(), providerId, modelId, client.kind(), observers.capIgnored());
    }

    @Override
    public @org.jspecify.annotations.Nullable Integer contextWindowOverride() {
        return bound.contextWindow();
    }

    @Override
    public ModelProfile profile() {
        return bound.profile();
    }

    @Override
    public Result<ChatResponse> chat(ChatRequest request) {
        return chat(request, CallAttemptListener.NONE);
    }

    @Override
    public Result<ChatResponse> chat(ChatRequest request, CallAttemptListener attempts) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attempts, "attempts");
        final EnumSet<RejectedCapability> downgraded = EnumSet.noneOf(RejectedCapability.class);
        final BoundSettings.Applied applied = bound.applied(request, downgraded);
        final ProviderCallResult<ChatResponse> call = callWithTransportRetry(applied, attempts, downgraded);
        notedThinking(call);
        return call.result().map(response -> BoundSettings.finished(applied.profile(), response));
    }

    // A repeat with a raised cap, or an end with a thinking reason, says the model spent its answer thinking.
    private void notedThinking(ProviderCallResult<ChatResponse> call) {
        final AppError failed = call.result().error();
        FailureReason reason = null;
        if (failed != null) {
            reason = FailureReason.in(failed.details()).orElse(null);
        } else if (call.sent() != null) {
            reason = FailureReason.CAP_SPENT;
        }
        if (reason == FailureReason.CAP_SPENT || reason == FailureReason.REASONING_EXHAUSTED) {
            thinking.note(providerId, modelId, reason);
        }
    }

    private ProviderCallResult<ChatResponse> callWithTransportRetry(
            BoundSettings.Applied applied, CallAttemptListener attempts, EnumSet<RejectedCapability> downgraded) {
        ChatRequest original = applied.request();
        ChatRequest request = original;
        int attempt = 1;
        int timedOut = 0;
        final EnumSet<FailureReason> resentOnce = EnumSet.noneOf(FailureReason.class);
        while (true) {
            final AttemptResult outcome =
                    attempt(request, new Tries(attempt, timedOut, attempts, downgraded, resentOnce, applied.profile()));
            if (outcome.refused()) {
                // A refusal is not an attempt: the same attempt number goes out again, minus the refused setting.
                original = SettingDrops.stripAll(original, downgraded);
                request = SettingDrops.stripAll(request, downgraded);
                continue;
            }
            timedOut += outcome.timedOut() ? 1 : 0;
            if (outcome.resend() == Resend.NONE) {
                return outcome.call();
            }
            if (!retryPolicy.sleep(outcome.delay())) {
                return ProviderCallResult.withoutRetryAfter(cancelled());
            }
            attempt++;
            final ChatRequest next = nextRequest(outcome, original, request, attempt, downgraded);
            if (next == null) {
                return outcome.call();
            }
            request = next;
        }
    }

    // The request the decision sends next, or null when a state check found the server not answering at all.
    private @org.jspecify.annotations.Nullable ChatRequest nextRequest(
            AttemptResult outcome,
            ChatRequest original,
            ChatRequest request,
            int nextAttempt,
            EnumSet<RejectedCapability> downgraded) {
        return switch (outcome.resend()) {
            case VARIED_AFTER_TIMEOUT -> SettingDrops.stripAll(variedAfterTimeout(original, nextAttempt), downgraded);
            case NEW_SEED -> withNewSeed(request, nextAttempt, downgraded);
            case SAME -> isAnswering(outcome.call(), request) ? request : null;
            case BACKOFF -> request;
            case NONE -> null;
        };
    }

    // A looping model needs another random path; a seed the model refused stays dropped, so the resend is the same.
    private ChatRequest withNewSeed(ChatRequest request, int nextAttempt, EnumSet<RejectedCapability> downgraded) {
        final Integer seed = request.seed();
        if (downgraded.contains(RejectedCapability.SEED)) {
            log.debug(
                    "New seed not sent: the model refused the seed setting model={} kind={}",
                    modelId,
                    request.callKind());
        }
        return SettingDrops.stripAll(request.withSeed(seed == null ? nextAttempt : seed + 1), downgraded);
    }

    // Sent again unchanged only to a server that answers at all; checked after the wait and outside the gate, so a
    // server that went away is handed to the run as an outage instead of being sent the same body to fail on.
    private boolean isAnswering(ProviderCallResult<ChatResponse> call, ChatRequest request) {
        final AppError failed = call.result().error();
        final String reason = failed == null ? "none" : FailureReason.tokenIn(failed.details());
        final Result<Boolean> checked = client.probe().result();
        if (checked.isOk()) {
            log.debug(
                    "State check passed before the resend model={} kind={} reason={}",
                    modelId,
                    request.callKind(),
                    reason);
            return true;
        }
        log.debug(
                "No resend: state check failed reason={} probeCode={}",
                reason,
                Objects.requireNonNull(checked.error(), "probe error").code());
        return false;
    }

    private AttemptResult attempt(ChatRequest request, Tries tries) {
        final int attempt = tries.attempt();
        final CallAttempt reported = reported(request, tries);
        logStarted(request, reported);
        final Result<TimedCall> gated =
                CallRecorder.timed(gate, () -> tries.listener().started(reported), () -> client.chat(modelId, request));
        if (gated.isErr()) {
            return finished(ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(gated.error(), "gate error"))));
        }
        final TimedCall timed = Objects.requireNonNull(gated.data(), "provider call");
        final ProviderCallResult<ChatResponse> call = timed.call();
        final Result<ChatResponse> result = call.result();
        if (isStepped(request, call, tries)) {
            return new AttemptResult(call, Resend.NONE, Duration.ZERO, false, true);
        }
        if (result.isOk()) {
            recorder.succeeded(request, attempt, timed);
            return finished(call);
        }
        final AppError error = Objects.requireNonNull(result.error(), "provider error");
        recorder.record(request, attempt, timed);
        tries.listener().failed(reported, error.code());
        return failure(call, error, request, tries);
    }

    // An attempt after the timeout budget is spent is the last one if it stalls too, which is what a waiting person
    // needs to know; a transient failure of another kind may still earn one more within the total budget.
    private CallAttempt reported(ChatRequest request, Tries tries) {
        final int stallBound = tries.attempt() + RetryPolicy.MAX_TIMEOUT_ATTEMPTS - 1 - tries.timedOut();
        final int maxAttempts = Math.clamp(stallBound, tries.attempt(), RetryPolicy.MAX_ATTEMPTS);
        final String body = client.requestBody(modelId, request);
        return new CallAttempt(
                tries.attempt(),
                maxAttempts,
                client.chatTimeout(request),
                request.maxOutputTokens(),
                body.isEmpty() ? null : body);
    }

    private void logStarted(ChatRequest request, CallAttempt reported) {
        log.debug(
                "Provider chat attempt started model={} kind={} attempt={} maxAttempts={} timeout={} seed={}"
                        + " maxOutputTokens={}",
                modelId,
                request.callKind(),
                reported.number(),
                reported.maxAttempts(),
                reported.timeout(),
                request.seed(),
                request.maxOutputTokens());
    }

    private AttemptResult failure(
            ProviderCallResult<ChatResponse> call, AppError error, ChatRequest request, Tries tries) {
        final int attempt = tries.attempt();
        final boolean timedOut = error.code() == ErrorCode.timeout;
        final RetryChoice.Decision decision =
                RetryChoice.decide(error, attempt, tries.timedOut(), tries.resentOnce(), retryPolicy);
        if (decision.resend() == Resend.NONE) {
            logStopped(request, attempt, error, decision);
            return new AttemptResult(call, Resend.NONE, Duration.ZERO, timedOut, false);
        }
        final FailureReason reason = decision.reason();
        if (reason != null && (decision.resend() == Resend.SAME || decision.resend() == Resend.NEW_SEED)) {
            tries.resentOnce().add(reason);
        }
        final Duration delay = retryPolicy.delayBeforeRetry(attempt, call.retryAfter());
        log.warn(
                "Retrying provider chat model={} kind={} failedAttempt={} nextAttempt={} code={} reason={} rule={}"
                        + " delay={} varied={}",
                modelId,
                request.callKind(),
                attempt,
                attempt + 1,
                error.code(),
                FailureReason.tokenIn(error.details()),
                decision.rule(),
                delay,
                timedOut);
        return new AttemptResult(call, decision.resend(), delay, timedOut, false);
    }

    // Given up when its resends are spent or its row says a resend cannot help; an unmatched code that is never
    // repeated (auth, a missing model) is the caller's to word, and a loading model's wait is the run's to log.
    private void logStopped(ChatRequest request, int attempt, AppError error, RetryChoice.Decision decision) {
        final String reason = FailureReason.tokenIn(error.details());
        if (decision.isGaveUp() || decision.rule() == RetryRule.NONE) {
            log.warn(
                    "gave up provider chat code={} reason={} kind={} model={} attempts={} why={}",
                    error.code(),
                    reason,
                    request.callKind(),
                    modelId,
                    attempt,
                    decision.why());
        } else {
            log.debug(
                    "Provider chat attempt stopped model={} kind={} attempt={} code={} reason={} why={}",
                    modelId,
                    request.callKind(),
                    attempt,
                    error.code(),
                    reason,
                    decision.why());
        }
    }

    // A timed-out reply most likely looped; sampling it again with the same seed and cap could loop the same way.
    private static ChatRequest variedAfterTimeout(ChatRequest original, int nextAttempt) {
        final Integer cap = original.maxOutputTokens();
        final Integer lowerCap = cap == null ? null : Math.max(1, cap * RETRY_CAP_NUMERATOR / RETRY_CAP_DENOMINATOR);
        return original.forRetry(nextAttempt, lowerCap);
    }

    // A refused setting or a step down a structured rung drops a setting from this call (and, when remembered, from
    // later ones); a BookLoom fault the server named is written once first.
    private boolean isStepped(ChatRequest request, ProviderCallResult<ChatResponse> call, Tries tries) {
        final @org.jspecify.annotations.Nullable AppError failed = call.result().error();
        if (failed != null) {
            builtWrong.noteIfFirst(providerId, modelId, failed);
        }
        final StructuredRungs.Step step = rungs.step(tries.profile(), request, call, tries.downgraded());
        if (step == null) {
            return false;
        }
        tries.downgraded().add(step.setting());
        noteRefusal(step);
        return true;
    }

    // A refusal, or a step down a structured rung, is not an attempt: the request is repeated without the setting.
    private void noteRefusal(StructuredRungs.Step step) {
        final RejectedCapability rejected = step.setting();
        log.warn(
                "Model refused a setting providerId={} model={} setting={} scope={} instead={} reason={}; the request"
                        + " is repeated without it, not counted as an attempt",
                providerId,
                modelId,
                rejected.settingName(),
                step.scope() == null ? "model" : step.scope(),
                rejected.insteadDid(),
                step.reason());
        if (refusals != null && rejected.isRemembered()) {
            refusals.remember(providerId, modelId, rejected, step.scope());
        }
    }

    private static AttemptResult finished(ProviderCallResult<ChatResponse> call) {
        return new AttemptResult(call, Resend.NONE, Duration.ZERO, false, false);
    }

    private static Result<ChatResponse> cancelled() {
        return Result.err(
                AppError.of(ErrorCode.cancelled, "Inference cancelled", "The provider retry wait was interrupted."));
    }

    // Where one call stands between its attempts: the attempt about to go, the timeouts so far, who hears it, the
    // settings dropped and the reasons whose one resend is spent.
    private record Tries(
            int attempt,
            int timedOut,
            CallAttemptListener listener,
            EnumSet<RejectedCapability> downgraded,
            EnumSet<FailureReason> resentOnce,
            ModelProfile profile) {}

    private record AttemptResult(
            ProviderCallResult<ChatResponse> call, Resend resend, Duration delay, boolean timedOut, boolean refused) {}
}
