package ua.bookloom.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
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

    private static final int PRESETS = 2;
    private final List<ProviderConfig> configs = new CopyOnWriteArrayList<>();
    private final AtomicInteger fxThreadCalls = new AtomicInteger();
    private final AtomicInteger saves = new AtomicInteger();
    private volatile boolean failSaves;

    /** The two presets: {@code ollama} on port 11434 and {@code lmstudio} on port 1234. */
    public FakeProviderConfigs() {
        save(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        save(new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://localhost:1234/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
    }

    @Override
    public Result<ProviderConfig> save(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        watch();
        if (failSaves) {
            return Result.err(AppError.of(ErrorCode.internal, "Not saved", "The store refused."));
        }
        configs.removeIf(existing -> existing.id().equals(config.id()));
        configs.add(config);
        saves.incrementAndGet();
        return Result.ok(config);
    }

    /** How many saves were accepted, the two presets not counted. */
    public int saves() {
        return saves.get() - PRESETS;
    }

    /** How many calls came from the FX Application Thread, which storage must never see. */
    public int fxThreadCalls() {
        return fxThreadCalls.get();
    }

    /** Makes every later save fail. */
    public void failSaves() {
        failSaves = true;
    }

    private void watch() {
        if (Platform.isFxApplicationThread()) {
            fxThreadCalls.incrementAndGet();
        }
    }

    @Override
    public Optional<ProviderConfig> find(final String id) {
        watch();
        return configs.stream().filter(config -> config.id().equals(id)).findFirst();
    }

    @Override
    public Result<Boolean> delete(final String id) {
        Objects.requireNonNull(id, "id");
        watch();
        return Result.ok(configs.removeIf(config -> config.id().equals(id)));
    }

    @Override
    public List<ProviderConfig> all() {
        watch();
        final List<ProviderConfig> sorted = new ArrayList<>(configs);
        sorted.sort(Comparator.comparing(ProviderConfig::id));
        return List.copyOf(sorted);
    }
}
