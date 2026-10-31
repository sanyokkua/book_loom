package ua.bookloom.llm.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.client.MessageShaper;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RejectedCapability;

/** The inference stage: a tiny structured request, downgraded to plain text when the server refuses the format. */
@Slf4j
final class InferenceStage {

    private static final String STRUCTURED_OUTPUT_SUPPORTED = "structured output: supported";
    private static final String STRUCTURED_OUTPUT_NOT_CONFIRMED = "structured output: not confirmed";

    private final RetriedCalls calls;
    private final ObjectMapper mapper;

    InferenceStage(final RetriedCalls calls, final ObjectMapper mapper) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    private record Sent(ProviderCallResult<ChatResponse> call, ChatRequest request) {}

    private record Probe(ProviderClient client, String providerId, String modelId, ProbeShaping.Shaper shaper) {}

    InferenceProbe run(
            final ProviderClient client,
            final String providerId,
            final String modelId,
            final ProbeShaping.Shaper shaper) {
        log.debug(
                "Provider verification inference stage started provider={} model={} messageCount=1 responseFormatPresent=true reasoningDisabled=true",
                providerId,
                modelId);
        final Probe probe = new Probe(client, providerId, modelId, shaper);
        final Sent first = chatProbe(probe, ProbeRequests.structured(ReasoningLevel.OFF));
        final ProviderCallResult<ChatResponse> call = first.call();
        if (call.result().isErr()) {
            return failed(RetriedCalls.error(call.result()), first.request());
        }
        final ChatResponse response = Objects.requireNonNull(call.result().data(), "chat response");
        if (call.rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            return content(response, first.request(), ProbeVerdicts.Outcome.REFUSED);
        }
        if (validStructuredProbe(response)) {
            return new InferenceProbe(
                    StageOutcomes.passed(VerificationStage.INFERENCE, STRUCTURED_OUTPUT_SUPPORTED),
                    response,
                    true,
                    first.request());
        }
        return plainProbe(probe);
    }

    private Sent chatProbe(final Probe probe, final ChatRequest request) {
        final Sent call = send(probe, request);
        final boolean reasoningRefused = call.call().rejectedCapability() == RejectedCapability.REASONING_CONTROL;
        final Sent retried = reasoningRefused ? send(probe, ProbeRequests.structured(null)) : call;
        if (retried.call().rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            final Sent plain = plainCall(probe, null);
            return new Sent(
                    new ProviderCallResult<>(
                            plain.call().result(), plain.call().retryAfter(), RejectedCapability.STRUCTURED_OUTPUT),
                    plain.request());
        }
        return retried;
    }

    private InferenceProbe plainProbe(final Probe probe) {
        final Sent plain = plainCall(probe, ReasoningLevel.OFF);
        if (plain.call().result().isErr()) {
            return failed(RetriedCalls.error(plain.call().result()), plain.request());
        }
        return content(
                Objects.requireNonNull(plain.call().result().data(), "chat response"),
                plain.request(),
                ProbeVerdicts.Outcome.IGNORED);
    }

    private Sent plainCall(final Probe probe, final @Nullable ReasoningLevel reasoning) {
        final Sent call = send(probe, ProbeRequests.plain(reasoning));
        if (call.call().rejectedCapability() != RejectedCapability.REASONING_CONTROL) {
            return call;
        }
        return send(probe, ProbeRequests.plain(null));
    }

    // A template that refuses the system role gets the probe once more with the system text folded in, as a run's call.
    private Sent send(final Probe probe, final ChatRequest request) {
        final ChatRequest shaped = probe.shaper().shaped(request);
        final ProviderCallResult<ChatResponse> call = exchange(probe, shaped);
        if (call.rejectedCapability() != RejectedCapability.SYSTEM_ROLE || !MessageShaper.hasSystem(shaped)) {
            return new Sent(call, shaped);
        }
        log.warn(
                "Provider verification probe refused the system role reason={} provider={} model={}; sending it"
                        + " folded",
                FailureReason.SYSTEM_ROLE_UNSUPPORTED.token(),
                probe.providerId(),
                probe.modelId());
        probe.shaper().systemRoleRefused();
        final ChatRequest folded = probe.shaper().shaped(request);
        return new Sent(exchange(probe, folded), folded);
    }

    private ProviderCallResult<ChatResponse> exchange(final Probe probe, final ChatRequest request) {
        return calls.run(
                () -> probe.client().chat(probe.modelId(), request), VerificationStage.INFERENCE, probe.providerId());
    }

    private static InferenceProbe failed(final AppError failure, final ChatRequest sent) {
        return new InferenceProbe(StageOutcomes.inferenceFailure(failure), null, false, sent);
    }

    // Only a plain probe that answered tells how the schema was treated; an empty one tells nothing.
    private static InferenceProbe content(
            final ChatResponse response, final ChatRequest sent, final ProbeVerdicts.Outcome schema) {
        final boolean blank = response.content().isBlank();
        log.debug("Provider verification plain probe answered blank={} schema={}", blank, schema);
        final StageOutcome outcome = blank
                ? StageOutcomes.failed(
                        VerificationStage.INFERENCE,
                        AppError.of(
                                ErrorCode.emptyCompletion,
                                "Empty completion",
                                "The provider returned an empty response."))
                : StageOutcomes.passed(VerificationStage.INFERENCE, STRUCTURED_OUTPUT_NOT_CONFIRMED);
        return new InferenceProbe(outcome, response, false, sent, blank ? null : schema);
    }

    private boolean validStructuredProbe(final ChatResponse response) {
        try {
            final JsonNode node = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(response.content());
            return node != null
                    && node.isObject()
                    && node.size() == 1
                    && "ok".equals(node.path("status").asText());
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }
}
