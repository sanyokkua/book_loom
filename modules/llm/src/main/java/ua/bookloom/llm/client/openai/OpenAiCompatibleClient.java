package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.capability.ContextLengthProbe;
import ua.bookloom.llm.capability.HealthProbe;
import ua.bookloom.llm.client.openai.OpenAiRequestMapper.ParsedFormat;
import ua.bookloom.llm.dto.OpenAiChatRequest;
import ua.bookloom.llm.dto.OpenAiChatResponse;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpErrorMapper.CallPurpose;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RequestTimeouts;
import ua.bookloom.llm.http.UnreadableReply;
import ua.bookloom.llm.provider.CapabilityRejections;
import ua.bookloom.llm.provider.EndpointPaths;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.response.ReplySanitizer;

/**
 * Implements the shared OpenAI-shaped models and chat endpoints. The provider's engine chooses the reasoning switch and
 * the cap field ({@link EngineShaping}), and a reply whose cap a thinking model spent on reasoning is asked for once
 * more with a raised cap ({@link ReasoningBudget}).
 */
@Slf4j
public final class OpenAiCompatibleClient implements ProviderClient {
    private static final String MODELS_PATH = "/models";
    private static final String CHAT_PATH = "/chat/completions";
    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final LongSupplier nanoTime;
    private final OpenAiRequestMapper requestMapper;
    private final EngineShaping shaping;
    private final OpenAiModelList models;

    /**
     * Binds one client to an endpoint with no stored check verdicts and its own session facts, so a server no engine
     * row describes gets only the plain request.
     */
    public OpenAiCompatibleClient(
            ProviderConfig config, HttpExchange exchange, ObjectMapper mapper, LongSupplier nanoTime) {
        this(config, exchange, mapper, nanoTime, new EngineFacts(exchange, mapper), modelId -> ProbeVerdicts.none());
    }

    /**
     * Binds one client to an endpoint while sharing the application's HTTP, JSON, time and engine-fact
     * infrastructure.
     *
     * @param verdicts the stored check verdicts of a model id, read on each call that needs them
     */
    public OpenAiCompatibleClient(
            ProviderConfig config,
            HttpExchange exchange,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineFacts facts,
            Function<String, ProbeVerdicts> verdicts) {
        this(config, exchange, mapper, nanoTime, new EngineShaping(config, facts, verdicts, false));
    }

