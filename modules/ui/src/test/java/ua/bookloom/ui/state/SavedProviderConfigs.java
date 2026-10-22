package ua.bookloom.ui.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;

/** A registry that starts empty and records what was saved, in order. */
final class SavedProviderConfigs implements ProviderConfigs {

    private final Map<String, ProviderConfig> byId = new LinkedHashMap<>();
    private final List<ProviderConfig> saved = new CopyOnWriteArrayList<>();

    @Override
    public Result<ProviderConfig> save(final ProviderConfig config) {
        byId.put(config.id(), config);
        saved.add(config);
        return Result.ok(config);
    }

    @Override
    public Optional<ProviderConfig> find(final String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Result<Boolean> delete(final String id) {
        return Result.ok(byId.remove(id) != null);
    }

    @Override
    public List<ProviderConfig> all() {
        return List.copyOf(byId.values());
    }

    List<ProviderConfig> saved() {
        return List.copyOf(saved);
    }
}
