package ua.bookloom.llm.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RejectedCapability;
import ua.bookloom.llm.retry.RetryPolicy;

/** Runs ordered provider verification stages with the shared inference gate and typed retry policy. */
@Slf4j
public final class ProviderVerifierImpl implements ProviderVerifier {

    private static final String STRUCTURED_OUTPUT_SUPPORTED = "structured output: supported";
    private static final String STRUCTURED_OUTPUT_NOT_CONFIRMED = "structured output: not confirmed";
    private final ProviderConfigs providerConfigs;
    private final ProviderClientFactory clients;
    private final InferenceGate gate;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;
    private final LongSupplier nanoTime;

    /** Shares the provider registry, real dialect clients, process-wide gate, retry policy, and time source. */
    @Inject
    public ProviderVerifierImpl(
            ProviderConfigs providerConfigs,
            ProviderClientFactory clients,
            InferenceGate gate,
            RetryPolicy retryPolicy,
            ObjectMapper mapper,
            LongSupplier nanoTime) {
        this.providerConfigs = Objects.requireNonNull(providerConfigs, "providerConfigs");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    @Override
    public Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(policy, "policy");
        final Optional<ProviderConfig> configured = providerConfigs.find(selection.providerId());
        logStart(selection, policy, configured);
        if (configured.isEmpty()) {
            return unknownProvider(selection, policy);
        }
        return verifyConfigured(selection, policy, configured.orElseThrow());
    }

    @Override
    public Result<VerificationReport> verifyConnection(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        final Optional<ProviderConfig> configured = providerConfigs.find(providerId);
        log.info(
                "Provider connection check started provider={} kind={} host={}",
                providerId,
                configured.map(value -> value.kind().name()).orElse("unknown"),
                configured.map(value -> value.baseUrl().getHost()).orElse("unknown"));
        if (configured.isEmpty()) {
            final AppError error =
                    AppError.of(ErrorCode.validation, "Unknown provider", "The selected provider is not registered.");
            log.warn("Provider connection check refused unknown provider={} code={}", providerId, error.code());
            return Result.err(error);
        }
        final ProviderClient client = clients.create(configured.orElseThrow());
        final StageOutcome connection = timed(() -> verifyConnection(client, providerId));
        log.info("Provider connection check finished provider={} stage={}", providerId, stageSummary(connection));
        return Result.ok(new VerificationReport(List.of(connection)));
    }

