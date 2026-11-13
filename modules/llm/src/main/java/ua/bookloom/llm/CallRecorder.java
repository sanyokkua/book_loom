package ua.bookloom.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServedCall;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;

/**
 * Times one model's chat attempts and keeps each in the call ledger with the figures the provider reported, so every
 * stage's cost is counted from what the calls said. Writes the per-call DEBUG line: sizes and figures only, never text.
 */
@Slf4j
final class CallRecorder {

    private static final String NOT_REPORTED = "-";
    private static final String MISSING = "missing";

    private final CallLedger ledger;
    private final String providerId;
    private final String modelId;
    private final ProviderKind kind;
    private final CapIgnoredLog capIgnored;

    CallRecorder(
            final CallLedger ledger,
            final String providerId,
            final String modelId,
            final ProviderKind kind,
            final CapIgnoredLog capIgnored) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.capIgnored = Objects.requireNonNull(capIgnored, "capIgnored");
    }

    /** Records an answered attempt, logs it, and notes once per model when the server ignored the answer cap. */
    void succeeded(final ChatRequest request, final int attempt, final TimedCall timed) {
        record(request, attempt, timed);
        logSucceeded(request, attempt);
        // A client that sent the call again with a raised cap is measured against that cap, not the caller's.
        final ChatRequest sent = Objects.requireNonNullElse(timed.call().sent(), request);
        capIgnored.noteIfIgnored(
                providerId,
                modelId,
                kind,
                sent,
                Objects.requireNonNull(timed.call().result().data(), "reply"));
    }

    /**
     * One provider call and how long it took inside the gate.
     *
     * @param call the provider's answer
     * @param wall the time from sending to the answer
     */
    record TimedCall(ProviderCallResult<ChatResponse> call, Duration wall) {}

    // Timed inside the gate, so the wait for another call's turn is not this call's cost. Wall time is only reported,
    // never decided on, so the monotonic clock is read directly.
    static Result<TimedCall> timed(
            final InferenceGate gate, final Runnable started, final Supplier<ProviderCallResult<ChatResponse>> chat) {
        return gate.run(() -> {
            started.run();
            final long start = System.nanoTime();
            final ProviderCallResult<ChatResponse> call = chat.get();
            return Result.ok(new TimedCall(call, Duration.ofNanos(System.nanoTime() - start)));
        });
    }

    /**
     * Keeps one attempt in the ledger. A call with no kind (a verification, a probe) is not the cost of any stage, so
     * it is not kept.
     */
    void record(final ChatRequest request, final int attempt, final TimedCall timed) {
        final CallKind callKind = request.callKind();
        if (callKind == null) {
            log.debug("Provider chat call not recorded: no call kind model={} attempt={}", modelId, attempt);
            return;
        }
        final Result<ChatResponse> result = timed.call().result();
        final ChatResponse response = result.data();
        final AppError error = result.error();
        final FailureReason reason =
                error == null ? null : FailureReason.in(error.details()).orElse(FailureReason.UNCLASSIFIED);
        final TokenUsage usage = response == null ? null : response.usage();
        final ServedCall served = response == null ? null : response.served();
        ledger.record(
                new CallRecord(callKind, usage, timed.wall(), null, reason, served == null ? null : served.cost()));
        logCall(request, attempt, timed.wall(), usage, outcome(reason, error), served);
    }

    // An answer after a retry is worth an INFO line: it says the retry rules saved the call.
    void logSucceeded(final ChatRequest request, final int attempt) {
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

    private void logCall(
            final ChatRequest request,
            final int attempt,
            final Duration wall,
            final @Nullable TokenUsage usage,
            final String outcome,
            final @Nullable ServedCall served) {
        log.debug(
                "Provider chat call model={} kind={} attempt={} wallMs={} outcome={} prompt={} completion={} cached={}"
                        + " promptEvalMs={} ttftMs={} sourceChars={}{}",
                modelId,
                request.callKind(),
                attempt,
                wall.toMillis(),
                outcome,
                figure(usage == null ? null : usage.prompt()),
                figure(usage == null ? null : usage.completion()),
                figure(usage == null ? null : usage.cachedPrompt()),
                millis(usage == null ? null : usage.promptEval()),
                millis(usage == null ? null : usage.promptEval()),
                request.messages().stream()
                        .mapToInt(message -> message.content().length())
                        .sum(),
                servedFields(served));
    }

    // A failure no reason row explains (a client timeout, an unreachable host) is named by its error code, not lost.
    private static String outcome(final @Nullable FailureReason reason, final @Nullable AppError error) {
        if (reason == null) {
            return "answered";
        }
        return reason == FailureReason.UNCLASSIFIED && error != null
                ? error.code().name()
                : reason.token();
    }

    // Only a reply that names its host or its price (OpenRouter) adds the fields: slugs and a number, never text.
    private static String servedFields(final @Nullable ServedCall served) {
        if (served == null || (served.provider() == null && served.cost() == null)) {
            return "";
        }
        return " servedProvider=" + Objects.requireNonNullElse(served.provider(), NOT_REPORTED)
                + " servedModel=" + Objects.requireNonNullElse(served.model(), NOT_REPORTED)
                + " cost="
                + (served.cost() == null ? NOT_REPORTED : served.cost().toPlainString());
    }

    // A figure the provider did not report is a dash in the log, never a 0.
    // The word "missing", never 0, for a time the server did not report.
    private static String millis(final @Nullable Duration time) {
        return time == null ? MISSING : String.valueOf(time.toMillis());
    }

    private static String figure(final @Nullable Integer value) {
        return value == null ? NOT_REPORTED : String.valueOf(value);
    }
}
