package ua.bookloom.llm;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.llm.provider.ProviderClientFactory;

/**
 * Answers "which models does this provider offer" by delegating to the client the factory already builds. Listing is a
 * catalogue read, not inference, so it deliberately does not pass through the inference gate.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ModelCatalogService implements ModelCatalog {

    private final ProviderConfigs configs;
    private final ProviderClientFactory clients;

    @Override
    public Result<List<ModelInfo>> listModels(String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        log.debug("Listing models providerId={}", providerId);
        final Result<List<ModelInfo>> result;
        try {
            result = discover(providerId);
        } catch (Throwable cause) {
            return Result.err(internalError(providerId, cause));
        }
        logOutcome(providerId, result);
        return result;
    }

    private Result<List<ModelInfo>> discover(String providerId) {
        final Optional<ProviderConfig> config = configs.find(providerId);
        if (config.isEmpty()) {
            log.debug("Model listing refused branch=unknown-provider providerId={}", providerId);
            return Result.err(
                    AppError.of(ErrorCode.validation, "Unknown provider", "The selected provider is not registered."));
        }
        return clients.create(config.get()).listModels().result();
    }

    private static void logOutcome(String providerId, Result<List<ModelInfo>> result) {
        if (result.isOk()) {
            log.debug(
                    "Listed models providerId={} count={}",
                    providerId,
                    Objects.requireNonNull(result.data(), "model list").size());
            return;
        }
        log.warn(
                "Model listing failed providerId={} code={}",
                providerId,
                Objects.requireNonNull(result.error(), "error").code());
    }

    private static AppError internalError(String providerId, Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Model listing failure", "The provider's models could not be listed.", null, cause);
        log.error("Unexpected model listing failure providerId={} code={}", providerId, error.code(), cause);
        return error;
    }
}
