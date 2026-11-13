package ua.bookloom.ui.state;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/** The storage calls behind the Providers form. Every method blocks, so it is called off the FX thread only. */
@Slf4j
final class ProviderStorage {

    private final ProviderConfigs configs;
    private final ModelSettingsRepository modelSettings;
    private final SettingsRepository settings;

    ProviderStorage(
            final ProviderConfigs configs,
            final ModelSettingsRepository modelSettings,
            final SettingsRepository settings) {
        this.configs = configs;
        this.modelSettings = modelSettings;
        this.settings = settings;
    }

    /**
     * Saves the provider and the model typed for it, which it keeps for when it is made current again; for the current
     * provider the model is the current model too. A blank model is kept as typed, i.e. none.
     */
    Result<Boolean> persist(final ProviderConfig config, final String model, final boolean current) {
        final Result<ProviderConfig> stored = configs.save(config);
        if (stored.isErr()) {
            return Result.err(Objects.requireNonNull(stored.error(), ERROR));
        }
        final String chosen = model.strip();
        final Result<?> kept = chosen.isEmpty()
                ? settings.remove(AppSetting.providerModel(config.id()))
                : settings.put(AppSetting.providerModel(config.id()), chosen);
        log.debug("provider '{}' keeps a model: {}, current {}", config.id(), !chosen.isEmpty(), current);
        if (kept.isErr()) {
            return modelNotWritten(config.id(), kept);
        }
        if (!current) {
            return Result.ok(true);
        }
        final Result<?> written = chosen.isEmpty()
                ? settings.remove(AppSetting.CURRENT_MODEL)
                : settings.put(AppSetting.CURRENT_MODEL, new ModelSelection(config.id(), chosen));
        return written.isErr() ? modelNotWritten(config.id(), written) : Result.ok(true);
    }

    private static Result<Boolean> modelNotWritten(final String providerId, final Result<?> written) {
        final AppError error = Objects.requireNonNull(written.error(), ERROR);
        log.warn("provider '{}' was saved but its model was not: {}", providerId, error.code());
        return Result.err(error);
    }

    /**
     * Writes the provider and model as the current ones; a blank model is kept as typed, i.e. none. The provider that
     * was current keeps its model, and so does the new one when a model is named, so either can be made current again
     * with the model chosen for it.
     */
    Result<Boolean> makeCurrent(final ModelSelection chosen) {
        keepEarlier(chosen.providerId());
        final Result<?> written = settings.put(AppSetting.CURRENT_MODEL, chosen);
        if (written.isErr()) {
            log.warn("provider '{}' could not be made current", chosen.providerId());
            return Result.err(Objects.requireNonNull(written.error(), ERROR));
        }
        if (chosen.modelId().isBlank()) {
            log.debug("provider '{}' made current with no model: the model it keeps stays", chosen.providerId());
            return Result.ok(true);
        }
        final Result<?> kept = settings.put(AppSetting.providerModel(chosen.providerId()), chosen.modelId());
        return kept.isErr() ? modelNotWritten(chosen.providerId(), kept) : Result.ok(true);
    }

    // A current model set without a save (the first-launch look, an earlier version) is kept for its provider too.
    private void keepEarlier(final String next) {
        final Result<Optional<ModelSelection>> read = settings.get(AppSetting.CURRENT_MODEL);
        final @Nullable ModelSelection earlier = read.isOk()
                ? Objects.requireNonNull(read.data(), "current model").orElse(null)
                : null;
        if (earlier == null
                || earlier.providerId().equals(next)
                || earlier.modelId().isBlank()) {
            log.debug("no earlier model to keep before '{}' is made current", next);
            return;
        }
        if (settings.put(AppSetting.providerModel(earlier.providerId()), earlier.modelId())
                .isErr()) {
            log.warn("the model of provider '{}' could not be kept", earlier.providerId());
        }
    }

    /**
     * Deletes the provider's per-model settings, then the provider, then, for the current one, the current model. The
     * provider goes last, so {@code false} always means it is still stored; a current model left behind after the
     * provider is gone points at nothing and is only logged.
     */
    boolean remove(final String id, final boolean current) {
        final Result<List<ModelSettings>> all = modelSettings.all();
        final List<ModelSettings> rows = all.data();
        if (all.isErr() || rows == null) {
            log.warn("the per-model settings of provider '{}' could not be read: it is kept", id);
            return false;
        }
        final long failed = rows.stream()
                .filter(row -> row.providerId().equals(id))
                .filter(row ->
                        modelSettings.delete(row.providerId(), row.modelId()).isErr())
                .count();
        if (failed > 0) {
            log.warn("{} per-model setting(s) of provider '{}' could not be deleted: it is kept", failed, id);
            return false;
        }
        if (configs.delete(id).isErr()) {
            log.warn("provider '{}' could not be deleted", id);
            return false;
        }
        if (settings.remove(AppSetting.providerModel(id)).isErr()) {
            log.warn("the model kept for deleted provider '{}' could not be removed", id);
        }
        if (current && settings.remove(AppSetting.CURRENT_MODEL).isErr()) {
            log.warn("the current model of deleted provider '{}' could not be removed; it points at nothing", id);
        }
        log.debug("provider '{}' deleted, current {}", id, current);
        return true;
    }
}
