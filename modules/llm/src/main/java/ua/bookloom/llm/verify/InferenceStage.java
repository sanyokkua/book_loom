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
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
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

    InferenceProbe run(final ProviderClient client, final String providerId, final String modelId) {
        log.debug(
                "Provider verification inference stage started provider={} model={} messageCount=1 responseFormatPresent=true reasoningDisabled=true",
                providerId,
                modelId);
        final Sent first = chatProbe(client, providerId, modelId, ProbeRequests.structured(ReasoningLevel.OFF));
        final ProviderCallResult<ChatResponse> call = first.call();
        if (call.result().isErr()) {
            return failed(RetriedCalls.error(call.result()), first.request());
        }
        final ChatResponse response = Objects.requireNonNull(call.result().data(), "chat response");
        if (call.rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            return content(response, first.request());
        }
        if (validStructuredProbe(response)) {
            return new InferenceProbe(
                    StageOutcomes.passed(VerificationStage.INFERENCE, STRUCTURED_OUTPUT_SUPPORTED),
                    response,
                    true,
                    first.request());
        }
        return plainProbe(client, providerId, modelId);
    }

    private Sent chatProbe(
            final ProviderClient client, final String providerId, final String modelId, final ChatRequest request) {
        final ProviderCallResult<ChatResponse> call = send(client, providerId, modelId, request);
        final boolean reasoningRefused = call.rejectedCapability() == RejectedCapability.REASONING_CONTROL;
        final ChatRequest effective = reasoningRefused ? ProbeRequests.structured(null) : request;
        final ProviderCallResult<ChatResponse> retried =
                reasoningRefused ? send(client, providerId, modelId, effective) : call;
        if (retried.rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            final Sent plain = plainCall(client, providerId, modelId, null);
            return new Sent(
                    new ProviderCallResult<>(
                            plain.call().result(), plain.call().retryAfter(), RejectedCapability.STRUCTURED_OUTPUT),
                    plain.request());
        }
        return new Sent(retried, effective);
    }

    private InferenceProbe plainProbe(final ProviderClient client, final String providerId, final String modelId) {
        final Sent plain = plainCall(client, providerId, modelId, ReasoningLevel.OFF);
        if (plain.call().result().isErr()) {
            return failed(RetriedCalls.error(plain.call().result()), plain.request());
        }
        return content(Objects.requireNonNull(plain.call().result().data(), "chat response"), plain.request());
    }

    private Sent plainCall(
            final ProviderClient client,
            final String providerId,
            final String modelId,
            final @Nullable ReasoningLevel reasoning) {
        final ChatRequest request = ProbeRequests.plain(reasoning);
        final ProviderCallResult<ChatResponse> call = send(client, providerId, modelId, request);
        if (call.rejectedCapability() != RejectedCapability.REASONING_CONTROL) {
            return new Sent(call, request);
        }
        final ChatRequest bare = ProbeRequests.plain(null);
        return new Sent(send(client, providerId, modelId, bare), bare);
    }

    private ProviderCallResult<ChatResponse> send(
            final ProviderClient client, final String providerId, final String modelId, final ChatRequest request) {
        return calls.run(() -> client.chat(modelId, request), VerificationStage.INFERENCE, providerId);
    }

    private static InferenceProbe failed(final AppError failure, final ChatRequest sent) {
        return new InferenceProbe(StageOutcomes.inferenceFailure(failure), null, false, sent);
    }

    private static InferenceProbe content(final ChatResponse response, final ChatRequest sent) {
        final StageOutcome outcome = response.content().isBlank()
                ? StageOutcomes.failed(
                        VerificationStage.INFERENCE,
                        AppError.of(
                                ErrorCode.emptyCompletion,
                                "Empty completion",
                                "The provider returned an empty response."))
                : StageOutcomes.passed(VerificationStage.INFERENCE, STRUCTURED_OUTPUT_NOT_CONFIRMED);
        return new InferenceProbe(outcome, response, false, sent);
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
