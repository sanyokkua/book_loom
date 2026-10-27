package ua.bookloom.llm.testing;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** A hand-written registry holding the two local presets; {@code :llm} cannot see {@code :persistence}. */
public final class FakeProviderConfigs implements ProviderConfigs {

    private final Map<String, ProviderConfig> configs = new ConcurrentHashMap<>();

    /** Seeds the Ollama and LM Studio presets. */
    public FakeProviderConfigs() {
        configs.put("ollama", preset("ollama", ProviderKind.OLLAMA, "http://localhost:11434"));
        configs.put("lmstudio", preset("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:1234/v1"));
    }

    @Override
    public Result<ProviderConfig> save(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final ProviderConfigRules.Check check = ProviderConfigRules.check(config);
        if (!check.isValid()) {
            return Result.err(check.errors().get(0));
        }
        configs.put(config.id(), config);
        return Result.ok(config);
    }

    @Override
    public Result<Boolean> delete(final String id) {
        return Result.ok(configs.remove(id) != null);
    }

    @Override
    public Optional<ProviderConfig> find(final String id) {
        return Optional.ofNullable(configs.get(id));
    }

    @Override
    public List<ProviderConfig> all() {
        return configs.values().stream()
                .sorted(Comparator.comparing(ProviderConfig::id))
                .toList();
    }

    private static ProviderConfig preset(final String id, final ProviderKind kind, final String url) {
        return new ProviderConfig(
                id,
                kind,
                URI.create(url),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }
}
