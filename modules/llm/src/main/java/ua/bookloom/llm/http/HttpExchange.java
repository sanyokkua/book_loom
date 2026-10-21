package ua.bookloom.llm.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;

/** Sends one JSON request per call; the provider client owns response decoding and HTTP-purpose decisions. */
@Slf4j
public final class HttpExchange {

    private final HttpClients clients;
    private final LongSupplier nanoTime;
    private final Function<String, @Nullable String> env;

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
        this(clients, nanoTime, System::getenv);
    }

    /** Also takes the lookup that resolves a credential or header variable, so a test never changes the real environment. */
    public HttpExchange(HttpClients clients, LongSupplier nanoTime, Function<String, @Nullable String> env) {
        this.clients = Objects.requireNonNull(clients, "clients");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.env = Objects.requireNonNull(env, "env");
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
     * @param maxLines the most lines read before the reply is cut as a runaway and returned marked
     *     {@link HttpReply#cut()}, the connection closed so the provider stops generating; {@link Long#MAX_VALUE} for
     *     no bound
     * @param counted whether a line counts against {@code maxLines}, so reasoning text can be left out of the bound
     * @return the status, headers and every line of the body, each followed by a newline; or a {@code timeout} error
     *     for a reply that stalled or ran past its limit, {@code cancelled} for an interrupted caller, and the transport
     *     failure's own code otherwise
     */
    public Result<HttpReply> postStreamed(
            ProviderConfig config, String path, String body, Duration idle, long maxLines, Predicate<String> counted) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(idle, "idle");
        log.debug(
                "HTTP streamed exchange request host={} path={} limit={} idle={} maxLines={} bodyLength={}",
                config.baseUrl().getHost(),
                path,
                config.requestTimeout(),
                idle,
                maxLines,
                body.length());
        traceBody(config, path, body);
        final Result<Map<String, String>> headers = RequestHeaders.resolve(config, env);
        if (headers.isErr()) {
            return Result.err(Objects.requireNonNull(headers.error(), "error"));
        }
        final URI endpoint = endpoint(config.baseUrl(), path);
        try {
            return streamed(
                    config, endpoint, body, idle, maxLines, counted, Objects.requireNonNull(headers.data(), "headers"));
        } catch (RuntimeException failure) {
            return Result.err(HttpErrorMapper.map(failure, config, null, "POST", endpoint.getPath()));
        }
    }

    private Result<HttpReply> streamed(
            ProviderConfig config,
            URI endpoint,
            String body,
            Duration idle,
            long maxLines,
            Predicate<String> counted,
            Map<String, String> headers) {
        final StreamedLines lines = new StreamedLines();
        final AtomicReference<HttpResponse.@Nullable ResponseInfo> head = new AtomicReference<>();
        final long started = nanoTime.getAsLong();
        final CompletableFuture<HttpResponse<Void>> exchange = clients.forConnectTimeout(config.connectTimeout())
                .sendAsync(buildRequest(config, endpoint, body, headers), info -> {
                    head.set(info);
                    return HttpResponse.BodySubscribers.fromLineSubscriber(lines);
                });
        // The stage only forwards a failure to the reader; the reader, not this stage, reports the outcome.
        var _ = exchange.whenComplete((_, failure) -> {
            if (failure != null) {
                lines.exchangeFailed(failure);
            }
        });
        final StreamedLines.Outcome outcome =
                lines.collect(idle, config.requestTimeout(), started, nanoTime, maxLines, counted);
        return replyOf(outcome, config, endpoint, lines, exchange, head);
    }

    private static Result<HttpReply> replyOf(
            StreamedLines.Outcome outcome,
            ProviderConfig config,
            URI endpoint,
            StreamedLines lines,
            CompletableFuture<HttpResponse<Void>> exchange,
            AtomicReference<HttpResponse.@Nullable ResponseInfo> head) {
        return switch (outcome) {
            case StreamedLines.Outcome.Collected collected ->
                Result.ok(received(head, endpoint, collected.body(), false));
            case StreamedLines.Outcome.Cut cut -> {
                lines.cancel();
                exchange.cancel(true);
                yield Result.ok(received(head, endpoint, cut.body(), true));
            }
            case StreamedLines.Outcome.Failed failed -> {
                lines.cancel();
                exchange.cancel(true);
                final ProviderConfig broken = config.withRequestTimeout(failed.timeout());
                yield Result.err(HttpErrorMapper.map(failed.failure(), broken, null, "POST", endpoint.getPath()));
            }
        };
    }

    private static HttpReply received(
            AtomicReference<HttpResponse.@Nullable ResponseInfo> head, URI endpoint, String body, boolean cut) {
        final HttpResponse.ResponseInfo info = Objects.requireNonNull(head.get(), "response head");
        final HttpReply reply = new HttpReply(info.statusCode(), info.headers().map(), body, cut);
        logResponse("POST", endpoint.getHost(), endpoint, reply);
        return reply;
    }

    private Result<HttpReply> send(ProviderConfig config, String path, @Nullable String body) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(path, "path");
        final String requestBody = body == null ? "" : body;
        final String method = body == null ? "GET" : "POST";
        log.debug(
                "HTTP exchange request method={} host={} path={} requestTimeout={} bodyLength={}",
                method,
                config.baseUrl().getHost(),
                path,
                config.requestTimeout(),
                requestBody.length());
        traceBody(config, path, requestBody);
        final Result<Map<String, String>> headers = RequestHeaders.resolve(config, env);
        if (headers.isErr()) {
            return Result.err(Objects.requireNonNull(headers.error(), "error"));
        }
        final URI endpoint = endpoint(config.baseUrl(), path);
        return execute(config, endpoint, method, body, Objects.requireNonNull(headers.data(), "headers"));
    }

    private Result<HttpReply> execute(
            ProviderConfig config, URI endpoint, String method, @Nullable String body, Map<String, String> headers) {
        final String host = endpoint.getHost();
        try {
            final HttpRequest builtRequest = buildRequest(config, endpoint, body, headers);
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

    private HttpRequest buildRequest(
            ProviderConfig config, URI endpoint, @Nullable String body, Map<String, String> headers) {
        log.debug(
                "Building HTTP request method={} host={} path={} requestTimeout={} headerNames={}",
                body == null ? "GET" : "POST",
                endpoint.getHost(),
                endpoint.getPath(),
                config.requestTimeout(),
                headers.keySet());
        final HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(config.requestTimeout());
        headers.forEach(request::header);
        return body == null
                ? request.GET().build()
                : request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
    }

    private static void traceBody(ProviderConfig config, String path, String body) {
        if (log.isTraceEnabled()) {
            log.trace(
                    "HTTP request body host={} path={} body={}",
                    config.baseUrl().getHost(),
                    path,
                    body);
        }
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
