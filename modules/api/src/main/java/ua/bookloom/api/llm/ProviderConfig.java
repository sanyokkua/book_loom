package ua.bookloom.api.llm;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The endpoint, sign-in and overrides describing one provider instance.
 *
 * <p>Semantic validation lives in {@link ProviderConfigRules}; this record enforces only that its required components
 * are present. A credential is never held here, only the name of the environment variable that carries it.
 *
 * @param id the registry key
 * @param kind the provider wire dialect
 * @param baseUrl the provider's base endpoint
 * @param connectTimeout the connection timeout
 * @param requestTimeout the timeout applied to an individual request
 * @param displayName the name shown to the person, or null to show the id
 * @param auth how requests sign in
 * @param apiKeyHeader the header carrying the key when {@code auth} is {@code API_KEY}, else null
 * @param credentialRef the environment variable name holding the key or token, never its value; null for none
 * @param modelsPath the model-list path replacing the dialect's default, or null
 * @param chatPath the chat path replacing the dialect's default (not allowed for Ollama), or null
 * @param headers extra headers; a credential-like header's value must be a {@code ${env:NAME}} reference
 * @param customModels model names added to the discovered list
 */
public record ProviderConfig(
        String id,
        ProviderKind kind,
        URI baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        @Nullable String displayName,
        ProviderAuth auth,
        @Nullable String apiKeyHeader,
        @Nullable String credentialRef,
        @Nullable String modelsPath,
        @Nullable String chatPath,
        Map<String, String> headers,
        List<String> customModels) {

    /** Default time allowed to establish a provider connection. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Default time allowed for an individual provider request. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(3);

    /** Rejects missing required components and copies the collections; semantic validation is elsewhere. */
    public ProviderConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(auth, "auth");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        customModels = List.copyOf(Objects.requireNonNull(customModels, "customModels"));
    }

    /** A provider with no sign-in, overrides, headers or custom models. */
    public ProviderConfig(
            final String id,
            final ProviderKind kind,
            final URI baseUrl,
            final Duration connectTimeout,
            final Duration requestTimeout) {
        this(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                null,
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of());
    }

    /**
     * Returns a copy using the supplied endpoint.
     *
     * @param baseUrl the replacement base URL
     * @return a copy with only {@code baseUrl} changed
     */
    public ProviderConfig withBaseUrl(final URI baseUrl) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy using the supplied per-request timeout.
     *
     * @param requestTimeout the replacement request timeout
     * @return a copy with only {@code requestTimeout} changed
     */
    public ProviderConfig withRequestTimeout(final Duration requestTimeout) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied display name.
     *
     * @param displayName the replacement name, or null to show the id
     * @return a copy with only {@code displayName} changed
     */
    public ProviderConfig withDisplayName(final @Nullable String displayName) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied sign-in method.
     *
     * @param auth the replacement method
     * @return a copy with only {@code auth} changed
     */
    public ProviderConfig withAuth(final ProviderAuth auth) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied key header name.
     *
     * @param apiKeyHeader the replacement header name, or null
     * @return a copy with only {@code apiKeyHeader} changed
     */
    public ProviderConfig withApiKeyHeader(final @Nullable String apiKeyHeader) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied credential reference.
     *
     * @param credentialRef the name of an environment variable, or null
     * @return a copy with only {@code credentialRef} changed
     */
    public ProviderConfig withCredentialRef(final @Nullable String credentialRef) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied model-list path.
     *
     * @param modelsPath the replacement path, or null for the dialect's default
     * @return a copy with only {@code modelsPath} changed
     */
    public ProviderConfig withModelsPath(final @Nullable String modelsPath) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied chat path.
     *
     * @param chatPath the replacement path, or null for the dialect's default
     * @return a copy with only {@code chatPath} changed
     */
    public ProviderConfig withChatPath(final @Nullable String chatPath) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied extra headers.
     *
     * @param headers the replacement headers
     * @return a copy with only {@code headers} changed
     */
    public ProviderConfig withHeaders(final Map<String, String> headers) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }

    /**
     * Returns a copy with the supplied custom model names.
     *
     * @param customModels the replacement names
     * @return a copy with only {@code customModels} changed
     */
    public ProviderConfig withCustomModels(final List<String> customModels) {
        return new ProviderConfig(
                id,
                kind,
                baseUrl,
                connectTimeout,
                requestTimeout,
                displayName,
                auth,
                apiKeyHeader,
                credentialRef,
                modelsPath,
                chatPath,
                headers,
                customModels);
    }
}
