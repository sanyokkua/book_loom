package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.capability.ContextLengthProbe;
import ua.bookloom.llm.capability.HealthProbe;
import ua.bookloom.llm.client.openai.OpenAiRequestMapper.ParsedFormat;
import ua.bookloom.llm.dto.OpenAiChatRequest;
import ua.bookloom.llm.engine.CheckedModelId;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.engine.EngineTraits.OffSwitch;
import ua.bookloom.llm.engine.ModelIdCheck;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpErrorMapper.CallPurpose;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RequestTimeouts;
import ua.bookloom.llm.provider.CapabilityRejections;
import ua.bookloom.llm.provider.CheckReply;
import ua.bookloom.llm.provider.EndpointPaths;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * Implements the shared OpenAI-shaped models and chat endpoints. The provider's engine chooses the reasoning switch and
 * the cap field ({@link EngineShaping}), and a reply whose cap a thinking model spent on reasoning is asked for once
 * more with a raised cap ({@link ReasoningBudget}). Where the engine answers any id with its loaded model, the id is
 * first checked against the server's own list ({@link ModelIdCheck}); LM Studio's chat goes to its own route
 * ({@link ChatRoute}).
 */
@Slf4j
public final class OpenAiCompatibleClient implements ProviderClient {
    private static final String MODELS_PATH = "/models";
    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final LongSupplier nanoTime;
    private final OpenAiRequestMapper requestMapper;
    private final EngineShaping shaping;
    private final OpenAiModelList models;
    private final OpenAiReplyReader replies;
    private final ChatRoute route;
    private final ModelIdCheck idCheck;

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
        this(
                config,
                exchange,
                mapper,
                nanoTime,
                new EngineShaping(config, facts, verdicts, false),
                new ChatRoute(config, exchange, mapper),
                new ModelIdCheck(config, exchange, mapper, facts));
    }

    // The Test's copy shares the session's route and id checks, so a fallback or a check made by one is the other's.
    private OpenAiCompatibleClient(
            ProviderConfig config,
            HttpExchange exchange,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineShaping shaping,
            ChatRoute route,
            ModelIdCheck idCheck) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.shaping = Objects.requireNonNull(shaping, "shaping");
        this.route = Objects.requireNonNull(route, "route");
        this.idCheck = Objects.requireNonNull(idCheck, "idCheck");
        this.models = new OpenAiModelList(config, mapper, exchange);
        this.replies = new OpenAiReplyReader(config, mapper);
        this.requestMapper = new OpenAiRequestMapper(
                config,
                mapper,
                (modelId, failure) -> unexpectedError("serialize chat request", modelId, failure),
                shaping);
    }

    @Override
    public ProviderClient forChecks() {
        return new OpenAiCompatibleClient(config, exchange, mapper, nanoTime, shaping.forChecks(), route, idCheck);
    }

    // A check is sent once as asked: no raised cap after a reply spent on reasoning, since that reply is the finding.
    @Override
    public ProviderCallResult<CheckReply> checkChat(
            String modelId, ChatRequest request, @Nullable OffSwitch offSwitch) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(request, "request");
        log.debug(
                "OpenAI-compatible check started provider={} model={} responseFormatPresent={} offSwitch={}",
                config.id(),
                modelId,
                request.responseFormat() != null,
                offSwitch == null ? "engine" : offSwitch.wireName());
        try {
            final OpenAiCompatibleClient checker = new OpenAiCompatibleClient(
                    config, exchange, mapper, nanoTime, shaping.forCheck(offSwitch), route, idCheck);
            final Result<CheckedModelId> checked = idCheck.check(modelId);
            if (checked.isErr()) {
                return ProviderCallResult.withoutRetryAfter(
                        Result.err(Objects.requireNonNull(checked.error(), "error")));
            }
            return checker.sendCheck(
                    Objects.requireNonNull(checked.data(), "checked").sentId(), request);
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("check", modelId, failure)));
        }
    }

    private ProviderCallResult<CheckReply> sendCheck(String modelId, ChatRequest request) {
        final Result<ParsedFormat> parsed = requestMapper.parseFormat(request.responseFormat(), modelId);
        final Result<String> body =
                parsed.flatMap(format -> requestMapper.serializeRequest(modelId, request, format.format()));
        if (body.isErr()) {
            return ProviderCallResult.withoutRetryAfter(Result.err(Objects.requireNonNull(body.error(), "error")));
        }
        final long startNanos = nanoTime.getAsLong();
        final ProviderCallResult<HttpReply> call =
                postChat(modelId, Objects.requireNonNull(body.data(), "body"), request);
        final Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - startNanos);
        final HttpReply reply = call.result().data();
        if (reply == null) {
            final AppError error = Objects.requireNonNull(call.result().error(), "error");
            logOutcome("check", modelId, error);
            return new ProviderCallResult<>(Result.err(error), call.retryAfter(), call.rejectedCapability());
        }
        final CheckReply.Reasoning reasoning = ReasoningSign.in(mapper, reply.body());
        return new ProviderCallResult<>(
                replies.read(modelId, reply, elapsed).map(response -> new CheckReply(response, reasoning)),
                call.retryAfter());
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
            final Optional<Result<List<ModelInfo>>> native0 = models.lmStudioList();
            if (native0.isPresent()) {
                return ProviderCallResult.withoutRetryAfter(native0.orElseThrow());
            }
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
            final Result<CheckedModelId> checked = idCheck.check(modelId);
            if (checked.isErr()) {
                return ProviderCallResult.withoutRetryAfter(
                        Result.err(Objects.requireNonNull(checked.error(), "error")));
            }
            final String sentId =
                    Objects.requireNonNull(checked.data(), "checked").sentId();
            return chatWithParsedFormat(sentId, shaping.proved(sentId, request));
        } catch (Throwable failure) {
            return ProviderCallResult.withoutRetryAfter(Result.err(unexpectedError("chat", modelId, failure)));
        }
    }

    @Override
    public String requestBody(String typedId, ChatRequest asked) {
        Objects.requireNonNull(typedId, "typedId");
        Objects.requireNonNull(asked, "asked");
        final String modelId = idCheck.kept(typedId).map(CheckedModelId::sentId).orElse(typedId);
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
    public Result<CheckedModelId> recheckModelId(String modelId) {
        return idCheck.recheck(Objects.requireNonNull(modelId, "modelId"));
    }

    @Override
    public boolean checksModelIds() {
        return idCheck.applies();
    }

    @Override
    public Optional<URI> chatUrl() {
        return Optional.of(route.url());
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
        return new ProviderCallResult<>(replies.read(modelId, reply, elapsed), call.retryAfter());
    }

    @Override
    public Duration chatTimeout(ChatRequest request) {
        return RequestTimeouts.forChat(config, Objects.requireNonNull(request, "request"))
                .requestTimeout();
    }

    private ProviderCallResult<HttpReply> postChat(String modelId, String body, ChatRequest request) {
        final ProviderConfig chatConfig = RequestTimeouts.forChat(config, request);
        final Result<HttpReply> response = route.post(chatConfig, body);
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        final Result<HttpReply> mapped = HttpErrorMapper.map(reply, chatConfig, CallPurpose.CHAT, modelId);
        return ProviderCallResult.fromHttpReply(
                mapped, reply, CapabilityRejections.fromMapped(mapped, reply, request, chatConfig.kind()));
    }

    private ProviderCallResult<HttpReply> get(CallPurpose purpose, @Nullable String modelId) {
        final Result<HttpReply> response = exchange.get(config, EndpointPaths.modelsPath(config, MODELS_PATH));
        if (response.isErr()) {
            return ProviderCallResult.withoutRetryAfter(response);
        }
        final HttpReply reply = Objects.requireNonNull(response.data(), "reply");
        return ProviderCallResult.fromHttpReply(HttpErrorMapper.map(reply, config, purpose, modelId), reply);
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
}
