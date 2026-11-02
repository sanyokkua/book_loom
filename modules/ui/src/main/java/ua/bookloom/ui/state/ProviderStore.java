package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.SettingsRepository;

/**
 * Where the providers and the model chosen last are kept.
 *
 * @param configs the registry the rows are read from
 * @param settingsStore where the model chosen last time is read from
 */
public record ProviderStore(ProviderConfigs configs, SettingsRepository settingsStore) {

    /** Rejects a missing part. */
    @Inject
    public ProviderStore {
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(settingsStore, "settingsStore");
    }
}
