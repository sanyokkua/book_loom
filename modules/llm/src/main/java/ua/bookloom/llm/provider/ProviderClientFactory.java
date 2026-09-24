package ua.bookloom.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpExchange;

/** Creates one dialect client per provider configuration id so client state cannot leak across providers. */
@Slf4j
public final class ProviderClientFactory {

    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, CachedClient> clients = new ConcurrentHashMap<>();

    /** Shares the configured HTTP exchange and tolerant mapper across all clients created by this factory. */
    @Inject
    public ProviderClientFactory(HttpExchange exchange, ObjectMapper mapper) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Returns the cached dialect client for a provider id, constructing it once on the first lookup. */
    public ProviderClient create(ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.debug(
                "Provider client lookup id={} kind={} host={}",
                config.id(),
                config.kind(),
                config.baseUrl().getHost());
        final CachedClient cached = clients.compute(config.id(), (ignored, existing) -> {
            if (existing != null && existing.config().equals(config)) {
                log.debug("Reusing provider client id={} kind={}", config.id(), config.kind());
                return existing;
            }
            if (existing != null) {
                log.debug("Replacing cached provider client id={} because its configuration changed", config.id());
            }
            return new CachedClient(config, createClient(config));
        });
        return Objects.requireNonNull(cached, "cached client").client();
    }

    private ProviderClient createClient(ProviderConfig config) {
        log.debug("Creating provider client id={} kind={}", config.id(), config.kind());
        return switch (config.kind()) {
            case OLLAMA -> new OllamaClient(config, exchange, mapper);
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleClient(config, exchange, mapper);
        };
    }

    private record CachedClient(ProviderConfig config, ProviderClient client) {}
}
