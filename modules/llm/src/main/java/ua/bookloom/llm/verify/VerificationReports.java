package ua.bookloom.llm.verify;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationListener;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * How {@link ProviderVerifierImpl} starts, hears, refuses and ends a verification: the lifecycle lines of the log, the
 * finished report, and each stage handed to the listener as it ends.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class VerificationReports {

    // A listener that fails must not fail the verification: the report still arrives whole.
    static void finished(List<StageOutcome> stages, StageOutcome outcome, VerificationListener listener) {
        stages.add(outcome);
        if (VerificationListener.NONE.equals(listener)) {
            return;
        }
        log.debug("Provider verification stage heard stage={} status={}", outcome.stage(), outcome.status());
        try {
            listener.stageFinished(outcome);
        } catch (RuntimeException failure) {
            log.warn("Provider verification listener failed on a stage end stage={}", outcome.stage(), failure);
        }
    }

    // A llama-server answers 503 on every route while it loads; its health route is the one that says when it is done.
    static boolean isStillLoading(ProviderClient client, ProviderConfig config) {
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

    static AppError loading(ProviderConfig config, String modelId) {
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

    static Result<VerificationReport> unknownProvider(ModelSelection selection, VerificationPolicy policy) {
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

    static void logStart(ModelSelection selection, VerificationPolicy policy, Optional<ProviderConfig> config) {
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

    static Result<VerificationReport> completed(
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
                outcomes.stream().map(VerificationReports::stageSummary).toList());
        return Result.ok(report);
    }

    static String stageSummary(StageOutcome outcome) {
        final String error =
                outcome.error() == null ? "" : " code=" + outcome.error().code();
        final String note = outcome.note() == null ? "" : " note=" + outcome.note();
        return outcome.stage() + ":" + outcome.status() + error + note;
    }
}