    @Override
    public Result<VerificationReport> verify(ProviderConfig draft, String modelId, VerificationPolicy policy) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(policy, "policy");
        log.info(
                "Draft provider verification started provider={} kind={} model={} policy={}",
                draft.id(),
                draft.kind(),
                modelId,
                policy);
        final ProviderConfigRules.Check check = ProviderConfigRules.check(draft);
        if (!check.isValid()) {
            final AppError refusal = check.errors().get(0);
            log.warn("Draft provider verification refused provider={} code={}", draft.id(), refusal.code());
            return Result.err(refusal);
        }
        return runStages(new ModelSelection(draft.id(), modelId), policy, draft, clients.createUncached(draft));
    }

    private Result<VerificationReport> verifyConfigured(
            ModelSelection selection, VerificationPolicy policy, ProviderConfig config) {
        return runStages(selection, policy, config, clients.create(config));
    }

    private Result<VerificationReport> runStages(
            ModelSelection selection, VerificationPolicy policy, ProviderConfig config, ProviderClient client) {
        final List<StageOutcome> stages = new ArrayList<>(3);
        final StageOutcome connection =
                withExchange(timed(() -> verifyConnection(client, config.id())), VerificationStage.CONNECTION, config);
        stages.add(connection);
        if (connection.status() == StageStatus.FAILED || policy == VerificationPolicy.CONNECTION) {
            return completed(selection, policy, stages);
        }
        final StageOutcome models = withExchange(
                timed(() -> verifyModels(client, config.id(), selection.modelId())), VerificationStage.MODELS, config);
        stages.add(models);
        if (models.status() == StageStatus.FAILED || policy == VerificationPolicy.CONNECTION_AND_MODELS) {
            return completed(selection, policy, stages);
        }
        return verifyInferencePolicy(selection, policy, client, config, stages, models);
    }

    private static StageOutcome withExchange(StageOutcome outcome, VerificationStage stage, ProviderConfig config) {
        return StageOutcomes.withExchange(
                outcome, VerificationExchanges.forStage(stage, config, outcome.status(), ProbeRequests.MESSAGE));
    }

    private StageOutcome timed(Supplier<StageOutcome> stage) {
        final long start = nanoTime.getAsLong();
        final StageOutcome outcome = stage.get();
        final Duration elapsed = Duration.ofNanos(nanoTime.getAsLong() - start);
        return outcome.withElapsed(elapsed);
    }

    private StageOutcome verifyConnection(ProviderClient client, String providerId) {
        log.debug("Provider verification connection stage started provider={} kind={}", providerId, client.kind());
        final ProviderCallResult<Boolean> call = callWithRetry(client::probe, VerificationStage.CONNECTION, providerId);
        return call.result().isErr()
                ? StageOutcomes.failed(VerificationStage.CONNECTION, error(call.result()))
                : StageOutcomes.passed(VerificationStage.CONNECTION);
    }

    private StageOutcome verifyModels(ProviderClient client, String providerId, String modelId) {
        log.debug("Provider verification models stage started provider={} model={}", providerId, modelId);
        final ProviderCallResult<List<ModelInfo>> call =
                callWithRetry(client::listModels, VerificationStage.MODELS, providerId);
        if (call.result().isErr()) {
            final AppError failure = error(call.result());
            return failure.code() == ErrorCode.discoveryFailed
                    ? StageOutcomes.softPass(failure, 0)
                    : StageOutcomes.failed(VerificationStage.MODELS, failure);
        }
        return listedModels(Objects.requireNonNull(call.result().data(), "model list"), providerId, modelId);
    }

    private StageOutcome listedModels(List<ModelInfo> models, String providerId, String modelId) {
        if (models.isEmpty()) {
            log.debug("Provider verification models stage soft pass provider={} reason=empty-list", providerId);
            return StageOutcomes.softPass(null, 0);
        }
        final boolean selectedModelListed = models.stream().map(ModelInfo::id).anyMatch(modelId::equals);
        log.debug(
                "Provider verification models stage decision provider={} selectedModel={} listedCount={} found={}",
                providerId,
                modelId,
                models.size(),
                selectedModelListed);
        return StageOutcomes.withCount(
                selectedModelListed
                        ? StageOutcomes.passed(VerificationStage.MODELS)
                        : StageOutcomes.failed(
                                VerificationStage.MODELS,
                                AppError.of(
                                        ErrorCode.modelUnavailable,
                                        "Model unavailable",
                                        "The selected model is not available from this provider.")),
                models.size());
    }

    private Result<VerificationReport> verifyInferencePolicy(
            ModelSelection selection,
            VerificationPolicy policy,
            ProviderClient client,
            ProviderConfig config,
            List<StageOutcome> stages,
            StageOutcome models) {
        final String providerId = config.id();
        log.debug(
                "Provider verification inference decision provider={} policy={} modelsStatus={} run=true",
                providerId,
                policy,
                models.status());
        stages.add(withExchange(
                timed(() -> verifyInference(client, providerId, selection.modelId())),
                VerificationStage.INFERENCE,
                config));
        return completed(selection, policy, stages);
    }

    private StageOutcome verifyInference(ProviderClient client, String providerId, String modelId) {
        final ChatRequest request = ProbeRequests.structured(false);
        log.debug(
                "Provider verification inference stage started provider={} model={} messageCount=1 responseFormatPresent=true reasoningDisabled=true",
                providerId,
                modelId);
        final ProviderCallResult<ChatResponse> call = chatProbe(client, providerId, modelId, request);
        if (call.result().isErr()) {
            final AppError failure = error(call.result());
            return failure.code() == ErrorCode.modelNotFound
                    ? StageOutcomes.modelUnavailable(failure)
                    : StageOutcomes.failed(VerificationStage.INFERENCE, failure);
        }
        final ChatResponse response = Objects.requireNonNull(call.result().data(), "chat response");
        if (call.rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            return inferenceContent(response, STRUCTURED_OUTPUT_NOT_CONFIRMED);
        }
        if (validStructuredProbe(response)) {
            return StageOutcomes.passed(VerificationStage.INFERENCE, STRUCTURED_OUTPUT_SUPPORTED);
        }
        return plainProbeOutcome(client, providerId, modelId);
    }

    private ProviderCallResult<ChatResponse> chatProbe(
            ProviderClient client, String providerId, String modelId, ChatRequest request) {
        final ProviderCallResult<ChatResponse> call =
                callWithRetry(() -> client.chat(modelId, request), VerificationStage.INFERENCE, providerId);
        final ProviderCallResult<ChatResponse> withoutReasoning =
                call.rejectedCapability() == RejectedCapability.REASONING_CONTROL
                        ? callWithRetry(
                                () -> client.chat(modelId, ProbeRequests.structured(null)),
                                VerificationStage.INFERENCE,
                                providerId)
                        : call;
        if (withoutReasoning.rejectedCapability() == RejectedCapability.STRUCTURED_OUTPUT) {
            final ProviderCallResult<ChatResponse> plain = plainProbeCall(client, providerId, modelId, null);
            return new ProviderCallResult<>(plain.result(), plain.retryAfter(), RejectedCapability.STRUCTURED_OUTPUT);
        }
        return withoutReasoning;
    }

    private StageOutcome plainProbeOutcome(ProviderClient client, String providerId, String modelId) {
        final ProviderCallResult<ChatResponse> plain = plainProbeCall(client, providerId, modelId, false);
        if (plain.result().isErr()) {
            final AppError failure = error(plain.result());
            return failure.code() == ErrorCode.modelNotFound
                    ? StageOutcomes.modelUnavailable(failure)
                    : StageOutcomes.failed(VerificationStage.INFERENCE, failure);
        }
        return inferenceContent(
                Objects.requireNonNull(plain.result().data(), "chat response"), STRUCTURED_OUTPUT_NOT_CONFIRMED);
    }

    private ProviderCallResult<ChatResponse> plainProbeCall(
            ProviderClient client, String providerId, String modelId, @Nullable Boolean reasoningEnabled) {
        final ProviderCallResult<ChatResponse> call = callWithRetry(
                () -> client.chat(modelId, ProbeRequests.plain(reasoningEnabled)),
                VerificationStage.INFERENCE,
                providerId);
        return call.rejectedCapability() == RejectedCapability.REASONING_CONTROL
                ? callWithRetry(
                        () -> client.chat(modelId, ProbeRequests.plain(null)), VerificationStage.INFERENCE, providerId)
                : call;
    }

    private StageOutcome inferenceContent(ChatResponse response, String note) {
        if (response.content().isBlank()) {
            return StageOutcomes.failed(
                    VerificationStage.INFERENCE,
                    AppError.of(
                            ErrorCode.emptyCompletion, "Empty completion", "The provider returned an empty response."));
        }
        return StageOutcomes.passed(VerificationStage.INFERENCE, note);
    }

    private boolean validStructuredProbe(ChatResponse response) {
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

    private <T> ProviderCallResult<T> callWithRetry(
            Supplier<ProviderCallResult<T>> operation, VerificationStage stage, String providerId) {
        int attempt = 1;
        while (true) {
            final Result<ProviderCallResult<T>> gated = gate.run(() -> Result.ok(operation.get()));
            if (gated.isErr()) {
                return ProviderCallResult.withoutRetryAfter(Result.err(error(gated)));
            }
            final ProviderCallResult<T> call = Objects.requireNonNull(gated.data(), "provider call");
            if (call.result().isOk()) {
                return call;
            }
            final AppError failure = error(call.result());
            if (!retryPolicy.shouldRetry(failure.code(), attempt)) {
                return call;
            }
            final Duration delay = retryPolicy.delayBeforeRetry(attempt, call.retryAfter());
            log.warn(
                    "Retrying provider verification provider={} stage={} failedAttempt={} nextAttempt={} code={} delay={}",
                    providerId,
                    stage,
                    attempt,
                    attempt + 1,
                    failure.code(),
                    delay);
            if (!retryPolicy.sleep(delay)) {
                return cancelled(stage);
            }
            attempt++;
        }
    }

    private static <T> AppError error(Result<T> result) {
        return Objects.requireNonNull(result.error(), "provider error");
    }

    private static <T> ProviderCallResult<T> cancelled(VerificationStage stage) {
        return ProviderCallResult.withoutRetryAfter(Result.err(AppError.of(
                ErrorCode.cancelled,
                "Verification cancelled",
                "The provider retry wait was interrupted during " + stage.name().toLowerCase(Locale.ROOT)
                        + " verification.")));
    }

    private Result<VerificationReport> unknownProvider(ModelSelection selection, VerificationPolicy policy) {
        final AppError error =
                AppError.of(ErrorCode.validation, "Unknown provider", "The selected provider is not registered.");
        log.warn("Provider verification refused unknown provider={} code={}", selection.providerId(), error.code());
        log.info(
                "Provider verification finished provider={} model={} policy={} error={} stages=[]",
                selection.providerId(),
                selection.modelId(),
                policy,
                error.code());
        return Result.err(error);
    }

    private void logStart(ModelSelection selection, VerificationPolicy policy, Optional<ProviderConfig> config) {
        final String kind = config.map(value -> value.kind().name()).orElse("unknown");
        final String host = config.map(value -> value.baseUrl().getHost()).orElse("unknown");
        log.info(
                "Provider verification started provider={} kind={} host={} model={} policy={}",
                selection.providerId(),
                kind,
                host,
                selection.modelId(),
                policy);
    }

    private Result<VerificationReport> completed(
            ModelSelection selection, VerificationPolicy policy, List<StageOutcome> outcomes) {
        final VerificationReport report = new VerificationReport(outcomes);
        log.info(
                "Provider verification finished provider={} model={} policy={} stages={}",
                selection.providerId(),
                selection.modelId(),
                policy,
                outcomes.stream().map(ProviderVerifierImpl::stageSummary).toList());
        return Result.ok(report);
    }

    private static String stageSummary(StageOutcome outcome) {
        final String error =
                outcome.error() == null ? "" : " code=" + outcome.error().code();
        final String note = outcome.note() == null ? "" : " note=" + outcome.note();
        return outcome.stage() + ":" + outcome.status() + error + note;
    }
}
