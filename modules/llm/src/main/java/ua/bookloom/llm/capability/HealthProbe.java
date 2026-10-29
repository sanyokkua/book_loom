package ua.bookloom.llm.capability;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Asks a llama-server's {@code /health} whether it is still loading its model. Every failure — transport, a missing
 * route, a success that holds only an error — is "not loading", so the wake goes on to its usual checks.
 */
@Slf4j
public final class HealthProbe {

    private static final String HEALTH_PATH = "/health";
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final Pattern LOADING = Pattern.compile("\\bloading\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_ONLY = Pattern.compile("\\A\\s*\\{\\s*\"error\"\\s*:");

    private final HttpExchange exchange;

    /** Shares the client's exchange. */
    public HealthProbe(final HttpExchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
    }

    /**
     * Reads the health route at the provider's host root.
     *
     * @param config the provider; never null
     * @return {@code true} if the server answered that it is loading, {@code false} otherwise
     */
    public boolean isLoading(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final Optional<ProviderConfig> rooted = EndpointPaths.atHostRoot(config);
        if (rooted.isEmpty()) {
            return false;
        }
        final Result<HttpReply> answered = exchange.get(rooted.get(), HEALTH_PATH);
        if (answered.isErr()) {
            log.debug(
                    "Health check unanswered host={} code={}",
                    config.baseUrl().getHost(),
                    Objects.requireNonNull(answered.error(), "error").code());
            return false;
        }
        final HttpReply reply = Objects.requireNonNull(answered.data(), "reply");
        log.trace("Health check reply host={} body={}", config.baseUrl().getHost(), reply.body());
        final boolean errorOnly =
                isSuccess(reply.status()) && ERROR_ONLY.matcher(reply.body()).find();
        final boolean loading = reply.status() != HTTP_NOT_FOUND
                && !errorOnly
                && LOADING.matcher(reply.body()).find();
        log.debug(
                "Health check read host={} status={} errorOnly={} loading={}",
                config.baseUrl().getHost(),
                reply.status(),
                errorOnly,
                loading);
        return loading;
    }

    private static boolean isSuccess(final int status) {
        return status >= HTTP_SUCCESS_MIN && status <= HTTP_SUCCESS_MAX;
    }
}
