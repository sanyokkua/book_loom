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

    private ProviderCallResult<ChatResponse> callWithTransportRetry(ChatRequest request) {
        int attempt = 1;
        while (true) {
            final AttemptResult outcome = attempt(request, attempt);
            if (!outcome.retry()) {
                return outcome.call();
            }
            if (!retryPolicy.sleep(outcome.delay())) {
                return ProviderCallResult.withoutRetryAfter(cancelled());
            }
            attempt++;
        }
    }

    private AttemptResult attempt(ChatRequest request, int attempt) {
        log.debug(
                "Provider chat attempt started model={} attempt={} maxAttempts={}",
                modelId,
                attempt,
                RetryPolicy.MAX_ATTEMPTS);
        final Result<ProviderCallResult<ChatResponse>> gated = gate.run(() -> Result.ok(client.chat(modelId, request)));
        if (gated.isErr()) {
            return finished(ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(gated.error(), "gate error"))));
        }
        final ProviderCallResult<ChatResponse> call = Objects.requireNonNull(gated.data(), "provider call");
        final Result<ChatResponse> result = call.result();
        if (result.isOk()) {
            log.debug("Provider chat attempt succeeded model={} attempt={}", modelId, attempt);
            return finished(call);
        }
        return failure(call, Objects.requireNonNull(result.error(), "provider error"), attempt);
    }

    private AttemptResult failure(ProviderCallResult<ChatResponse> call, AppError error, int attempt) {
        if (!retryPolicy.shouldRetry(error.code(), attempt)) {
            log.debug("Provider chat attempt stopped model={} attempt={} code={}", modelId, attempt, error.code());
            return finished(call);
        }
        final Duration delay = retryPolicy.delayBeforeRetry(attempt, call.retryAfter());
        log.warn(
                "Retrying provider chat model={} failedAttempt={} nextAttempt={} code={} delay={}",
                modelId,
                attempt,
                attempt + 1,
                error.code(),
                delay);
        return new AttemptResult(call, true, delay);
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
        return new ChatRequest(request.messages(), request.temperature(), null, request.reasoningEnabled());
    }

    private @org.jspecify.annotations.Nullable ChatRequest withoutReasoningControl(ChatRequest request) {
        if (request.reasoningEnabled() == null) {
            return null;
        }
        log.info("Provider rejected reasoning control; retrying once without that capability model={}", modelId);
        return new ChatRequest(request.messages(), request.temperature(), request.responseFormat(), null);
    }

    private static AttemptResult finished(ProviderCallResult<ChatResponse> call) {
        return new AttemptResult(call, false, Duration.ZERO);
    }

    private static Result<ChatResponse> cancelled() {
        return Result.err(
                AppError.of(ErrorCode.cancelled, "Inference cancelled", "The provider retry wait was interrupted."));
    }

    private record AttemptResult(ProviderCallResult<ChatResponse> call, boolean retry, Duration delay) {}
}
