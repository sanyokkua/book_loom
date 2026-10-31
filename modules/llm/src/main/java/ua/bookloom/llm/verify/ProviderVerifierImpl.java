package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
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
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationListener;
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
    private final ProbeShaping shaping;
    private final PrettyJson pretty;

    /**
     * Shares the provider registry, real dialect clients, process-wide gate, retry policy, time source, the engine
     * detection that stores what it finds on the saved provider, the models' settings, where the check verdicts
     * are kept, and the shaping that lays the inference probe out the way the model's template reads it.
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
            ModelSettingsRepository settings,
            ProbeShaping shaping) {
        this.providerConfigs = Objects.requireNonNull(providerConfigs, "providerConfigs");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.calls = new RetriedCalls(gate, Objects.requireNonNull(retryPolicy, "retryPolicy"));
        this.inference = new InferenceStage(calls, Objects.requireNonNull(mapper, "mapper"));
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.engines = Objects.requireNonNull(engines, "engines");
        this.shaping = Objects.requireNonNull(shaping, "shaping");
        this.engineStage = new EngineStage(engines);
        this.models = new ModelsStage(calls);
        this.pretty = new PrettyJson(mapper);
        this.advanced = new AdvancedStage(
                calls, mapper, Objects.requireNonNull(settings, "settings"), retryPolicy.clock(), nanoTime, pretty);
    }

    @Override
    public Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(policy, "policy");
        final Optional<ProviderConfig> configured = providerConfigs.find(selection.providerId());
        VerificationReports.logStart(selection, policy, configured);
        if (configured.isEmpty()) {
            return VerificationReports.unknownProvider(selection, policy);
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
                VerificationReports.stageSummary(connection.outcome()));
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
        return verify(draft, modelId, policy, VerificationListener.NONE);
    }

    @Override
    public Result<VerificationReport> verify(
            ProviderConfig draft, String modelId, VerificationPolicy policy, VerificationListener listener) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(listener, "listener");
        log.info(
                "Draft provider verification started provider={} kind={} model={} policy={} heard={}",
                draft.id(),
                draft.kind(),
                modelId,
                policy,
                !VerificationListener.NONE.equals(listener));
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
                new Run(detectsEngine(draft, policy, true), listener));
    }

    /**
     * How one verification runs beyond its stages.
     *
     * @param detect whether the connection stage asks which engine answers
     * @param listener hears each request before it is sent and each stage as it ends
     */
    private record Run(boolean detect, VerificationListener listener) {}

    private Result<VerificationReport> verifyConfigured(
            ModelSelection selection, VerificationPolicy policy, ProviderConfig config) {
        return runStages(
                selection,
                policy,
                config,
                clients.create(config),
                new Run(detectsEngine(config, policy, false), VerificationListener.NONE));
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
            Run run) {
        final List<StageOutcome> stages = new ArrayList<>(3);
        if (policy == VerificationPolicy.HEALTH_AND_MODELS && VerificationReports.isStillLoading(client, config)) {
            stages.add(StageOutcomes.failed(
                    VerificationStage.CONNECTION, VerificationReports.loading(config, selection.modelId())));
            return VerificationReports.completed(selection, policy, stages, null);
        }
        final EngineStage.Checked connection = connectionStage(client, config, run.detect());
        VerificationReports.finished(stages, connection.outcome(), run.listener());
        if (connection.outcome().status() == StageStatus.FAILED || policy == VerificationPolicy.CONNECTION) {
            return VerificationReports.completed(selection, policy, stages, connection.engine());
        }
        final ProviderConfig detected = detectedConfig(config, connection.engine());
        final ProviderClient asDetected = detected.equals(config) ? client : clients.createUncached(detected);
        final StageOutcome models = modelsStage(asDetected, detected, selection.modelId());
        VerificationReports.finished(stages, models, run.listener());
        if (models.status() == StageStatus.FAILED
                || policy == VerificationPolicy.CONNECTION_AND_MODELS
                || policy == VerificationPolicy.HEALTH_AND_MODELS) {
            return VerificationReports.completed(selection, policy, stages, connection.engine());
        }
        final StageOutcome inference =
                inferenceStage(asDetected.forChecks(), detected, selection.modelId(), run.listener());
        VerificationReports.finished(stages, inference, run.listener());
        if (policy == VerificationPolicy.ADVANCED && inference.status() != StageStatus.FAILED) {
            VerificationReports.finished(
                    stages, advancedStage(detected, selection.modelId(), run.listener()), run.listener());
        }
        return VerificationReports.completed(selection, policy, stages, connection.engine());
    }

    // The models, inference and advanced stages are shaped by the engine this Test just found (an LM Studio id check,
    // its variant removal, its route), which neither the description being tested nor the cached client knows yet.
    private static ProviderConfig detectedConfig(ProviderConfig config, @Nullable EngineDetection found) {
        final ProviderConfig detected = found == null || found.source() != EngineDetection.Source.DETECTED
                ? config
                : config.withEngine(found.engine(), config.engineChoice(), found.version());
        log.debug(
                "Provider verification stages engine provider={} stored={} found={}",
                config.id(),
                config.effectiveEngine(),
                detected.effectiveEngine());
        return detected;
    }

    private StageOutcome advancedStage(ProviderConfig config, String modelId, VerificationListener listener) {
        final ProviderClient client = clients.createUncached(config);
        final long start = nanoTime.getAsLong();
        final AdvancedStage.Ran ran =
                advanced.run(client, config, modelId, new RequestAnnouncer(listener, config, client, modelId, pretty));
        final StageOutcome outcome = ran.outcome().withElapsed(since(start));
        return StageOutcomes.withExchange(
                outcome,
                VerificationExchanges.advanced(
                        config, outcome, ran.calls(), client.chatUrl().orElse(null)));
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

    private StageOutcome inferenceStage(
            ProviderClient client, ProviderConfig config, String modelId, VerificationListener listener) {
        final long start = nanoTime.getAsLong();
        final InferenceProbe probe = inference.run(
                client,
                config.id(),
                modelId,
                shaping.forModel(config, modelId),
                new RequestAnnouncer(listener, config, client, modelId, pretty));
        final StageOutcome outcome = probe.outcome().withElapsed(since(start));
        final ProbeVerdicts.Outcome schema = probe.schema();
        if (schema != null) {
            advanced.keepSchema(config.id(), modelId, schema);
        }
        final String body = client.requestBody(modelId, probe.sent());
        final String reply = probe.response() == null ? "" : probe.response().content();
        return StageOutcomes.withExchange(
                outcome,
                VerificationExchanges.inference(
                        config,
                        probe,
                        new VerificationExchanges.Bodies(body, pretty.of(body), reply, pretty.of(reply)),
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
}
