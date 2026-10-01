package ua.bookloom.llm.client.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.dto.OllamaChatRequest;
import ua.bookloom.llm.dto.OllamaTagsResponse;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpErrorMapper.CallPurpose;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RequestTimeouts;
import ua.bookloom.llm.provider.CapabilityRejections;
import ua.bookloom.llm.provider.DiscoveryErrors;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;

/** Speaks Ollama's native chat dialect so request fields and provider errors retain their intended meaning. */
@Slf4j
public final class OllamaClient implements ProviderClient {

    private static final String VERSION_PATH = "/api/version";
    private static final String TAGS_PATH = "/api/tags";
    private static final String CHAT_PATH = "/api/chat";

    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final OllamaReplyReader replies;

    /** Keeps each client bound to one provider endpoint and the shared HTTP/JSON infrastructure. */
    public OllamaClient(ProviderConfig config, HttpExchange exchange, ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.replies = new OllamaReplyReader(
                config, mapper, (modelId, cause) -> unexpectedError("read chat response", modelId, cause));
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
                "Ollama chat started provider={} host={} model={} messageCount={} temperature={} contextWindow={} maxOutputTokens={} responseFormatPresent={}",
                config.id(),
                config.baseUrl().getHost(),
                modelId,
                request.messages().size(),
                request.temperature(),
                request.contextWindow(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens(),
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
                replies.read(modelId, Objects.requireNonNull(response.data(), "reply")), call.retryAfter());
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
        final OllamaChatRequest.Options options = new OllamaChatRequest.Options(
                request.temperature(), request.contextWindow(), request.maxOutputTokens(), request.seed());
        try {
            final OllamaChatRequest payload =
                    new OllamaChatRequest(modelId, messages, true, options, schema, request.reasoningEnabled());
            return Result.ok(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            return Result.err(unexpectedError("serialize chat request", modelId, failure));
        }
    }

    private OllamaChatRequest.Message toOllamaMessage(ChatMessage message) {
        return new OllamaChatRequest.Message(message.role().name().toLowerCase(Locale.ROOT), message.content());
    }

    @Override
    public Duration chatTimeout(ChatRequest request) {
        return RequestTimeouts.forChat(config, Objects.requireNonNull(request, "request"))
                .requestTimeout();
    }

    private ProviderCallResult<HttpReply> postChat(String modelId, String body, ChatRequest request) {
        final ProviderConfig chatConfig = RequestTimeouts.forChat(config, request);
        final Result<HttpReply> response =
                exchange.postStreamed(chatConfig, CHAT_PATH, body, RequestTimeouts.streamIdle(config));
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        return ProviderCallResult.fromHttpReply(
                HttpErrorMapper.map(reply, chatConfig, CallPurpose.CHAT, modelId),
                reply,
                CapabilityRejections.from(reply, request));
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
        if (DiscoveryErrors.preserve(error.code())) {
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

    private AppError discoveryFailure(@Nullable Throwable cause) {
        log.warn("Ollama discovery failed host={} code={}", config.baseUrl().getHost(), ErrorCode.discoveryFailed);
        return AppError.of(
                ErrorCode.discoveryFailed,
                "Model discovery failed",
                "The Ollama model list could not be read.",
                details(null),
                cause);
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

    private record ParsedFormat(@Nullable JsonNode schema) {}
}
