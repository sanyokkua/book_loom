package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.engine.EngineRegistry;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** Runs ordered provider verification stages with the shared inference gate and typed retry policy. */
@Slf4j
public final class ProviderVerifierImpl implements ProviderVerifier {

    private final ProviderConfigs providerConfigs;
    private final ProviderClientFactory clients;
    private final RetriedCalls calls;
    private final InferenceStage inference;
    private final LongSupplier nanoTime;
    private final EngineRegistry engines;
    private final EngineStage engineStage;
    private final AdvancedStage advanced;
    private final ModelsStage models;

    /**
     * Shares the provider registry, real dialect clients, process-wide gate, retry policy, time source, the engine
     * detection that stores what it finds on the saved provider, and the models' settings, where the check verdicts
     * are kept.
     */
    @Inject
    public ProviderVerifierImpl(
            ProviderConfigs providerConfigs,
            ProviderClientFactory clients,
            InferenceGate gate,
            RetryPolicy retryPolicy,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineRegistry engines,
            ModelSettingsRepository settings) {
        this.providerConfigs = Objects.requireNonNull(providerConfigs, "providerConfigs");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.calls = new RetriedCalls(gate, retryPolicy);
        this.inference = new InferenceStage(calls, Objects.requireNonNull(mapper, "mapper"));
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.engineStage = new EngineStage(engines);
        this.models = new ModelsStage(calls);
        this.advanced =
                new AdvancedStage(calls, mapper, Objects.requireNonNull(settings, "settings"), Clock.systemUTC());
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
        final ProviderConfig config = configured.orElseThrow();
        final ProviderClient client = clients.create(config);
        final EngineStage.Checked connection =
                engineStage.check(config, timed(() -> verifyConnection(client, providerId)));
        log.info(
                "Provider connection check finished provider={} stage={}",
                providerId,
                stageSummary(connection.outcome()));
        return Result.ok(new VerificationReport(List.of(connection.outcome()), connection.engine()));
    }

