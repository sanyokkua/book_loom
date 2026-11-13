package ua.bookloom.llm.engine;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.util.text.Slashes;

/**
 * The provider's server root and its OpenAI path, both as short-timeout copies of the description the probes are sent
 * with.
 *
 * <p>The root is the base URL with a trailing {@code /}, {@code /v1}, {@code /api} or {@code /api/v1} removed, and then
 * an {@code /engines} or {@code /engines/llama.cpp} remainder, which is the root of an OpenAI path that a server keeps
 * below its own routes.
 *
 * @param root the description at the server's root, where {@code /api/version} and {@code /health} live
 * @param base the description at the configured base URL, where {@code models} lives
 * @param baseIsRootV1 whether the base is the root or the root's {@code /v1}, so the step-one {@code /v1/models}
 *     reply answers the base's model list too
 */
@Slf4j
record BaseUrlRoot(ProviderConfig root, ProviderConfig base, boolean baseIsRootV1) {

    /** Each probe's connect and read limit: a server that answers at all answers a GET at once. */
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    private static final List<String> API_SUFFIXES = List.of("/api/v1", "/v1", "/api");
    private static final List<String> ENGINE_SUFFIXES = List.of("/engines/llama.cpp", "/engines");
    private static final String ANYTHING_LLM_PATH = "/api/v1/openai";

    BaseUrlRoot {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(base, "base");
    }

    /**
     * Splits a description into its root and base.
     *
     * @param config the provider description
     * @return the two short-timeout copies, or empty when the base URL has no usable root
     */
    static Optional<BaseUrlRoot> of(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final String basePath = trimmed(config.baseUrl().getPath());
        final String rootPath = rootPath(basePath);
        // An Ollama-native provider's model-list override names a tags route, not an OpenAI list, so its OpenAI list
        // is always the root's /v1/models.
        final boolean reusable = config.kind() == ProviderKind.OLLAMA
                || ((basePath.equals(rootPath) || basePath.equals(rootPath + "/v1"))
                        && (config.modelsPath() == null || config.modelsPath().isBlank()));
        log.debug(
                "Provider address split host={} basePath={} rootPath={} baseIsRootV1={}",
                config.baseUrl().getHost(),
                basePath,
                rootPath,
                reusable);
        final ProviderConfig probing = probeCopy(config);
        return atPath(probing, rootPath).map(root -> new BaseUrlRoot(root, probing, reusable));
    }

    /** Whether the base path is AnythingLLM's OpenAI path, whose "model" is a workspace over the person's documents. */
    static boolean isAnythingLlm(final ProviderConfig config) {
        return trimmed(config.baseUrl().getPath()).endsWith(ANYTHING_LLM_PATH);
    }

    private static String rootPath(final String basePath) {
        final String withoutApi = stripFirst(basePath, API_SUFFIXES);
        return stripFirst(withoutApi, ENGINE_SUFFIXES);
    }

    private static String stripFirst(final String path, final List<String> suffixes) {
        return suffixes.stream()
                .filter(path::endsWith)
                .findFirst()
                .map(suffix -> path.substring(0, path.length() - suffix.length()))
                .orElse(path);
    }

    private static String trimmed(final String path) {
        return path == null ? "" : Slashes.trimTrailing(path);
    }

    private static Optional<ProviderConfig> atPath(final ProviderConfig config, final String path) {
        final URI base = config.baseUrl();
        try {
            return Optional.of(config.withBaseUrl(
                    new URI(base.getScheme(), null, base.getHost(), base.getPort(), path, null, null)));
        } catch (URISyntaxException _) {
            log.debug("Provider base URL has no usable root host={}", base.getHost());
            return Optional.empty();
        }
    }

    private static ProviderConfig probeCopy(final ProviderConfig config) {
        return new ProviderConfig(
                config.id(),
                config.kind(),
                config.baseUrl(),
                PROBE_TIMEOUT,
                PROBE_TIMEOUT,
                config.displayName(),
                config.auth(),
                config.apiKeyHeader(),
                config.credentialRef(),
                config.modelsPath(),
                config.chatPath(),
                config.headers(),
                config.customModels(),
                config.engine(),
                config.engineChoice(),
                config.engineVersion(),
                config.nativeOfferDeclined());
    }
}
