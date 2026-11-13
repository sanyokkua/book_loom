package ua.bookloom.llm.client.ollama;

import static ua.bookloom.util.contract.ArgNames.ERROR;
import static ua.bookloom.util.contract.ArgNames.REPLY;
import static ua.bookloom.util.contract.ArgNames.REQUEST;

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
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.capability.ModelFactsReader;
import ua.bookloom.llm.dto.OllamaChatRequest;
import ua.bookloom.llm.dto.OllamaTagsResponse;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpErrorMapper.CallPurpose;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RequestTimeouts;
import ua.bookloom.llm.http.UnreadableReply;
import ua.bookloom.llm.profile.ListedModels;
import ua.bookloom.llm.provider.CapabilityRejections;
import ua.bookloom.llm.provider.DiscoveryErrors;
import ua.bookloom.llm.provider.EndpointPaths;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.response.ReasoningBudget;

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
    private final ModelFactsReader factsReader;
    private final ListedModels listed = new ListedModels();
    private final EngineFacts facts;
    private final boolean reasks;

    /** Keeps each client bound to one provider endpoint and the shared HTTP/JSON infrastructure. */
    public OllamaClient(ProviderConfig config, HttpExchange exchange, ObjectMapper mapper) {
        this(config, exchange, mapper, new EngineFacts(exchange, mapper));
    }

    /** As above, sharing the session's engine facts so what a read finds is kept for the session. */
    public OllamaClient(ProviderConfig config, HttpExchange exchange, ObjectMapper mapper, EngineFacts facts) {
        this(config, exchange, mapper, facts, true);
    }

    private OllamaClient(
            ProviderConfig config, HttpExchange exchange, ObjectMapper mapper, EngineFacts facts, boolean reasks) {
        this.facts = facts;
        this.reasks = reasks;
        this.factsReader = new ModelFactsReader(exchange, mapper, Objects.requireNonNull(facts, "facts"));
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.replies = new OllamaReplyReader(
                config, mapper, (modelId, cause) -> UnreadableReply.error(config, modelId, cause));
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
        } catch (Exception failure) {
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
            final ProviderCallResult<HttpReply> call =
                    get(EndpointPaths.modelsPath(config, TAGS_PATH), CallPurpose.DISCOVERY, null);
            final Result<HttpReply> result = call.result();
            if (result.isErr()) {
                return new ProviderCallResult<>(
                        discoveryError(Objects.requireNonNull(result.error(), ERROR)), call.retryAfter());
            }
            return new ProviderCallResult<>(
                    readModels(Objects.requireNonNull(result.data(), REPLY).body()), call.retryAfter());
        } catch (Exception failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(discoveryFailure(failure)));
        }
    }

    @Override
    public ProviderCallResult<ChatResponse> chat(String modelId, ChatRequest request) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(request, REQUEST);
        log.debug(
                "Ollama chat started provider={} host={} model={} messageCount={} temperature={} contextWindow={} maxOutputTokens={} responseFormatPresent={} keepLoaded={}",
                config.id(),
                config.baseUrl().getHost(),
                modelId,
                request.messages().size(),
                request.temperature(),
                request.contextWindow(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens(),
                request.responseFormat() != null,
                request.keepLoaded());
        try {
            return chatWithParsedFormat(modelId, request);
        } catch (Exception failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("chat", modelId, failure)));
        }
    }

    @Override
    public String requestBody(String modelId, ChatRequest request) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(request, REQUEST);
        try {
            final Result<ParsedFormat> parsed = parseFormat(request.responseFormat(), modelId);
            if (parsed.isErr()) {
                return "";
            }
            final Result<String> body = serializeRequest(
                    modelId,
                    request,
                    Objects.requireNonNull(parsed.data(), "parsed format").schema());
            return body.isErr() ? "" : Objects.requireNonNull(body.data(), "body");
        } catch (RuntimeException failure) {
            log.warn(
                    "the request body of model {} could not be written: {}",
                    modelId,
                    failure.getClass().getSimpleName());
            return "";
        }
    }

    // A check sends its request once as asked, with no repeat after a reply that spent its cap thinking.
    @Override
    public ProviderClient forChecks() {
        return new OllamaClient(config, exchange, mapper, facts, false);
    }

    @Override
    public ModelFacts facts(String modelId) {
        return factsReader.read(config, modelId);
    }

    @Override
    public ProviderKind kind() {
        return ProviderKind.OLLAMA;
    }

    private ProviderCallResult<ChatResponse> chatWithParsedFormat(String modelId, ChatRequest request) {
        final Result<ParsedFormat> parsedFormat = parseFormat(request.responseFormat(), modelId);
        if (parsedFormat.isErr()) {
            return ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(parsedFormat.error(), ERROR)));
        }
        return exchangeChat(
                modelId,
                request,
                Objects.requireNonNull(parsedFormat.data(), "parsed format").schema(),
                reasks);
    }

    // A reply that spent its cap thinking is asked for once more (thinking off, or a raised cap), never twice; a repeat
    // that spends it again is reasoning-exhausted. A check sends its request once as asked: that reply is the finding.
    private ProviderCallResult<ChatResponse> exchangeChat(
            String modelId, ChatRequest request, @Nullable JsonNode schema, boolean mayReask) {
        final Result<String> requestBody = serializeRequest(modelId, request, schema);
        if (requestBody.isErr()) {
            return ProviderCallResult.withoutRetryAfter(Result.err(Objects.requireNonNull(requestBody.error(), ERROR)));
        }
        final ProviderCallResult<HttpReply> call =
                postChat(modelId, Objects.requireNonNull(requestBody.data(), "body"), request);
        final Result<HttpReply> response = call.result();
        if (response.isErr()) {
            final AppError error = Objects.requireNonNull(response.error(), ERROR);
            logOutcome("chat", modelId, error);
            return new ProviderCallResult<>(Result.err(error), call.retryAfter(), call.rejectedCapability());
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), REPLY);
        return ReasoningBudget.settled(
                request,
                replies.spentOnReasoning(request, reply),
                mayReask,
                () -> ReasoningBudget.exhausted(config, modelId),
                again -> exchangeChat(modelId, again, schema, false),
                () -> new ProviderCallResult<>(replies.read(modelId, reply), call.retryAfter()));
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
                request.temperature(),
                request.contextWindow(),
                request.maxOutputTokens(),
                request.seed(),
                request.samplingOrNone().topP(),
                request.samplingOrNone().topK(),
                request.samplingOrNone().minP(),
                request.samplingOrNone().repeatPenalty());
        try {
            final OllamaChatRequest payload = new OllamaChatRequest(
                    modelId,
                    messages,
                    true,
                    options,
                    schema,
                    OllamaReasoning.thinkControl(request.reasoningEnabled()),
                    OllamaWindow.keepAlive(request),
                    OllamaWindow.truncate(request));
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
        return RequestTimeouts.forChat(config, Objects.requireNonNull(request, REQUEST))
                .requestTimeout();
    }

    private ProviderCallResult<HttpReply> postChat(String modelId, String body, ChatRequest request) {
        final ProviderConfig chatConfig = RequestTimeouts.forChat(config, request);
        final Result<HttpReply> response = exchange.postStreamed(
                chatConfig,
                CHAT_PATH,
                body,
                RequestTimeouts.streamIdle(config),
                ThinkingLines.maxLines(request),
                ThinkingLines.counted(request));
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), REPLY);
        final Result<HttpReply> mapped = HttpErrorMapper.map(reply, chatConfig, CallPurpose.CHAT, modelId);
        return ProviderCallResult.fromHttpReply(
                mapped, reply, CapabilityRejections.fromMapped(mapped, reply, request, chatConfig.kind()));
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
                    .filter(model -> listed.isChat(Objects.requireNonNull(model.name(), "model name")))
                    .map(model -> new ModelInfo(Objects.requireNonNull(model.name(), "model name"), model.isCloud()))
                    .toList();
            log.debug(
                    "Ollama discovery outcome host={} modelCount={} cloudCount={} code=none",
                    config.baseUrl().getHost(),
                    models.size(),
                    models.stream().filter(ModelInfo::cloud).count());
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
        final HttpReply reply = Objects.requireNonNull(response.data(), REPLY);
        return ProviderCallResult.fromHttpReply(HttpErrorMapper.map(reply, config, purpose, modelId), reply);
    }

    private Result<List<ModelInfo>> discoveryError(AppError error) {
        if (DiscoveryErrors.preserve(error.code())) {
            log.debug(
                    "Ollama discovery outcome host={} code={}", config.baseUrl().getHost(), error.code());
            return Result.err(error);
        }
        return Result.err(DiscoveryErrors.degraded(
                "Ollama", config.baseUrl().getHost(), error, "The Ollama model list could not be read."));
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
