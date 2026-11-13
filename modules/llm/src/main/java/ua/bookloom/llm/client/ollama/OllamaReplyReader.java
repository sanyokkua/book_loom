package ua.bookloom.llm.client.ollama;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
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
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.dto.OllamaChatResponse;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RawBodies;
import ua.bookloom.llm.response.ReasoningBudget;
import ua.bookloom.llm.response.ReasoningScrubber;

/**
 * Reads an Ollama chat reply, streamed or whole, into one {@link ChatResponse}: the content of every part joined in
 * order, and the finish reason and token counts of the last part that states them. A whole reply is a stream of one
 * part, so both read the same way.
 */
@Slf4j
final class OllamaReplyReader {

    /** The done reason a reply cut as a runaway is read with. */
    private static final String CUT_REASON = "length";

    private final ProviderConfig config;
    private final ObjectMapper mapper;
    private final BiFunction<String, @Nullable Throwable, AppError> unreadable;

    /**
     * Binds the reader to one endpoint.
     *
     * @param unreadable builds the error for a reply that cannot be read, from the model id and the failure, if any;
     *     the client owns it so every unexpected failure is logged and worded in one place
     */
    OllamaReplyReader(
            ProviderConfig config, ObjectMapper mapper, BiFunction<String, @Nullable Throwable, AppError> unreadable) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.unreadable = Objects.requireNonNull(unreadable, "unreadable");
    }

    Result<ChatResponse> read(String requestedModel, HttpReply reply) {
        final List<OllamaChatResponse> parts;
        try {
            parts = parts(reply);
        } catch (IOException | RuntimeException failure) {
            return Result.err(unreadable.apply(requestedModel, failure));
        }
        log.debug("Ollama reply read model={} parts={}", requestedModel, parts.size());
        if (parts.isEmpty()) {
            return Result.err(unreadable.apply(requestedModel, null));
        }
        if (parts.stream().anyMatch(part -> part.error() != null)) {
            return stoppedWithError(requestedModel, parts);
        }
        return chatResponse(requestedModel, reply, merged(parts, reply.cut()));
    }

    /**
     * Whether a reply spent its whole answer thinking; a reply that cannot be read, or stopped with an error, did not
     * (read() says why).
     */
    boolean spentOnReasoning(ChatRequest request, HttpReply reply) {
        try {
            final List<OllamaChatResponse> parts = parts(reply);
            if (parts.isEmpty() || parts.stream().anyMatch(part -> part.error() != null)) {
                return false;
            }
            final OllamaChatResponse merged = merged(parts, reply.cut());
            final OllamaChatResponse.Message message = merged.message();
            final String thinking = message == null ? null : message.thinking();
            return ReasoningBudget.spent(
                    request,
                    message == null ? null : message.content(),
                    thinking != null && !thinking.isBlank(),
                    merged.doneReason());
        } catch (IOException | RuntimeException _) {
            log.debug("Reasoning budget check skipped: the reply could not be read");
            return false;
        }
    }

    private List<OllamaChatResponse> parts(HttpReply reply) throws IOException {
        try (MappingIterator<@Nullable OllamaChatResponse> values =
                mapper.readerFor(OllamaChatResponse.class).readValues(reply.body())) {
            return values.readAll().stream().filter(Objects::nonNull).toList();
        }
    }

    /** A reply cut as a runaway ends without the provider's last part; it finished for length, as a cap would. */
    private static OllamaChatResponse merged(List<OllamaChatResponse> parts, boolean cut) {
        final StringBuilder content = new StringBuilder();
        final StringBuilder thinking = new StringBuilder();
        boolean anyContent = false;
        for (final OllamaChatResponse part : parts) {
            final OllamaChatResponse.Message message = part.message();
            if (message != null && message.content() != null) {
                content.append(message.content());
                anyContent = true;
            }
            if (message != null && message.thinking() != null) {
                thinking.append(message.thinking());
            }
        }
        final OllamaChatResponse last = parts.getLast();
        return new OllamaChatResponse(
                last.model(),
                mergedMessage(anyContent ? content.toString() : null, thinking.toString()),
                cut ? CUT_REASON : last.doneReason(),
                last.promptEvalCount(),
                last.evalCount(),
                last.evalDuration(),
                last.promptEvalDuration(),
                last.promptEvalCachedCount(),
                last.loadDuration(),
                null);
    }

    private Result<ChatResponse> chatResponse(String requestedModel, HttpReply reply, OllamaChatResponse decoded) {
        final OllamaChatResponse.Message message = decoded.message();
        if (message == null || message.content() == null) {
            return Result.err(unreadable.apply(requestedModel, null));
        }
        final String content = Objects.requireNonNull(message.content());
        if (content.isBlank()) {
            return emptyCompletion(requestedModel, reply);
        }
        logModelMismatch(requestedModel, decoded.model());
        final FinishReason finishReason = finishReason(decoded.doneReason());
        final TokenUsage usage = usage(decoded);
        log.debug(
                "Ollama chat outcome host={} model={} status={} bodyLength={} finish={} usage={}",
                config.baseUrl().getHost(),
                requestedModel,
                reply.status(),
                reply.body().length(),
                finishReason,
                usage);
        return Result.ok(
                new ChatResponse(ReasoningScrubber.scrub(content), finishReason, usage, RawBodies.kept(reply.body())));
    }

    private Result<ChatResponse> stoppedWithError(String modelId, List<OllamaChatResponse> parts) {
        final String text = parts.stream()
                .map(OllamaChatResponse::error)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
        final AppError error = HttpErrorMapper.mapStreamedError(config, modelId, text);
        log.warn(
                "Ollama stopped a reply with an error host={} model={} parts={} code={} reason={}",
                config.baseUrl().getHost(),
                modelId,
                parts.size(),
                error.code(),
                FailureReason.tokenIn(error.details()));
        return Result.err(error);
    }

    private Result<ChatResponse> emptyCompletion(String modelId, HttpReply reply) {
        log.debug(
                "Ollama chat outcome host={} model={} status={} bodyLength={} code={}",
                config.baseUrl().getHost(),
                modelId,
                reply.status(),
                reply.body().length(),
                ErrorCode.emptyCompletion);
        return Result.err(AppError.of(
                ErrorCode.emptyCompletion,
                "Model returned an empty response",
                "Ollama completed the request without response text.",
                details(modelId),
                null));
    }

    private static @Nullable TokenUsage usage(OllamaChatResponse decoded) {
        final Long evalDuration = decoded.evalDuration();
        final Long promptEvalDuration = decoded.promptEvalDuration();
        final Long loadDuration = decoded.loadDuration();
        if (decoded.promptEvalCount() == null
                && decoded.evalCount() == null
                && evalDuration == null
                && promptEvalDuration == null
                && decoded.promptEvalCachedCount() == null) {
            return null;
        }
        log.debug(
                "Ollama usage promptTokens={} cachedPromptTokens={} completionTokens={} loadNanos={}",
                decoded.promptEvalCount(),
                decoded.promptEvalCachedCount(),
                decoded.evalCount(),
                loadDuration);
        return new TokenUsage(
                decoded.promptEvalCount(),
                decoded.evalCount(),
                evalDuration == null ? null : Duration.ofNanos(evalDuration),
                promptEvalDuration == null ? null : Duration.ofNanos(promptEvalDuration),
                decoded.promptEvalCachedCount(),
                loadDuration == null ? null : Duration.ofNanos(loadDuration));
    }

    private void logModelMismatch(String requestedModel, @Nullable String answeredModel) {
        if (answeredModel != null && !requestedModel.equals(answeredModel)) {
            log.warn(
                    "Ollama answered with a different model requested={} answered={}",
                    SafeDetails.empty().withModelName(requestedModel).render(),
                    SafeDetails.empty().withModelName(answeredModel).render());
        }
    }

    private @Nullable String details(String modelId) {
        return SafeDetails.empty()
                .withEndpoint(config.baseUrl())
                .withModelName(modelId)
                .render();
    }

    private static FinishReason finishReason(@Nullable String doneReason) {
        if (doneReason == null) {
            return FinishReason.OTHER;
        }
        return switch (doneReason) {
            case "stop" -> FinishReason.STOP;
            case CUT_REASON -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    private static OllamaChatResponse.@Nullable Message mergedMessage(
            final @Nullable String content, final String thinking) {
        return content != null || !thinking.isEmpty() ? new OllamaChatResponse.Message(content, thinking) : null;
    }
}
