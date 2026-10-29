package ua.bookloom.llm.engine;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ServerEngine;

/**
 * The hosted services known by their host name, which need no probe: OpenRouter, Ollama's own cloud, the hosted
 * OpenAI-shaped APIs, and the two whose own request shape is not built yet.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HostRules {

    private static final Found GENERIC = Found.of(ServerEngine.GENERIC);
    private static final Found OPENROUTER = Found.of(ServerEngine.OPENROUTER);
    private static final Found OLLAMA_CLOUD = Found.of(ServerEngine.OLLAMA);
    private static final Found NOT_YET = new Found(ServerEngine.GENERIC, null, false, Suitability.NOT_SUPPORTED_YET);

    private static final Map<String, Found> HOSTS = Map.ofEntries(
            Map.entry("openrouter.ai", OPENROUTER),
            Map.entry("eu.openrouter.ai", OPENROUTER),
            Map.entry("us.openrouter.ai", OPENROUTER),
            Map.entry("ollama.com", OLLAMA_CLOUD),
            Map.entry("www.ollama.com", OLLAMA_CLOUD),
            Map.entry("api.openai.com", GENERIC),
            Map.entry("api.groq.com", GENERIC),
            Map.entry("api.together.ai", GENERIC),
            Map.entry("api.fireworks.ai", GENERIC),
            Map.entry("api.deepinfra.com", GENERIC),
            Map.entry("api.anthropic.com", NOT_YET),
            Map.entry("generativelanguage.googleapis.com", NOT_YET));

    /**
     * The engine a known host names.
     *
     * @param baseUrl the provider's address
     * @return the engine of a known host, or empty when the host must be probed
     */
    static Optional<Found> match(final URI baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        final String host = baseUrl.getHost() == null ? "" : baseUrl.getHost().toLowerCase(Locale.ROOT);
        final Optional<Found> known = Optional.ofNullable(HOSTS.get(host));
        log.debug(
                "Engine host rule host={} known={}",
                host,
                known.map(Found::engine).orElse(null));
        return known;
    }
}
