package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** Holds the per-model settings for one process; nothing survives a restart (the SQLite twin does). */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryModelSettingsRepository implements ModelSettingsRepository {

    private final Map<String, ModelSettings> byModel = new ConcurrentHashMap<>();

    @Override
    public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        try {
            final Optional<ModelSettings> found = Optional.ofNullable(byModel.get(key(providerId, modelId)));
            log.debug("Model settings read provider={} model={} present={}", providerId, modelId, found.isPresent());
            return Result.ok(found);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<ModelSettings> save(final ModelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            byModel.put(key(settings.providerId(), settings.modelId()), settings);
            log.debug(
                    "Model settings stored provider={} model={} refused={}",
                    settings.providerId(),
                    settings.modelId(),
                    settings.refused().size());
            return Result.ok(settings);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> delete(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        try {
            final boolean removed = byModel.remove(key(providerId, modelId)) != null;
            log.debug("Model settings deleted provider={} model={} existed={}", providerId, modelId, removed);
            return Result.ok(removed);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<ModelSettings>> all() {
        try {
            log.debug("Model settings listed count={}", byModel.size());
            return Result.ok(List.copyOf(byModel.values()));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static String key(final String providerId, final String modelId) {
        return providerId + '\u0000' + modelId;
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Model settings storage failure",
                "The model's settings could not be stored.",
                null,
                cause);
        log.error("Unexpected model settings repository failure code={}", error.code(), cause);
        return error;
    }
}
