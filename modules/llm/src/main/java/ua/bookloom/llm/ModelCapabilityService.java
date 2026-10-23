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
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.llm.provider.ProviderClient;
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

    @Override
    public Result<ModelReport> report(String providerId, String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        log.debug("Reporting model providerId={} model={}", providerId, modelId);
        final Optional<ProviderConfig> config = configs.find(providerId);
        if (config.isEmpty()) {
            log.debug("Model report refused branch=unknown-provider providerId={}", providerId);
            return Result.err(unknownProvider());
        }
        try {
            final ProviderClient client = clients.create(config.get());
            final ModelReport report = new ModelReport(
                    client.contextLength(modelId).orElse(null),
                    client.loadedContextLength(modelId).orElse(null));
            log.debug(
                    "Model reported providerId={} model={} contextLength={} loaded={}",
                    providerId,
                    modelId,
                    report.contextLength(),
                    report.loadedContext());
            return Result.ok(report);
        } catch (Throwable cause) {
            log.warn("Model report degraded to unknown providerId={} model={}", providerId, modelId, cause);
            return Result.ok(new ModelReport(null, null));
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
                    clients.create(config.get()).contextLength(modelId).orElse(null));
            log.debug("Detected context length providerId={} model={} tokens={}", providerId, modelId, length.tokens());
            return Result.ok(length);
        } catch (Throwable cause) {
            log.debug(
                    "Context length detection degraded to unknown providerId={} model={}", providerId, modelId, cause);
            return Result.ok(ContextLength.unknown());
        }
    }
}
