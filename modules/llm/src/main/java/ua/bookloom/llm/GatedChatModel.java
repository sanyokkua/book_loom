package ua.bookloom.llm;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.llm.gate.InferenceGate;
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
    private final BiFunction<String, String, ModelSettings> settings;
    private final @org.jspecify.annotations.Nullable RefusalMemory refusals;

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
        this.refusals = refusals;
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.client = Objects.requireNonNull(client, "client");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    @Override
    public @org.jspecify.annotations.Nullable Integer contextWindowOverride() {
        return settings.apply(providerId, modelId).contextWindow();
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
        return callWithTransportRetry(applied(request, downgraded), attempts, downgraded)
                .result();
    }

    // Read once per call, so every retry of it keeps the same settings and a save lands on the next call. The
    // refusals the model's record already holds are stripped here and join the set of settings not to send again.
    private ChatRequest applied(ChatRequest request, EnumSet<RejectedCapability> downgraded) {
        final ModelSettings stored = settings.apply(providerId, modelId);
        final Double temperature =
                switch (stored.temperatureChoice()) {
                    case CHOOSE -> request.temperature();
                    case VALUE -> stored.temperature();
                    case OMIT -> null;
                };
        final Integer requestCap = request.maxOutputTokens();
        final Integer maxOutput = stored.maxOutput();
        final Integer cap =
                requestCap != null && maxOutput != null ? Integer.valueOf(Math.min(requestCap, maxOutput)) : requestCap;
        final OutputLengthField field = outputField(request, stored);
        if (refusals != null) {
            downgraded.addAll(refusals.remembered(stored));
        }
        log.debug(
                "Applied model settings model={} kind={} temperatureChoice={} temperature={} cap={} outputField={}"
                        + " refused={}",
                modelId,
                request.callKind(),
                stored.temperatureChoice(),
                temperature,
                cap,
                field,
                downgraded);
        return SettingDrops.stripAll(request.withModelSettings(temperature, cap, field), downgraded);
    }

    private static @org.jspecify.annotations.Nullable OutputLengthField outputField(
            ChatRequest request, ModelSettings stored) {
        return request.outputField() == null && stored.outputField() != OutputLengthField.MAX_TOKENS
                ? stored.outputField()
                : request.outputField();
    }

    private ProviderCallResult<ChatResponse> callWithTransportRetry(
            ChatRequest first, CallAttemptListener attempts, EnumSet<RejectedCapability> downgraded) {
        ChatRequest original = first;
        ChatRequest request = first;
        int attempt = 1;
        int timedOut = 0;
        while (true) {
            final AttemptResult outcome = attempt(request, new Tries(attempt, timedOut, attempts, downgraded));
            if (outcome.refused()) {
                // A refusal is not an attempt: the same attempt number goes out again, minus the refused setting.
                original = SettingDrops.stripAll(original, downgraded);
                request = SettingDrops.stripAll(request, downgraded);
                continue;
            }
            timedOut += outcome.timedOut() ? 1 : 0;
            if (!outcome.retry()) {
                return outcome.call();
            }
            if (!retryPolicy.sleep(outcome.delay())) {
                return ProviderCallResult.withoutRetryAfter(cancelled());
            }
            attempt++;
            request = outcome.timedOut()
                    ? SettingDrops.stripAll(variedAfterTimeout(original, attempt), downgraded)
                    : request;
        }
    }

    private AttemptResult attempt(ChatRequest request, Tries tries) {
        final int attempt = tries.attempt();
        final CallAttempt reported = reported(request, tries);
        logStarted(request, reported);
        final Result<ProviderCallResult<ChatResponse>> gated = gate.run(() -> {
            tries.listener().started(reported);
            return Result.ok(client.chat(modelId, request));
        });
        if (gated.isErr()) {
            return finished(ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(gated.error(), "gate error"))));
        }
        final ProviderCallResult<ChatResponse> call = Objects.requireNonNull(gated.data(), "provider call");
        final Result<ChatResponse> result = call.result();
        if (result.isOk()) {
            logSucceeded(request, attempt);
            return finished(call);
        }
        final AppError error = Objects.requireNonNull(result.error(), "provider error");
        if (isNewRefusal(call, request, tries.downgraded())) {
            final RejectedCapability rejected = Objects.requireNonNull(call.rejectedCapability(), "refused setting");
            tries.downgraded().add(rejected);
            noteRefusal(rejected);
            return new AttemptResult(call, false, Duration.ZERO, false, true);
        }
        tries.listener().failed(reported, error.code());
        return failure(call, error, request, attempt, tries.timedOut());
    }

    // An attempt after the timeout budget is spent is the last one if it stalls too, which is what a waiting person
    // needs to know; a transient failure of another kind may still earn one more within the total budget.
    private CallAttempt reported(ChatRequest request, Tries tries) {
        final int stallBound = tries.attempt() + RetryPolicy.MAX_TIMEOUT_ATTEMPTS - 1 - tries.timedOut();
        final int maxAttempts = Math.max(tries.attempt(), Math.min(RetryPolicy.MAX_ATTEMPTS, stallBound));
        return new CallAttempt(tries.attempt(), maxAttempts, client.chatTimeout(request), request.maxOutputTokens());
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

    private void logSucceeded(ChatRequest request, int attempt) {
        if (attempt > 1) {
            log.info(
                    "Provider chat answered on a retry model={} kind={} attempt={}",
                    modelId,
                    request.callKind(),
                    attempt);
        } else {
            log.debug(
                    "Provider chat attempt succeeded model={} kind={} attempt={}",
                    modelId,
                    request.callKind(),
                    attempt);
        }
    }

    private AttemptResult failure(
            ProviderCallResult<ChatResponse> call,
            AppError error,
            ChatRequest request,
            int attempt,
            int timedOutBefore) {
        final boolean timedOut = error.code() == ErrorCode.timeout;
        final boolean retry = timedOut
                ? retryPolicy.shouldRetryTimeout(attempt, timedOutBefore + 1)
                : retryPolicy.shouldRetry(error.code(), attempt);
        if (!retry) {
            logStopped(request, attempt, error);
            return new AttemptResult(call, false, Duration.ZERO, timedOut, false);
        }
        final Duration delay = retryPolicy.delayBeforeRetry(attempt, call.retryAfter());
        log.warn(
                "Retrying provider chat model={} kind={} failedAttempt={} nextAttempt={} code={} delay={} varied={}",
                modelId,
                request.callKind(),
                attempt,
                attempt + 1,
                error.code(),
                delay,
                timedOut);
        return new AttemptResult(call, true, delay, timedOut, false);
    }

    private void logStopped(ChatRequest request, int attempt, AppError error) {
        if (error.code().isRetryable()) {
            log.warn(
                    "Provider chat gave up model={} kind={} attempts={} code={}",
                    modelId,
                    request.callKind(),
                    attempt,
                    error.code());
        } else {
            log.debug(
                    "Provider chat attempt stopped model={} kind={} attempt={} code={}",
                    modelId,
                    request.callKind(),
                    attempt,
                    error.code());
        }
    }

    // A timed-out reply most likely looped; sampling it again with the same seed and cap could loop the same way.
    private static ChatRequest variedAfterTimeout(ChatRequest original, int nextAttempt) {
        final Integer cap = original.maxOutputTokens();
        final Integer lowerCap = cap == null ? null : Math.max(1, cap * RETRY_CAP_NUMERATOR / RETRY_CAP_DENOMINATOR);
        return original.forRetry(nextAttempt, lowerCap);
    }

    // A reply that refuses a setting the request carried, and that has not been refused on this call before.
    private static boolean isNewRefusal(
            ProviderCallResult<ChatResponse> call, ChatRequest request, EnumSet<RejectedCapability> downgraded) {
        final RejectedCapability rejected = call.rejectedCapability();
        return rejected != null && !downgraded.contains(rejected) && SettingDrops.without(request, rejected) != null;
    }

    private void noteRefusal(RejectedCapability rejected) {
        log.warn(
                "Model refused a setting providerId={} model={} setting={} instead={}; the request is repeated"
                        + " without it, not counted as an attempt",
                providerId,
                modelId,
                rejected.settingName(),
                rejected.insteadDid());
        if (refusals != null) {
            refusals.remember(providerId, modelId, rejected);
        }
    }

    private static AttemptResult finished(ProviderCallResult<ChatResponse> call) {
        return new AttemptResult(call, false, Duration.ZERO, false, false);
    }

    private static Result<ChatResponse> cancelled() {
        return Result.err(
                AppError.of(ErrorCode.cancelled, "Inference cancelled", "The provider retry wait was interrupted."));
    }

    /** Where one call stands between its attempts: the attempt about to go, the timeouts so far, who hears it. */
    private record Tries(
            int attempt, int timedOut, CallAttemptListener listener, EnumSet<RejectedCapability> downgraded) {}

    private record AttemptResult(
            ProviderCallResult<ChatResponse> call, boolean retry, Duration delay, boolean timedOut, boolean refused) {}
}
