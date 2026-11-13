package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelVariant;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServedCall;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.dto.OpenAiChatResponse;
import ua.bookloom.llm.engine.EngineTraits;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RawBodies;
import ua.bookloom.llm.http.UnreadableReply;
import ua.bookloom.llm.response.ReasoningBudget;
import ua.bookloom.llm.response.ReasoningScrubber;

/**
 * Reads one OpenAI-shaped chat reply into a response. Where the engine answers any id with its loaded model (LM Studio,
 * Unsloth), a reply that names another model than the one asked for is refused: a translation the chosen model did not
 * write is not kept.
 */
@Slf4j
final class OpenAiReplyReader {

    // OpenRouter ends a choice this way when the hosting company failed; the reply's error object says why.
    private static final String FINISHED_WITH_ERROR = "error";

    private final ProviderConfig config;
    private final ObjectMapper mapper;
    private final boolean servesUnknownModel;
    private final boolean checksReasoningOff;

    OpenAiReplyReader(final ProviderConfig config, final ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        final EngineTraits traits = EngineTraits.of(config.effectiveEngine());
        this.servesUnknownModel = traits.servesUnknownModel();
        this.checksReasoningOff = traits.offSwitch() == EngineTraits.OffSwitch.REASONING_OBJECT;
    }

    Result<ChatResponse> read(final String requestedModel, final HttpReply reply, final Duration elapsed) {
        try {
            final OpenAiChatResponse decoded = mapper.readValue(reply.body(), OpenAiChatResponse.class);
            if (decoded == null) {
                return unreadable(requestedModel, null);
            }
            return response(requestedModel, reply, decoded, elapsed);
        } catch (JsonProcessingException failure) {
            return unreadable(requestedModel, failure);
        }
    }

    // A reply that is not JSON, or has no choice, is read by read(), which says why; here it simply did not spend.
    boolean spentOnReasoning(final ChatRequest request, final String body) {
        try {
            final OpenAiChatResponse decoded = mapper.readValue(body, OpenAiChatResponse.class);
            if (decoded == null
                    || decoded.choices() == null
                    || decoded.choices().isEmpty()) {
                return false;
            }
            final OpenAiChatResponse.@Nullable Choice choice = decoded.choices().getFirst();
            final OpenAiChatResponse.@Nullable Message message = choice == null ? null : choice.message();
            return message != null
                    && ReasoningBudget.spent(request, message.content(), message.hasReasoning(), choice.finishReason());
        } catch (JsonProcessingException _) {
            log.debug("Reasoning budget check skipped: the reply is not JSON");
            return false;
        }
    }

    // OpenRouter counts reasoning tokens on every reply, so a call sent with reasoning off shows whether the endpoint
    // honoured it; the reply is kept either way, and the line says which model's off value to doubt.
    void checkReasoningOff(final String modelId, final ChatRequest request, final String body) {
        if (!checksReasoningOff || request.reasoningEnabled() != ReasoningLevel.OFF) {
            return;
        }
        try {
            final OpenAiChatResponse decoded = mapper.readValue(body, OpenAiChatResponse.class);
            final OpenAiChatResponse.@Nullable Usage usage = decoded == null ? null : decoded.usage();
            final OpenAiChatResponse.@Nullable CompletionTokensDetails details =
                    usage == null ? null : usage.completionTokensDetails();
            final Integer tokens = details == null ? null : details.reasoningTokens();
            log.debug("Reasoning off checked provider={} model={} reasoningTokens={}", config.id(), modelId, tokens);
            if (tokens != null && tokens > 0) {
                log.warn(
                        "Reasoning sent off but the reply reasoned provider={} model={} reasoningTokens={}",
                        config.id(),
                        modelId,
                        tokens);
            }
        } catch (JsonProcessingException _) {
            log.debug("Reasoning off check skipped: the reply is not JSON");
        }
    }

    private Result<ChatResponse> response(
            final String requestedModel,
            final HttpReply reply,
            final OpenAiChatResponse decoded,
            final Duration elapsed) {
        if (decoded.choices() == null
                || decoded.choices().isEmpty()
                || decoded.choices().getFirst() == null) {
            return unreadable(requestedModel, null);
        }
        if (FINISHED_WITH_ERROR.equals(decoded.choices().getFirst().finishReason())) {
            return Result.err(HttpErrorMapper.mapFailedChoice(reply, config, requestedModel));
        }
        final OpenAiChatResponse.Message message = decoded.choices().getFirst().message();
        if (message == null || message.content() == null) {
            return unreadable(requestedModel, null);
        }
        final FinishReason finishReason =
                finishReason(decoded.choices().getFirst().finishReason());
        if (message.content().isBlank()) {
            return emptyCompletion(requestedModel, reply, finishReason);
        }
        final @Nullable AppError wrongModel = checkedModel(requestedModel, decoded.model());
        if (wrongModel != null) {
            return Result.err(wrongModel);
        }
        final TokenUsage usage = OpenAiUsage.of(decoded.usage(), decoded.stats(), decoded.timings(), elapsed);
        logOutcome(requestedModel, reply, finishReason, usage);
        return Result.ok(chatResponse(message.content(), finishReason, usage, reply, decoded));
    }