    @Override
    public Result<EngineDetection> detectEngine(ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.info(
                "Provider engine detection asked again provider={} host={}",
                config.id(),
                config.baseUrl().getHost());
        try {
            return engines.detectAgain(config);
        } catch (RuntimeException failure) {
            log.error("Provider engine detection failed unexpectedly provider={}", config.id(), failure);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Engine detection failed",
                    "The server's engine could not be detected.",
                    null,
                    failure));
        }
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
        return runStages(
                new ModelSelection(draft.id(), modelId),
                policy,
                draft,
                clients.createUncached(draft),
                detectsEngine(draft, policy, true));
    }

    private Result<VerificationReport> verifyConfigured(
            ModelSelection selection, VerificationPolicy policy, ProviderConfig config) {
        return runStages(selection, policy, config, clients.create(config), detectsEngine(config, policy, false));
    }

    // Only the person's Test asks which engine answers: the window's three Test buttons check a description
    // (connection, connection and models, full), and a saved provider is tested whole or for its connection. A run's
    // recovery probe (connection and models, health and models) and the command line's preflight never detect, so
    // nothing is sent at a start or in the background.
    private static boolean detectsEngine(ProviderConfig config, VerificationPolicy policy, boolean draft) {
        final boolean detect =
                switch (policy) {
                    case FULL, ADVANCED, CONNECTION -> true;
                    case CONNECTION_AND_MODELS -> draft;
                    case PREFLIGHT, HEALTH_AND_MODELS -> false;
                };
        log.debug(
                "Provider verification engine detection provider={} policy={} draft={} detect={}",
                config.id(),
                policy,
                draft,
                detect);
        return detect;
    }

    private Result<VerificationReport> runStages(
            ModelSelection selection,
            VerificationPolicy policy,
            ProviderConfig config,
            ProviderClient client,
            boolean detect) {
        final List<StageOutcome> stages = new ArrayList<>(3);
        if (policy == VerificationPolicy.HEALTH_AND_MODELS && isStillLoading(client, config)) {
            stages.add(StageOutcomes.failed(VerificationStage.CONNECTION, loading(config, selection.modelId())));
            return completed(selection, policy, stages, null);
        }
        final EngineStage.Checked connection = connectionStage(client, config, detect);
        stages.add(connection.outcome());
        if (connection.outcome().status() == StageStatus.FAILED || policy == VerificationPolicy.CONNECTION) {
            return completed(selection, policy, stages, connection.engine());
        }
        final StageOutcome models = modelsStage(client, config, selection.modelId());
        stages.add(models);
        if (models.status() == StageStatus.FAILED
                || policy == VerificationPolicy.CONNECTION_AND_MODELS
                || policy == VerificationPolicy.HEALTH_AND_MODELS) {
            return completed(selection, policy, stages, connection.engine());
        }
        final StageOutcome inference = inferenceStage(client.forChecks(), config, selection.modelId());
        stages.add(inference);
        if (policy == VerificationPolicy.ADVANCED && inference.status() != StageStatus.FAILED) {
            stages.add(advancedStage(config, connection.engine(), selection.modelId()));
        }
        return completed(selection, policy, stages, connection.engine());
    }

    // The checks are chosen by the engine this Test just found, which the cached client does not know yet.
    private StageOutcome advancedStage(ProviderConfig config, @Nullable EngineDetection found, String modelId) {
        final ProviderConfig checked = found == null || found.source() != EngineDetection.Source.DETECTED
                ? config
                : config.withEngine(found.engine(), config.engineChoice(), found.version());
        log.debug(
                "Provider verification advanced stage engine provider={} stored={} found={}",
                config.id(),
                config.effectiveEngine(),
                checked.effectiveEngine());
        final ProviderClient client = clients.createUncached(checked);
        final long start = nanoTime.getAsLong();
        final StageOutcome outcome = advanced.run(client, checked, modelId).withElapsed(since(start));
        return StageOutcomes.withExchange(
                outcome,
                VerificationExchanges.advanced(config, outcome, client.chatUrl().orElse(null)));
    }

    // A llama-server answers 503 on every route while it loads; its health route is the one that says when it is done.
    private static boolean isStillLoading(ProviderClient client, ProviderConfig config) {
        final boolean loading = client.isLoadingModel();
        if (loading) {
            log.debug("Provider health says the model is still loading provider={}", config.id());
        } else {
            log.info(
                    "Provider health no longer says loading provider={} host={}",
                    config.id(),
                    config.baseUrl().getHost());
        }
        return loading;
    }

    private static AppError loading(ProviderConfig config, String modelId) {
        return AppError.of(
                ErrorCode.modelUnavailable,
                "Provider is still loading the model",
                "The server is loading the model and will answer when it is ready.",
                SafeDetails.empty()
                        .withReason(FailureReason.MODEL_LOADING)
                        .withEndpoint(config.baseUrl())
                        .withModelName(modelId)
                        .render(),
                null);
    }

    private EngineStage.Checked connectionStage(ProviderClient client, ProviderConfig config, boolean detect) {
        final StageOutcome timedOutcome = timed(() -> verifyConnection(client, config.id()));
        final EngineStage.Checked checked =
                detect ? engineStage.check(config, timedOutcome) : new EngineStage.Checked(timedOutcome, null);
        final StageOutcome outcome = checked.outcome();
        return new EngineStage.Checked(
                StageOutcomes.withExchange(outcome, VerificationExchanges.connection(config, outcome)),
                checked.engine());
    }

    private StageOutcome modelsStage(ProviderClient client, ProviderConfig config, String modelId) {
        final long start = nanoTime.getAsLong();
        final ModelsProbe probe = models.run(client, config.id(), modelId);
        final StageOutcome outcome = probe.outcome().withElapsed(since(start));
        return StageOutcomes.withExchange(outcome, VerificationExchanges.models(config, outcome, probe.ids()));
    }

    private StageOutcome inferenceStage(ProviderClient client, ProviderConfig config, String modelId) {
        final long start = nanoTime.getAsLong();
        final InferenceProbe probe = inference.run(client, config.id(), modelId);
        final StageOutcome outcome = probe.outcome().withElapsed(since(start));
        final ProbeVerdicts.Outcome schema = probe.schema();
        if (schema != null) {
            advanced.keepSchema(config.id(), modelId, schema);
        }
        return StageOutcomes.withExchange(
                outcome,
                VerificationExchanges.inference(
                        config,
                        probe,
                        client.requestBody(modelId, probe.sent()),
                        client.chatUrl().orElse(null)));
    }

    private Duration since(long startNanos) {
        return Duration.ofNanos(nanoTime.getAsLong() - startNanos);
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

    private <T> ProviderCallResult<T> callWithRetry(
            Supplier<ProviderCallResult<T>> operation, VerificationStage stage, String providerId) {
        return calls.run(operation, stage, providerId);
    }

    private static <T> AppError error(Result<T> result) {
        return RetriedCalls.error(result);
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
            ModelSelection selection,
            VerificationPolicy policy,
            List<StageOutcome> outcomes,
            @Nullable EngineDetection engine) {
        final VerificationReport report = new VerificationReport(outcomes, engine);
        log.info(
                "Provider verification finished provider={} model={} policy={} engine={} stages={}",
                selection.providerId(),
                selection.modelId(),
                policy,
                engine == null ? "none" : engine.engine(),
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
