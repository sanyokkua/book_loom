package ua.bookloom.api.llm;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * The endpoint and timeouts describing one provider instance.
 *
 * <p>URL-scheme and positive-timeout validation belongs to the provider registry; this record enforces only that its
 * required components are present.
 *
 * @param id the registry key
 * @param kind the provider wire dialect
 * @param baseUrl the provider's base endpoint
 * @param connectTimeout the connection timeout
 * @param requestTimeout the timeout applied to an individual request
 */
public record ProviderConfig(
        String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout) {

    /** Default time allowed to establish a provider connection. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Default time allowed for an individual provider request. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(3);

    /** Rejects missing required components without applying registry-level semantic validation. */
    public ProviderConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Returns a copy using the supplied endpoint.
     *
     * @param baseUrl the replacement base URL
     * @return a copy with only {@code baseUrl} changed
     */
    public ProviderConfig withBaseUrl(URI baseUrl) {
        return new ProviderConfig(id, kind, baseUrl, connectTimeout, requestTimeout);
    }

    /**
     * Returns a copy using the supplied per-request timeout.
     *
     * @param requestTimeout the replacement request timeout
     * @return a copy with only {@code requestTimeout} changed
     */
    public ProviderConfig withRequestTimeout(Duration requestTimeout) {
        return new ProviderConfig(id, kind, baseUrl, connectTimeout, requestTimeout);
    }
}
