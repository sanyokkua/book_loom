package ua.bookloom.llm.verify;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.retry.RetryPolicy;

/** Runs one provider call through the shared gate with the typed retry policy; the gate is free during each wait. */
@Slf4j
final class RetriedCalls {

    private final InferenceGate gate;
    private final RetryPolicy retryPolicy;

    RetriedCalls(final InferenceGate gate, final RetryPolicy retryPolicy) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    <T> ProviderCallResult<T> run(
            final Supplier<ProviderCallResult<T>> operation, final VerificationStage stage, final String providerId) {
        int attempt = 1;
        while (true) {
            final Result<ProviderCallResult<T>> gated = gate.run(() -> Result.ok(operation.get()));
            if (gated.isErr()) {
                return ProviderCallResult.withoutRetryAfter(Result.err(error(gated)));
            }
            final ProviderCallResult<T> call = Objects.requireNonNull(gated.data(), "provider call");
            if (call.result().isOk()) {
                return call;
            }
            final AppError failure = error(call.result());
            if (!retryPolicy.shouldRetry(failure.code(), attempt)) {
                return call;
            }
            final Duration delay = retryPolicy.delayBeforeRetry(attempt, call.retryAfter());
            log.warn(
                    "Retrying provider verification provider={} stage={} failedAttempt={} nextAttempt={} code={} delay={}",
                    providerId,
                    stage,
                    attempt,
                    attempt + 1,
                    failure.code(),
                    delay);
            if (!retryPolicy.sleep(delay)) {
                return cancelled(stage);
            }
            attempt++;
        }
    }

    static <T> AppError error(final Result<T> result) {
        return Objects.requireNonNull(result.error(), "provider error");
    }

    private static <T> ProviderCallResult<T> cancelled(final VerificationStage stage) {
        return ProviderCallResult.withoutRetryAfter(Result.err(AppError.of(
                ErrorCode.cancelled,
                "Verification cancelled",
                "The provider retry wait was interrupted during " + stage.name().toLowerCase(Locale.ROOT)
                        + " verification.")));
    }
}
