package ua.bookloom.llm.client.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.dto.OllamaChatRequest;
import ua.bookloom.llm.dto.OllamaChatResponse;
import ua.bookloom.llm.dto.OllamaTagsResponse;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpErrorMapper.CallPurpose;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.CapabilityRejections;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.response.ReplySanitizer;

/** Speaks Ollama's native chat dialect so request fields and provider errors retain their intended meaning. */
@Slf4j
public final class OllamaClient implements ProviderClient {

    private static final String VERSION_PATH = "/api/version";
    private static final String TAGS_PATH = "/api/tags";
    private static final String CHAT_PATH = "/api/chat";

    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;

    /** Keeps each client bound to one provider endpoint and the shared HTTP/JSON infrastructure. */
    public OllamaClient(ProviderConfig config, HttpExchange exchange, ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ProviderCallResult<Boolean> probe() {
        log.debug(
                "Ollama probe started provider={} host={}",
                config.id(),
                config.baseUrl().getHost());
        try {
            final ProviderCallResult<HttpReply> call = get(VERSION_PATH, CallPurpose.PROBE, null);
            final Result<HttpReply> result = call.result();
            logOutcome("probe", null, result.error());
            return new ProviderCallResult<>(result.map(ignored -> true), call.retryAfter());
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("probe", null, failure)));
        }
    }

    @Override
    public ProviderCallResult<List<ModelInfo>> listModels() {
        log.debug(
                "Ollama discovery started provider={} host={}",
                config.id(),
                config.baseUrl().getHost());
        try {
            final ProviderCallResult<HttpReply> call = get(TAGS_PATH, CallPurpose.DISCOVERY, null);
            final Result<HttpReply> result = call.result();
            if (result.isErr()) {
                return new ProviderCallResult<>(
                        discoveryError(Objects.requireNonNull(result.error(), "error")), call.retryAfter());
            }
            return new ProviderCallResult<>(
                    readModels(Objects.requireNonNull(result.data(), "reply").body()), call.retryAfter());
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(discoveryFailure(failure)));
        }
    }

    @Override
    public ProviderCallResult<ChatResponse> chat(String modelId, ChatRequest request) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(request, "request");
        log.debug(
                "Ollama chat started provider={} host={} model={} messageCount={} temperature={} responseFormatPresent={}",
                config.id(),
                config.baseUrl().getHost(),
                modelId,
                request.messages().size(),
                request.temperature(),
                request.responseFormat() != null);
        try {
            return chatWithParsedFormat(modelId, request);
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("chat", modelId, failure)));
        }
    }

    @Override
    public ProviderKind kind() {
        return ProviderKind.OLLAMA;
    }

    private ProviderCallResult<ChatResponse> chatWithParsedFormat(String modelId, ChatRequest request) {
        final Result<ParsedFormat> parsedFormat = parseFormat(request.responseFormat(), modelId);
        if (parsedFormat.isErr()) {
            return ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(parsedFormat.error(), "error")));
        }
        final Result<String> requestBody = serializeRequest(
                modelId,
                request,
                Objects.requireNonNull(parsedFormat.data(), "parsed format").schema());
        if (requestBody.isErr()) {
            return ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(requestBody.error(), "error")));
        }
        final ProviderCallResult<HttpReply> call =
                postChat(modelId, Objects.requireNonNull(requestBody.data(), "body"), request);
        final Result<HttpReply> response = call.result();
        if (response.isErr()) {
            final AppError error = Objects.requireNonNull(response.error(), "error");
            logOutcome("chat", modelId, error);
            return new ProviderCallResult<>(Result.err(error), call.retryAfter(), call.rejectedCapability());
        }
        return new ProviderCallResult<>(
                readChatResponse(modelId, Objects.requireNonNull(response.data(), "reply")), call.retryAfter());
    }

    private Result<ParsedFormat> parseFormat(@Nullable ResponseFormat format, String modelId) {
        if (format == null) {
            return Result.ok(new ParsedFormat(null));
        }
        try {
            final JsonNode schema = mapper.readTree(format.jsonSchema());
            if (schema == null || !schema.isObject()) {
                return invalidFormat(modelId, null);
            }
            return Result.ok(new ParsedFormat(schema));
        } catch (JsonProcessingException failure) {
            return invalidFormat(modelId, failure);
        }
    }

    private Result<ParsedFormat> invalidFormat(String modelId, @Nullable Throwable cause) {
        log.warn(
                "Ollama request rejected locally provider={} model={} code={}",
                config.id(),
                modelId,
                ErrorCode.validation);
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Invalid response schema",
                "The response format must contain a JSON schema object.",
                details(modelId),
                cause));
    }

    private Result<String> serializeRequest(String modelId, ChatRequest request, @Nullable JsonNode schema) {
        final List<OllamaChatRequest.Message> messages =
                request.messages().stream().map(this::toOllamaMessage).toList();
        final OllamaChatRequest.Options options =
                request.temperature() == null ? null : new OllamaChatRequest.Options(request.temperature());
        try {
            final OllamaChatRequest payload =
                    new OllamaChatRequest(modelId, messages, false, options, schema, request.reasoningEnabled());
            return Result.ok(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            return Result.err(unexpectedError("serialize chat request", modelId, failure));
        }
    }

    private OllamaChatRequest.Message toOllamaMessage(ChatMessage message) {
        return new OllamaChatRequest.Message(message.role().name().toLowerCase(Locale.ROOT), message.content());
    }

    private ProviderCallResult<HttpReply> postChat(String modelId, String body, ChatRequest request) {
        final Result<HttpReply> response = exchange.post(config, CHAT_PATH, body);
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        return ProviderCallResult.fromHttpReply(
                HttpErrorMapper.map(reply, config, CallPurpose.CHAT, modelId),
                reply,
                CapabilityRejections.from(reply, request));
    }

    private Result<ChatResponse> readChatResponse(String requestedModel, HttpReply reply) {
        final OllamaChatResponse decoded;
        try {
            decoded = mapper.readValue(reply.body(), OllamaChatResponse.class);
        } catch (JsonProcessingException failure) {
            return unreadableChatResponse(requestedModel, failure);
        }
        if (decoded == null) {
            return unreadableChatResponse(requestedModel, null);
        }
        return chatResponse(requestedModel, reply, decoded);
    }

    private Result<ChatResponse> chatResponse(String requestedModel, HttpReply reply, OllamaChatResponse decoded) {
        if (decoded.message() == null) {
            return unreadableChatResponse(requestedModel, null);
        }
        final String content = decoded.message().content();
        if (content == null) {
            return unreadableChatResponse(requestedModel, null);
        }
        if (content.isBlank()) {
            return emptyCompletion(requestedModel, reply);
        }
        logModelMismatch(requestedModel, decoded.model());
        final FinishReason finishReason = finishReason(decoded.doneReason());
        log.debug(
                "Ollama chat outcome host={} model={} status={} bodyLength={} finish={}",
                config.baseUrl().getHost(),
                requestedModel,
                reply.status(),
                reply.body().length(),
                finishReason);
        return Result.ok(new ChatResponse(ReplySanitizer.clean(content), finishReason));
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

    private void logModelMismatch(String requestedModel, @Nullable String answeredModel) {
        if (answeredModel != null && !requestedModel.equals(answeredModel)) {
            log.warn(
                    "Ollama answered with a different model requested={} answered={}",
                    SafeDetails.empty().withModelName(requestedModel).render(),
                    SafeDetails.empty().withModelName(answeredModel).render());
        }
    }

    private Result<List<ModelInfo>> readModels(String body) {
        try {
            final OllamaTagsResponse response = mapper.readValue(body, OllamaTagsResponse.class);
            if (response == null
                    || response.models() == null
                    || response.models().stream().anyMatch(model -> model == null || model.name() == null)) {
                return Result.err(discoveryFailure(null));
            }
            final List<ModelInfo> models = response.models().stream()
                    .map(model -> new ModelInfo(Objects.requireNonNull(model.name(), "model name")))
                    .toList();
            log.debug(
                    "Ollama discovery outcome host={} modelCount={} code=none",
                    config.baseUrl().getHost(),
                    models.size());
            return Result.ok(models);
        } catch (JsonProcessingException failure) {
            return Result.err(discoveryFailure(failure));
        }
    }

    private ProviderCallResult<HttpReply> get(String path, CallPurpose purpose, @Nullable String modelId) {
        final Result<HttpReply> response = exchange.get(config, path);
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        return ProviderCallResult.fromHttpReply(HttpErrorMapper.map(reply, config, purpose, modelId), reply);
    }

    private Result<List<ModelInfo>> discoveryError(AppError error) {
        if (preserveDiscoveryError(error.code())) {
            log.debug(
                    "Ollama discovery outcome host={} code={}", config.baseUrl().getHost(), error.code());
            return Result.err(error);
        }
        log.warn(
                "Ollama discovery degraded host={} originalCode={} code={}",
                config.baseUrl().getHost(),
                error.code(),
                ErrorCode.discoveryFailed);
        return Result.err(AppError.of(
                ErrorCode.discoveryFailed,
                "Model discovery failed",
                "The Ollama model list could not be read.",
                error.details(),
                error.cause()));
    }

    private static boolean preserveDiscoveryError(ErrorCode code) {
        return switch (code) {
            case auth, unreachable, timeout, cancelled -> true;
            case rateLimited,
                    modelNotFound,
                    modelUnavailable,
                    discoveryFailed,
                    upstream,
                    missingCredential,
                    contextWindow,
                    emptyCompletion,
                    validation,
                    internal,
                    busy -> false;
        };
    }

    private AppError discoveryFailure(@Nullable Throwable cause) {
        log.warn("Ollama discovery failed host={} code={}", config.baseUrl().getHost(), ErrorCode.discoveryFailed);
        return AppError.of(
                ErrorCode.discoveryFailed,
                "Model discovery failed",
                "The Ollama model list could not be read.",
                details(null),
                cause);
    }

    private Result<ChatResponse> unreadableChatResponse(String modelId, @Nullable Throwable cause) {
        return Result.err(unexpectedError("read chat response", modelId, cause));
    }

    private AppError unexpectedError(String operation, @Nullable String modelId, @Nullable Throwable failure) {
        log.error(
                "Unexpected Ollama client failure operation={} host={} modelPresent={} failureType={}",
                operation,
                config.baseUrl().getHost(),
                modelId != null,
                failure == null ? "none" : failure.getClass().getSimpleName());
        return AppError.of(
                ErrorCode.internal,
                "Ollama request failed",
                "The Ollama provider could not complete the request.",
                details(modelId),
                failure);
    }

    private @Nullable String details(@Nullable String modelId) {
        SafeDetails safeDetails = SafeDetails.empty().withEndpoint(config.baseUrl());
        return modelId == null
                ? safeDetails.render()
                : safeDetails.withModelName(modelId).render();
    }

    private void logOutcome(String operation, @Nullable String modelId, @Nullable AppError error) {
        log.debug(
                "Ollama {} outcome host={} modelPresent={} code={}",
                operation,
                config.baseUrl().getHost(),
                modelId != null,
                error == null ? "none" : error.code());
    }

    private static FinishReason finishReason(@Nullable String doneReason) {
        if (doneReason == null) {
            return FinishReason.OTHER;
        }
        return switch (doneReason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }

    private record ParsedFormat(@Nullable JsonNode schema) {}
}
