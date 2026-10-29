package ua.bookloom.llm.verify;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;

/**
 * The models stage: the server's own id check where any id is served, else the listing, where an unreadable or empty
 * list passes softly and an unlisted model fails.
 */
@Slf4j
final class ModelsStage {

    private final RetriedCalls calls;

    ModelsStage(final RetriedCalls calls) {
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    ModelsProbe run(ProviderClient client, String providerId, String modelId) {
        log.debug("Provider verification models stage started provider={} model={}", providerId, modelId);
        if (!modelId.isBlank() && client.checksModelIds()) {
            final Optional<ModelsProbe> checked = IdCheckStage.probe(client, providerId, modelId);
            if (checked.isPresent()) {
                return checked.orElseThrow();
            }
        }
        final ProviderCallResult<List<ModelInfo>> call =
                calls.run(client::listModels, VerificationStage.MODELS, providerId);
        if (call.result().isErr()) {
            final AppError failure = RetriedCalls.error(call.result());
            return new ModelsProbe(
                    failure.code() == ErrorCode.discoveryFailed
                            ? StageOutcomes.softPass(failure, 0)
                            : StageOutcomes.failed(VerificationStage.MODELS, failure),
                    List.of());
        }
        return listedModels(Objects.requireNonNull(call.result().data(), "model list"), providerId, modelId);
    }

    private static ModelsProbe listedModels(List<ModelInfo> models, String providerId, String modelId) {
        if (models.isEmpty()) {
            log.debug("Provider verification models stage soft pass provider={} reason=empty-list", providerId);
            return new ModelsProbe(StageOutcomes.softPass(null, 0), List.of());
        }
        // With no model chosen yet the question is only whether the list can be read; it is answered by the count.
        final boolean selectedModelListed =
                modelId.isBlank() || models.stream().map(ModelInfo::id).anyMatch(modelId::equals);
        log.debug(
                "Provider verification models stage decision provider={} selectedModel={} listedCount={} found={}",
                providerId,
                modelId,
                models.size(),
                selectedModelListed);
        final StageOutcome outcome = StageOutcomes.withCount(
                selectedModelListed
                        ? StageOutcomes.passed(VerificationStage.MODELS)
                        : StageOutcomes.failed(
                                VerificationStage.MODELS,
                                AppError.of(
                                        ErrorCode.modelUnavailable,
                                        "Model unavailable",
                                        "The selected model is not available from this provider.")),
                models.size());
        return new ModelsProbe(outcome, models.stream().map(ModelInfo::id).toList());
    }
}