    private static ChatResponse chatResponse(
            final String content,
            final FinishReason finishReason,
            final @Nullable TokenUsage usage,
            final HttpReply reply,
            final OpenAiChatResponse decoded) {
        return new ChatResponse(
                ReasoningScrubber.scrub(content), finishReason, usage, RawBodies.kept(reply.body()), served(decoded));
    }

    // Slugs and a price only; a reply that names no host and no price (every non-aggregating server) has nothing to
    // add, and a cost of 0 stays 0.
    private static @Nullable ServedCall served(final OpenAiChatResponse decoded) {
        final BigDecimal cost = decoded.usage() == null ? null : decoded.usage().cost();
        if (decoded.provider() == null && cost == null) {
            return null;
        }
        return new ServedCall(decoded.provider(), decoded.model(), cost);
    }

    private void logOutcome(
            final String requestedModel,
            final HttpReply reply,
            final FinishReason finishReason,
            final @Nullable TokenUsage usage) {
        log.debug(
                "OpenAI-compatible chat outcome host={} model={} status={} bodyLength={} finish={} usage={}",
                config.baseUrl().getHost(),
                requestedModel,
                reply.status(),
                reply.body().length(),
                finishReason,
                usage);
    }

    // Where any id is served, a reply another model wrote is refused; elsewhere the mismatch is only named.
    private @Nullable AppError checkedModel(final String requestedModel, final @Nullable String answered) {
        if (answered == null || isSameModel(requestedModel, answered)) {
            return null;
        }
        if (servesUnknownModel) {
            return wrongModel(requestedModel, answered);
        }
        log.warn(
                "OpenAI-compatible provider answered with a different model requested={} answered={}",
                SafeDetails.empty().withModelName(requestedModel).render(),
                SafeDetails.empty().withModelName(answered).render());
        return null;
    }

    // A server may name the model with another letter case, with its variant, or with its publisher in front of the id.
    private static boolean isSameModel(final String requested, final String answered) {
        final String asked = ModelVariant.of(requested).id().toLowerCase(Locale.ROOT);
        final String named = ModelVariant.of(answered).id().toLowerCase(Locale.ROOT);
        return asked.equals(named) || asked.endsWith("/" + named) || named.endsWith("/" + asked);
    }

    private AppError wrongModel(final String requested, final String answered) {
        final String engine = config.effectiveEngine().label();
        log.warn(
                "Reply written by another model reason={} provider={} engine={} requested={} answered={}",
                FailureReason.WRONG_MODEL_SERVED.token(),
                config.id(),
                config.effectiveEngine(),
                requested,
                answered);
        return AppError.of(
                ErrorCode.modelNotFound,
                "Another model answered",
                engine + " answered with " + answered + ", not " + requested + ". Check the model id.",
                SafeDetails.empty()
                        .withReason(FailureReason.WRONG_MODEL_SERVED)
                        .withEndpoint(config.baseUrl())
                        .withModelName(requested)
                        .render(),
                null);
    }

    private Result<ChatResponse> emptyCompletion(
            final String modelId, final HttpReply reply, final FinishReason finishReason) {
        log.debug(
                "OpenAI-compatible chat outcome host={} model={} status={} bodyLength={} code={}",
                config.baseUrl().getHost(),
                modelId,
                reply.status(),
                reply.body().length(),
                ErrorCode.emptyCompletion);
        SafeDetails details = SafeDetails.empty();
        if (finishReason == FinishReason.LENGTH) {
            details = details.withReason(FailureReason.CAP_SPENT);
        }
        return Result.err(AppError.of(
                ErrorCode.emptyCompletion,
                "Model returned an empty response",
                "The OpenAI-compatible provider completed the request without response text.",
                details.withEndpoint(config.baseUrl()).withModelName(modelId).render(),
                null));
    }

    private Result<ChatResponse> unreadable(final String modelId, final @Nullable Throwable cause) {
        return Result.err(UnreadableReply.error(config, modelId, cause));
    }

    private static FinishReason finishReason(final @Nullable String wireReason) {
        return switch (wireReason == null ? "" : wireReason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }
}
