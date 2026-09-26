package ua.bookloom.llm.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;

/** Holds reusable JDK clients without allowing one provider's connect timeout to change another's. */
@Slf4j
public final class HttpClients {

    // Local Ollama and LM Studio speak HTTP/1.1; the JDK's default h2c upgrade offer on plain http:// can stall a first
    // request.
    private static final HttpClient.Version PROTOCOL_VERSION = HttpClient.Version.HTTP_1_1;

    private final ConcurrentMap<Duration, HttpClient> clients = new ConcurrentHashMap<>();

    /** Returns the shared client for this exact connect timeout, building it once under concurrent access. */
    public HttpClient forConnectTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        log.debug("HTTP client lookup connectTimeout={}", timeout);
        final HttpClient existing = clients.get(timeout);
        if (existing != null) {
            log.debug("Reusing HTTP client connectTimeout={}", timeout);
            return existing;
        }
        return clients.computeIfAbsent(timeout, this::newClient);
    }

    private HttpClient newClient(Duration timeout) {
        log.debug("Creating HTTP client connectTimeout={} version={}", timeout, PROTOCOL_VERSION);
        return HttpClient.newBuilder()
                .version(PROTOCOL_VERSION)
                .connectTimeout(timeout)
                .build();
    }
}
