package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
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

    Result<Boolean> persist(final ProviderConfig config, final @Nullable String currentModel) {
        final Result<ProviderConfig> stored = configs.save(config);
        if (stored.isErr()) {
            return Result.err(Objects.requireNonNull(stored.error(), "error"));
        }
        if (currentModel == null) {
            return Result.ok(true);
        }
        final Result<?> written = currentModel.isEmpty()
                ? settings.remove(AppSetting.CURRENT_MODEL)
                : settings.put(AppSetting.CURRENT_MODEL, new ModelSelection(config.id(), currentModel));
        if (written.isErr()) {
            final AppError error = Objects.requireNonNull(written.error(), "error");
            log.warn("provider '{}' was saved but its current model was not: {}", config.id(), error.code());
            return Result.err(error);
        }
        return Result.ok(true);
    }

    /** Writes the provider and model as the current ones; a blank model is kept as typed, i.e. none. */
    Result<Boolean> makeCurrent(final ModelSelection chosen) {
        final Result<?> written = settings.put(AppSetting.CURRENT_MODEL, chosen);
        if (written.isErr()) {
            log.warn("provider '{}' could not be made current", chosen.providerId());
            return Result.err(Objects.requireNonNull(written.error(), "error"));
        }
        return Result.ok(true);
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
        if (current && settings.remove(AppSetting.CURRENT_MODEL).isErr()) {
            log.warn("the current model of deleted provider '{}' could not be removed; it points at nothing", id);
        }
        log.debug("provider '{}' deleted, current {}", id, current);
        return true;
    }
}
