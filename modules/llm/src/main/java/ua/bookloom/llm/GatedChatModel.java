package ua.bookloom.llm;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RejectedCapability;
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

    /** Captures the concrete provider client, model id, shared gate, and retry policy for each call. */
    public GatedChatModel(ProviderClient client, String modelId, InferenceGate gate, RetryPolicy retryPolicy) {
        this.client = Objects.requireNonNull(client, "client");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    @Override
    public Result<ChatResponse> chat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        ChatRequest candidate = request;
        final EnumSet<RejectedCapability> downgraded = EnumSet.noneOf(RejectedCapability.class);
        while (true) {
            final ProviderCallResult<ChatResponse> call = callWithTransportRetry(candidate);
            if (call.result().isOk()) {
                return call.result();
            }
            final ChatRequest next = downgrade(candidate, call.rejectedCapability(), downgraded);
            if (next == null) {
                return call.result();
            }
            candidate = next;
        }
    }

    private ProviderCallResult<ChatResponse> callWithTransportRetry(ChatRequest original) {
        ChatRequest request = original;
        int attempt = 1;
        int timedOut = 0;
        while (true) {
            final AttemptResult outcome = attempt(request, attempt, timedOut);
            timedOut += outcome.timedOut() ? 1 : 0;
            if (!outcome.retry()) {
                return outcome.call();
            }
            if (!retryPolicy.sleep(outcome.delay())) {
                return ProviderCallResult.withoutRetryAfter(cancelled());
            }
            attempt++;
            request = outcome.timedOut() ? variedAfterTimeout(original, attempt) : request;
        }
    }

    private AttemptResult attempt(ChatRequest request, int attempt, int timedOutBefore) {
        log.debug(
                "Provider chat attempt started model={} kind={} attempt={} maxAttempts={} seed={} maxOutputTokens={}",
                modelId,
                request.callKind(),
                attempt,
                RetryPolicy.MAX_ATTEMPTS,
                request.seed(),
                request.maxOutputTokens());
        final Result<ProviderCallResult<ChatResponse>> gated = gate.run(() -> Result.ok(client.chat(modelId, request)));
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
        return failure(
                call, Objects.requireNonNull(result.error(), "provider error"), request, attempt, timedOutBefore);
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
            return new AttemptResult(call, false, Duration.ZERO, timedOut);
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
        return new AttemptResult(call, true, delay, timedOut);
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

    private @org.jspecify.annotations.Nullable ChatRequest downgrade(
            ChatRequest request,
            @org.jspecify.annotations.Nullable RejectedCapability rejected,
            EnumSet<RejectedCapability> downgraded) {
        if (rejected == null || !downgraded.add(rejected)) {
            return null;
        }
        return switch (rejected) {
            case STRUCTURED_OUTPUT -> withoutStructuredOutput(request);
            case REASONING_CONTROL -> withoutReasoningControl(request);
        };
    }

    private @org.jspecify.annotations.Nullable ChatRequest withoutStructuredOutput(ChatRequest request) {
        if (request.responseFormat() == null) {
            return null;
        }
        log.info("Provider rejected structured output; retrying once without that capability model={}", modelId);
        return request.withoutResponseFormat();
    }

    private @org.jspecify.annotations.Nullable ChatRequest withoutReasoningControl(ChatRequest request) {
        if (request.reasoningEnabled() == null) {
            return null;
        }
        log.info("Provider rejected reasoning control; retrying once without that capability model={}", modelId);
        return request.withoutReasoning();
    }

    private static AttemptResult finished(ProviderCallResult<ChatResponse> call) {
        return new AttemptResult(call, false, Duration.ZERO, false);
    }

    private static Result<ChatResponse> cancelled() {
        return Result.err(
                AppError.of(ErrorCode.cancelled, "Inference cancelled", "The provider retry wait was interrupted."));
    }

    private record AttemptResult(
            ProviderCallResult<ChatResponse> call, boolean retry, Duration delay, boolean timedOut) {}
}
