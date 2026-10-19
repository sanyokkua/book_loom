package ua.bookloom.llm.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;

/**
 * Asks a provider's model-detail endpoint for a model's context length. Every failure — transport, status, shape —
 * is "no answer": detection refines a default and must never stop a run (offline-and-privacy: optional, safe).
 */
@Slf4j
public final class ContextLengthProbe {

    private static final String OLLAMA_SHOW_PATH = "/api/show";
    private static final String LM_STUDIO_MODELS_PATH = "/api/v0/models/";
    private static final int HTTP_OK = 200;

    private final HttpExchange exchange;
    private final ObjectMapper mapper;

    /** Shares the clients' exchange and tolerant mapper. */
    public ContextLengthProbe(final HttpExchange exchange, final ObjectMapper mapper) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Asks Ollama's {@code /api/show}.
     *
     * @param config the Ollama provider; never null
     * @param modelId the model name; never null
     * @return the context length, or empty when the provider could not say
     */
    public Optional<Integer> ollama(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        log.debug(
                "Ollama context length probe host={} model={}", config.baseUrl().getHost(), modelId);
        final String body = mapper.createObjectNode().put("model", modelId).toString();
        return read(exchange.post(config, OLLAMA_SHOW_PATH, body), ContextLengthReader::fromOllamaShow);
    }

    /**
     * Asks LM Studio's native {@code /api/v0/models/<id>}, which sits at the host root beside the OpenAI-compatible
     * {@code /v1} base and so is addressed without the configured base path.
     *
     * @param config the OpenAI-compatible provider; never null
     * @param modelId the model id; never null
     * @return the context length, or empty when the server is not LM Studio or could not say
     */
    public Optional<Integer> lmStudio(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        log.debug(
                "LM Studio context length probe host={} model={}",
                config.baseUrl().getHost(),
                modelId);
        final Optional<ProviderConfig> rooted = atHostRoot(config);
        if (rooted.isEmpty()) {
            return Optional.empty();
        }
        return read(
                exchange.get(rooted.get(), LM_STUDIO_MODELS_PATH + modelId), ContextLengthReader::fromLmStudioModel);
    }

    private Optional<Integer> read(final Result<HttpReply> reply, final Reader reader) {
        if (reply.isErr()) {
            log.debug(
                    "Context length probe unanswered code={}",
                    Objects.requireNonNull(reply.error(), "error").code());
            return Optional.empty();
        }
        final HttpReply data = Objects.requireNonNull(reply.data(), "reply");
        if (data.status() != HTTP_OK) {
            log.debug("Context length probe refused status={}", data.status());
            return Optional.empty();
        }
        return reader.read(data.body(), mapper);
    }

    private static Optional<ProviderConfig> atHostRoot(final ProviderConfig config) {
        final URI base = config.baseUrl();
        try {
            return Optional.of(config.withBaseUrl(
                    new URI(base.getScheme(), null, base.getHost(), base.getPort(), "", null, null)));
        } catch (URISyntaxException failure) {
            log.debug("Provider base URL has no usable host root; no LM Studio probe");
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface Reader {
        Optional<Integer> read(String body, ObjectMapper mapper);
    }
}