    private OpenAiCompatibleClient(
            ProviderConfig config,
            HttpExchange exchange,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineShaping shaping) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.shaping = Objects.requireNonNull(shaping, "shaping");
        this.models = new OpenAiModelList(config, mapper);
        this.requestMapper = new OpenAiRequestMapper(
                config,
                mapper,
                (modelId, failure) -> unexpectedError("serialize chat request", modelId, failure),
                shaping);
    }

    @Override
    public ProviderClient forChecks() {
        return new OpenAiCompatibleClient(config, exchange, mapper, nanoTime, shaping.forChecks());
    }

    @Override
    public ProviderCallResult<Boolean> probe() {
        log.debug(
                "OpenAI-compatible probe started provider={} host={}",
                config.id(),
                config.baseUrl().getHost());
        try {
            final ProviderCallResult<HttpReply> call = get(CallPurpose.PROBE, null);
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
                "OpenAI-compatible discovery started provider={} host={}",
                config.id(),
                config.baseUrl().getHost());
        try {
            final ProviderCallResult<HttpReply> call = get(CallPurpose.DISCOVERY, null);
            final Result<HttpReply> result = call.result();
            if (result.isErr()) {
                return new ProviderCallResult<>(
                        models.failed(Objects.requireNonNull(result.error(), "error")), call.retryAfter());
            }
            return new ProviderCallResult<>(
                    models.read(Objects.requireNonNull(result.data(), "reply").body()), call.retryAfter());
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(models.failure(failure)));
        }
    }

    @Override
    public ProviderCallResult<ChatResponse> chat(String modelId, ChatRequest request) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(request, "request");
        log.debug(
                "OpenAI-compatible chat started provider={} host={} model={} messageCount={} temperature={} maxOutputTokens={} responseFormatPresent={}",
                config.id(),
                config.baseUrl().getHost(),
                modelId,
                request.messages().size(),
                request.temperature(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens(),
                request.responseFormat() != null);
        try {
            return chatWithParsedFormat(modelId, shaping.proved(modelId, request));
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("chat", modelId, failure)));
        }
    }

    @Override
    public String requestBody(String modelId, ChatRequest asked) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(asked, "asked");
        try {
            final ChatRequest request = shaping.proved(modelId, asked);
            final Result<ParsedFormat> parsed = requestMapper.parseFormat(request.responseFormat(), modelId);
            if (parsed.isErr()) {
                return "";
            }
            final Result<String> body = requestMapper.serializeRequest(
                    modelId,
                    request,
                    Objects.requireNonNull(parsed.data(), "parsed format").format());
            return body.isErr() ? "" : Objects.requireNonNull(body.data(), "body");
        } catch (RuntimeException failure) {
            log.warn(
                    "the request body of model {} could not be written: {}",
                    modelId,
                    failure.getClass().getSimpleName());
            return "";
        }
    }

    @Override
    public Optional<Integer> contextLength(String modelId) {
        return new ContextLengthProbe(exchange, mapper).lmStudio(config, modelId);
    }

    @Override
    public boolean isLoadingModel() {
        return new HealthProbe(exchange).isLoading(config);
    }

    @Override
    public ProviderKind kind() {
        return ProviderKind.OPENAI_COMPATIBLE;
    }

    private ProviderCallResult<ChatResponse> chatWithParsedFormat(String modelId, ChatRequest request) {
        final Result<ParsedFormat> parsedFormat = requestMapper.parseFormat(request.responseFormat(), modelId);
        if (parsedFormat.isErr()) {
            return ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(parsedFormat.error(), "error")));
        }
        return exchangeChat(
                modelId,
                request,
                Objects.requireNonNull(parsedFormat.data(), "parsed format").format(),
                true);
    }

    // A reply whose cap a thinking model spent on reasoning is asked for once more with a raised cap, never twice.
    private ProviderCallResult<ChatResponse> exchangeChat(
            String modelId,
            ChatRequest request,
            OpenAiChatRequest.@Nullable ResponseFormatDto format,
            boolean mayRaiseCap) {
        final Result<String> requestBody = requestMapper.serializeRequest(modelId, request, format);
        if (requestBody.isErr()) {
            return ProviderCallResult.withoutRetryAfter(
                    Result.err(Objects.requireNonNull(requestBody.error(), "error")));
        }
        final long startNanos = nanoTime.getAsLong();
        final ProviderCallResult<HttpReply> call =
                postChat(modelId, Objects.requireNonNull(requestBody.data(), "body"), request);
        final Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - startNanos);
        final Result<HttpReply> response = call.result();
        if (response.isErr()) {
            final AppError error = Objects.requireNonNull(response.error(), "error");
            logOutcome("chat", modelId, error);
            return new ProviderCallResult<>(Result.err(error), call.retryAfter(), call.rejectedCapability());
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        final Optional<ChatRequest> raised =
                mayRaiseCap ? ReasoningBudget.raisedAfter(mapper, reply.body(), request) : Optional.empty();
        if (raised.isPresent()) {
            return exchangeChat(modelId, raised.get(), format, false);
        }
        return new ProviderCallResult<>(readChatResponse(modelId, reply, elapsed), call.retryAfter());
    }

    @Override
    public Duration chatTimeout(ChatRequest request) {
        return RequestTimeouts.forChat(config, Objects.requireNonNull(request, "request"))
                .requestTimeout();
    }

    private ProviderCallResult<HttpReply> postChat(String modelId, String body, ChatRequest request) {
        final ProviderConfig chatConfig = RequestTimeouts.forChat(config, request);
        final Result<HttpReply> response = exchange.post(chatConfig, EndpointPaths.chatPath(config, CHAT_PATH), body);
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        final Result<HttpReply> mapped = HttpErrorMapper.map(reply, chatConfig, CallPurpose.CHAT, modelId);
        return ProviderCallResult.fromHttpReply(
                mapped, reply, CapabilityRejections.fromMapped(mapped, reply, request, chatConfig.kind()));
    }

    private Result<ChatResponse> readChatResponse(String requestedModel, HttpReply reply, Duration elapsed) {
        try {
            final OpenAiChatResponse decoded = mapper.readValue(reply.body(), OpenAiChatResponse.class);
            if (decoded == null) {
                return unreadableChatResponse(requestedModel, null);
            }
            return chatResponse(requestedModel, reply, decoded, elapsed);
        } catch (JsonProcessingException failure) {
            return unreadableChatResponse(requestedModel, failure);
        }
    }

    private Result<ChatResponse> chatResponse(
            String requestedModel, HttpReply reply, OpenAiChatResponse decoded, Duration elapsed) {
        if (decoded.choices() == null
                || decoded.choices().isEmpty()
                || decoded.choices().getFirst() == null) {
            return unreadableChatResponse(requestedModel, null);
        }
        final OpenAiChatResponse.Message message = decoded.choices().getFirst().message();
        if (message == null || message.content() == null) {
            return unreadableChatResponse(requestedModel, null);
        }
        if (message.content().isBlank()) {
            return emptyCompletion(requestedModel, reply);
        }
        logModelMismatch(requestedModel, decoded.model());
        final FinishReason finishReason =
                finishReason(decoded.choices().getFirst().finishReason());
        final TokenUsage usage = OpenAiUsage.of(decoded.usage(), decoded.stats(), elapsed);
        log.debug(
                "OpenAI-compatible chat outcome host={} model={} status={} bodyLength={} finish={} usage={}",
                config.baseUrl().getHost(),
                requestedModel,
                reply.status(),
                reply.body().length(),
                finishReason,
                usage);
        return Result.ok(new ChatResponse(ReplySanitizer.clean(message.content()), finishReason, usage));
    }

    private Result<ChatResponse> emptyCompletion(String modelId, HttpReply reply) {
        log.debug(
                "OpenAI-compatible chat outcome host={} model={} status={} bodyLength={} code={}",
                config.baseUrl().getHost(),
                modelId,
                reply.status(),
                reply.body().length(),
                ErrorCode.emptyCompletion);
        return Result.err(AppError.of(
                ErrorCode.emptyCompletion,
                "Model returned an empty response",
                "The OpenAI-compatible provider completed the request without response text.",
                details(modelId),
                null));
    }

    private void logModelMismatch(String requestedModel, @Nullable String answeredModel) {
        if (answeredModel != null && !requestedModel.equals(answeredModel)) {
            log.warn(
                    "OpenAI-compatible provider answered with a different model requested={} answered={}",
                    SafeDetails.empty().withModelName(requestedModel).render(),
                    SafeDetails.empty().withModelName(answeredModel).render());
        }
    }

    private ProviderCallResult<HttpReply> get(CallPurpose purpose, @Nullable String modelId) {
        final Result<HttpReply> response = exchange.get(config, EndpointPaths.modelsPath(config, MODELS_PATH));
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        return ProviderCallResult.fromHttpReply(HttpErrorMapper.map(reply, config, purpose, modelId), reply);
    }

    private Result<ChatResponse> unreadableChatResponse(String modelId, @Nullable Throwable cause) {
        return Result.err(UnreadableReply.error(config, modelId, cause));
    }

    private AppError unexpectedError(String operation, @Nullable String modelId, @Nullable Throwable failure) {
        log.error(
                "Unexpected OpenAI-compatible client failure operation={} host={} modelPresent={} failureType={}",
                operation,
                config.baseUrl().getHost(),
                modelId != null,
                failure == null ? "none" : failure.getClass().getSimpleName());
        return AppError.of(
                ErrorCode.internal,
                "OpenAI-compatible request failed",
                "The OpenAI-compatible provider could not complete the request.",
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
                "OpenAI-compatible {} outcome host={} modelPresent={} code={}",
                operation,
                config.baseUrl().getHost(),
                modelId != null,
                error == null ? "none" : error.code());
    }

    private static FinishReason finishReason(@Nullable String wireReason) {
        return switch (wireReason == null ? "" : wireReason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            default -> FinishReason.OTHER;
        };
    }
}
