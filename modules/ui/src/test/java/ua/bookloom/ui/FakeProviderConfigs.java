package ua.bookloom.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/**
 * A hand-written {@link ProviderConfigs} holding the two built-in presets, listing them sorted by id the way the real
 * registry does, so that a view model which must show Ollama first is really tested against a source that puts
 * {@code lmstudio} first.
 */
public final class FakeProviderConfigs implements ProviderConfigs {

    private final List<ProviderConfig> configs = new CopyOnWriteArrayList<>();

    /** The two presets: {@code ollama} on port 11434 and {@code lmstudio} on port 1234. */
    public FakeProviderConfigs() {
        register(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        register(new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://localhost:1234/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
    }

    @Override
    public Result<ProviderConfig> register(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        configs.add(config);
        return Result.ok(config);
    }

    @Override
    public Optional<ProviderConfig> find(final String id) {
        return configs.stream().filter(config -> config.id().equals(id)).findFirst();
    }

    @Override
    public List<ProviderConfig> all() {
        final List<ProviderConfig> sorted = new ArrayList<>(configs);
        sorted.sort(Comparator.comparing(ProviderConfig::id));
        return List.copyOf(sorted);
    }
}
