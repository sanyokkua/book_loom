package ua.bookloom.ui.state;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;

/**
 * The model each provider keeps for when it is made current again — the open form of a provider that is not the
 * current one shows it, so Use this one makes it current with it — as stored at the last read of the registry and as
 * chosen since; {@link ProviderStorage} writes the same models. A blank model never erases the one a provider keeps.
 * FX thread only, apart from {@link #read}.
 */
@Slf4j
final class KeptModels {

    private final Map<String, String> byProvider = new HashMap<>();

    /** Reads the stored models of the given providers; blocks, so never on the FX thread. */
    static Map<String, String> read(final List<ProviderConfig> configs, final SettingsRepository store) {
        final Map<String, String> kept = new HashMap<>();
        configs.forEach(config -> {
            final Result<Optional<String>> stored = store.get(AppSetting.providerModel(config.id()));
            if (stored.isOk()) {
                Objects.requireNonNull(stored.data(), "stored model").ifPresent(found -> kept.put(config.id(), found));
            }
        });
        log.debug("providers keeping a model: {} of {}", kept.size(), configs.size());
        return kept;
    }

    /** Takes what a read found in place of what was held. */
    void replace(final Map<String, String> read) {
        byProvider.clear();
        byProvider.putAll(read);
    }

    /** Notes a switch of the current provider: the one left and the one chosen each keep their model. */
    void switched(final String leftId, final String leftModel, final ModelSelection chosen) {
        keep(leftId, leftModel);
        keep(chosen.providerId(), chosen.modelId());
    }

    /** {@return the model a provider keeps, blank when it keeps none} */
    String of(final String providerId) {
        final String model = byProvider.getOrDefault(providerId, "");
        log.debug("provider '{}' keeps a model: {}", providerId, !model.isEmpty());
        return model;
    }

    private void keep(final String providerId, final String modelId) {
        if (!providerId.isEmpty() && !modelId.isBlank()) {
            byProvider.put(providerId, modelId);
        }
    }
}
