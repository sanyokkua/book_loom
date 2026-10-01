package ua.bookloom.llm.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;

/** Sends one JSON request per call; the provider client owns response decoding and HTTP-purpose decisions. */
@Slf4j
public final class HttpExchange {

    private static final String JSON_HEADER_NAMES = "Content-Type,Accept";

    private final HttpClients clients;
    private final LongSupplier nanoTime;

    /**
     * Shares the configured client holder while keeping each request's timeout independent. A streamed reply's waits
     * are measured with the system's monotonic clock; it is kept apart from the shared verification clock binding,
     * which a test scripts with a fixed number of readings.
     */
    public HttpExchange(HttpClients clients) {
        this(clients, System::nanoTime);
    }

    /** Also takes the clock a streamed reply's waits are measured with. */
    public HttpExchange(HttpClients clients, LongSupplier nanoTime) {
        this.clients = Objects.requireNonNull(clients, "clients");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Sends a JSON-header GET and returns the raw status, headers, and body for the caller to classify. */
    public Result<HttpReply> get(ProviderConfig config, String path) {
        return send(config, path, null);
    }

    /** Sends a JSON POST and returns even non-2xx responses unchanged for the caller to classify. */
    public Result<HttpReply> post(ProviderConfig config, String path, String body) {
        Objects.requireNonNull(body, "body");
        return send(config, path, body);
    }

    /**
     * Sends a JSON POST whose reply is streamed line by line, and returns the whole reply once it ends.
     *
     * @param config the provider config; its request timeout bounds the whole reply
     * @param path the endpoint path
     * @param body the request body
     * @param idle the longest the reply may send nothing, before its first line or between two lines
     * @return the status, headers and every line of the body, each followed by a newline; or a {@code timeout} error
     *     for a reply that stalled or ran past its limit, {@code cancelled} for an interrupted caller, and the transport
     *     failure's own code otherwise
     */
    public Result<HttpReply> postStreamed(ProviderConfig config, String path, String body, Duration idle) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(idle, "idle");
        log.debug(
                "HTTP streamed exchange request host={} path={} limit={} idle={} bodyLength={}",
                config.baseUrl().getHost(),
                path,
                config.requestTimeout(),
                idle,
                body.length());
        if (log.isTraceEnabled()) {
            log.trace(
                    "HTTP request body host={} path={} body={}",
                    config.baseUrl().getHost(),
                    path,
                    body);
        }
        final URI endpoint = endpoint(config.baseUrl(), path);
        try {
            return streamed(config, endpoint, body, idle);
        } catch (RuntimeException failure) {
            return Result.err(HttpErrorMapper.map(failure, config, null, "POST", endpoint.getPath()));
        }
    }

    private Result<HttpReply> streamed(ProviderConfig config, URI endpoint, String body, Duration idle) {
        final StreamedLines lines = new StreamedLines();
        final AtomicReference<HttpResponse.@Nullable ResponseInfo> head = new AtomicReference<>();
        final long started = nanoTime.getAsLong();
        final CompletableFuture<HttpResponse<Void>> exchange = clients.forConnectTimeout(config.connectTimeout())
                .sendAsync(buildRequest(config, endpoint, body), info -> {
                    head.set(info);
                    return HttpResponse.BodySubscribers.fromLineSubscriber(lines);
                });
        // The stage only forwards a failure to the reader; the reader, not this stage, reports the outcome.
        final var _ = exchange.whenComplete((_, failure) -> {
            if (failure != null) {
                lines.exchangeFailed(failure);
            }
        });
        return switch (lines.collect(idle, config.requestTimeout(), started, nanoTime)) {
            case StreamedLines.Outcome.Collected collected -> {
                final HttpResponse.ResponseInfo info = Objects.requireNonNull(head.get(), "response head");
                final HttpReply reply =
                        new HttpReply(info.statusCode(), info.headers().map(), collected.body());
                logResponse("POST", endpoint.getHost(), endpoint, reply);
                yield Result.ok(reply);
            }
            case StreamedLines.Outcome.Failed failed -> {
                lines.cancel();
                exchange.cancel(true);
                final ProviderConfig broken = config.withRequestTimeout(failed.timeout());
                yield Result.err(HttpErrorMapper.map(failed.failure(), broken, null, "POST", endpoint.getPath()));
            }
        };
    }

    private Result<HttpReply> send(ProviderConfig config, String path, @Nullable String body) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        final String requestBody = body == null ? "" : body;
        final String method = body == null ? "GET" : "POST";
        log.debug(
                "HTTP exchange request method={} host={} path={} headerNames={} requestTimeout={} bodyLength={}",
                method,
                config.baseUrl().getHost(),
                path,
                JSON_HEADER_NAMES,
                config.requestTimeout(),
                requestBody.length());
        if (log.isTraceEnabled()) {
            log.trace(
                    "HTTP request body host={} path={} body={}",
                    config.baseUrl().getHost(),
                    path,
                    requestBody);
        }
        final URI endpoint = endpoint(config.baseUrl(), path);
        final String host = endpoint.getHost();
        return execute(config, endpoint, method, host, body);
    }

    private Result<HttpReply> execute(
            ProviderConfig config, URI endpoint, String method, String host, @Nullable String body) {
        try {
            final HttpRequest builtRequest = buildRequest(config, endpoint, body);
            final HttpResponse<String> response = clients.forConnectTimeout(config.connectTimeout())
                    .send(builtRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final HttpReply reply =
                    new HttpReply(response.statusCode(), response.headers().map(), response.body());
            logResponse(method, host, endpoint, reply);
            return Result.ok(reply);
        } catch (InterruptedException failure) {
            return Result.err(HttpErrorMapper.map(failure, config, null, method, endpoint.getPath()));
        } catch (IOException | RuntimeException failure) {
            final AppError error = HttpErrorMapper.map(failure, config, null, method, endpoint.getPath());
            return Result.err(error);
        }
    }

    private HttpRequest buildRequest(ProviderConfig config, URI endpoint, @Nullable String body) {
        log.debug(
                "Building HTTP request method={} host={} path={} requestTimeout={} headerNames={}",
                body == null ? "GET" : "POST",
                endpoint.getHost(),
                endpoint.getPath(),
                config.requestTimeout(),
                JSON_HEADER_NAMES);
        final HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        return body == null
                ? request.GET().build()
                : request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
    }

    private static void logResponse(String method, String host, URI endpoint, HttpReply reply) {
        log.debug(
                "HTTP exchange response method={} host={} path={} status={} headerNames={} bodyLength={}",
                method,
                host,
                endpoint.getPath(),
                reply.status(),
                String.join(",", reply.headers().keySet()),
                reply.body().length());
        if (log.isTraceEnabled()) {
            log.trace("HTTP response body host={} path={} body={}", host, endpoint.getPath(), reply.body());
        }
    }

    private static URI endpoint(URI baseUrl, String path) {
        log.debug("Joining provider endpoint host={} routePath={}", baseUrl.getHost(), path);
        final String basePath =
                baseUrl.getPath() == null ? "" : baseUrl.getPath().replaceFirst("/+$", "");
        final String routePath = path.replaceFirst("^/+", "");
        try {
            return new URI(
                    baseUrl.getScheme(),
                    null,
                    baseUrl.getHost(),
                    baseUrl.getPort(),
                    basePath + "/" + routePath,
                    null,
                    null);
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Provider endpoint path is invalid.", failure);
        }
    }
}
