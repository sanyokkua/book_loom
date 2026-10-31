package ua.bookloom.llm;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.engine.CheckedModelId;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;

/**
 * Detects a model's context length through the provider's own client. Like listing, it is a catalogue read and skips
 * the inference gate; a provider that cannot answer yields an unknown length, never an error.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ModelCapabilityService implements ModelCapabilities {

    private final ProviderConfigs configs;
    private final ProviderClientFactory clients;
    private final ModelProfiles profiles;
    private final ModelSettingsCache settings;

    // Served by provider and model id, so the Models tab reads it before a chat model exists. A read that throws leaves
    // the server's facts unknown; the name alone still gives the identity and the default profile.
    @Override
    public Result<ModelReport> report(ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        final String providerId = selection.providerId();
        final String modelId = selection.modelId();
        log.debug("Reporting model providerId={} model={}", providerId, modelId);
        final Optional<ProviderConfig> config = configs.find(providerId);
        if (config.isEmpty()) {
            log.debug("Model report refused branch=unknown-provider providerId={}", providerId);
            return Result.err(unknownProvider());
        }
        final ModelFacts facts = factsOf(config.get(), modelId);
        final ModelReport report = profiles.resolve(
                        config.get().effectiveEngine(), modelId, facts, settings.current(providerId, modelId))
                .report();
        log.debug(
                "Model reported providerId={} model={} contextLength={} loaded={} match={} verdict={}",
                providerId,
                modelId,
                report.contextLength(),
                report.loadedContext(),
                report.match(),
                report.verdict());
        return Result.ok(report);
    }

    private ModelFacts factsOf(final ProviderConfig config, final String modelId) {
        try {
            return clients.create(config).facts(modelId);
        } catch (RuntimeException cause) {
            log.warn("Model facts degraded to unknown providerId={} model={}", config.id(), modelId, cause);
            return ModelFacts.unknown();
        }
    }

    // Only LM Studio's own list says whether a model is in memory; asking any other server would be a GET for nothing.
    @Override
    public boolean loadsWithOwnSettings(ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        final Optional<ProviderConfig> config = configs.find(selection.providerId());
        if (config.isEmpty() || config.get().effectiveEngine() != ServerEngine.LM_STUDIO) {
            log.debug(
                    "Model load question skipped providerId={} branch={}",
                    selection.providerId(),
                    config.isEmpty() ? "unknown-provider" : "not-lm-studio");
            return false;
        }
        try {
            final Result<CheckedModelId> checked = clients.create(config.get()).recheckModelId(selection.modelId());
            final CheckedModelId found = checked.data();
            final boolean loads = found != null && Boolean.FALSE.equals(found.loaded());
            log.debug(
                    "Model load question providerId={} model={} loaded={} loadsWithOwnSettings={}",
                    selection.providerId(),
                    selection.modelId(),
                    found == null ? null : found.loaded(),
                    loads);
            return loads;
        } catch (Throwable cause) {
            log.warn(
                    "Model load question degraded to no providerId={} model={}",
                    selection.providerId(),
                    selection.modelId(),
                    cause);
            return false;
        }
    }

    private static AppError unknownProvider() {
        return AppError.of(ErrorCode.validation, "Unknown provider", "The selected provider is not registered.");
    }

    @Override
    public Result<ContextLength> contextLength(String providerId, String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        log.debug("Detecting context length providerId={} model={}", providerId, modelId);
        final Optional<ProviderConfig> config = configs.find(providerId);
        if (config.isEmpty()) {
            log.debug("Context length detection refused branch=unknown-provider providerId={}", providerId);
            return Result.err(unknownProvider());
        }
        try {
            final ContextLength length = new ContextLength(
                    clients.create(config.get()).facts(modelId).contextLength());
            log.debug("Detected context length providerId={} model={} tokens={}", providerId, modelId, length.tokens());
            return Result.ok(length);
        } catch (Throwable cause) {
            log.debug(
                    "Context length detection degraded to unknown providerId={} model={}", providerId, modelId, cause);
            return Result.ok(ContextLength.unknown());
        }
    }
}
