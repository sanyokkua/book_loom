package ua.bookloom.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpExchange;

/** Creates one dialect client per provider configuration id so client state cannot leak across providers. */
@Slf4j
public final class ProviderClientFactory {

    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final LongSupplier nanoTime;
    private final EngineFacts facts;
    private final BiFunction<String, String, ProbeVerdicts> verdicts;
    private final ConcurrentMap<String, CachedClient> clients = new ConcurrentHashMap<>();

    /** As the injected form, with its own session facts and no stored check verdicts. */
    public ProviderClientFactory(HttpExchange exchange, ObjectMapper mapper, LongSupplier nanoTime) {
        this(
                exchange,
                mapper,
                nanoTime,
                new EngineFacts(exchange, mapper),
                (providerId, modelId) -> ProbeVerdicts.none());
    }

    /**
     * Shares the configured HTTP exchange, tolerant mapper, time source, the session's engine facts and the models'
     * stored settings across all clients created by this factory.
     */
    @Inject
    public ProviderClientFactory(
            HttpExchange exchange,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineFacts facts,
            ModelSettingsCache settings) {
        this(
                exchange,
                mapper,
                nanoTime,
                facts,
                (providerId, modelId) -> Objects.requireNonNull(settings, "settings")
                        .current(providerId, modelId)
                        .probeVerdicts());
    }

    private ProviderClientFactory(
            HttpExchange exchange,
            ObjectMapper mapper,
            LongSupplier nanoTime,
            EngineFacts facts,
            BiFunction<String, String, ProbeVerdicts> verdicts) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.verdicts = Objects.requireNonNull(verdicts, "verdicts");
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

    /**
     * What a server last said about a model in this session, without asking it again.
     *
     * @param providerId the provider's id; never null
     * @param modelId the model id; never null
     * @return the facts, or empty when no person's action read them this session
     */
    public Optional<ModelFacts> heldFacts(String providerId, String modelId) {
        return facts.lastRead(providerId, modelId);
    }

    /** Builds a client for a description that is not saved, leaving the cache and any saved client of that id alone. */
    public ProviderClient createUncached(ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.debug("Creating uncached provider client id={} kind={}", config.id(), config.kind());
        return createClient(config);
    }

    private ProviderClient createClient(ProviderConfig config) {
        log.debug("Creating provider client id={} kind={}", config.id(), config.kind());
        return switch (config.kind()) {
            case OLLAMA -> new OllamaClient(config, exchange, mapper, facts);
            case OPENAI_COMPATIBLE ->
                new OpenAiCompatibleClient(
                        config, exchange, mapper, nanoTime, facts, modelId -> verdicts.apply(config.id(), modelId));
        };
    }

    private record CachedClient(ProviderConfig config, ProviderClient client) {}
}
