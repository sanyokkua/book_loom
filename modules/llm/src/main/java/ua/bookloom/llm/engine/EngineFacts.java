package ua.bookloom.llm.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;

/**
 * What a server says about the model it serves, kept for the session: today, whether llama-server's chat template reads
 * the {@code enable_thinking} variable, which decides whether the off switch can be sent at all. A router reads it per
 * model id from {@code /props?model=<id>}. It is read by one GET of {@code /props} on the first call that needs it, so
 * a server named by "Treat this server as..." works without a Test, and a Test forgets it so the next call reads afresh.
 * An unreadable {@code /props} means no kwarg; a reply that is not answered at all, or not answered with HTTP 200 (a
 * llama-server still loading its model answers 503 on every route), is not kept, so a later call asks again. The Ollama runner and LM Studio's reasoning options join it when a request field depends on them.
 */
@Slf4j
@Singleton
public final class EngineFacts {

    private static final Pattern READS_ENABLE_THINKING = Pattern.compile("\\benable_thinking\\b");
    private static final String PROPS = "props";
    private static final char KEY_SEPARATOR = '\u0000';
    private static final int HTTP_OK = 200;
    private static final List<String> TEMPLATE_SWITCH = List.of("true", "false");

    private final HttpExchange exchange;
    private final ReplyShapes shapes;
    private final Map<String, Props> props = new ConcurrentHashMap<>();
    private final Map<String, ModelFacts> modelFacts = new ConcurrentHashMap<>();

    /** Shares the provider exchange, so the GET signs in and is logged like every other request. */
    @Inject
    public EngineFacts(final HttpExchange exchange, final ObjectMapper mapper) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.shapes = new ReplyShapes(Objects.requireNonNull(mapper, "mapper"));
    }

    /**
     * Whether the chat template that answers this model reads {@code enable_thinking}.
     *
     * @param config the llama-server provider; non-null
     * @param modelId the model the call is for; non-null
     * @return {@code true} if the template reads the variable, {@code false} if it does not or cannot be read
     */
    public boolean readsEnableThinking(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        final String serverKey = config.id() + KEY_SEPARATOR + config.baseUrl();
        final Props server = known(config, serverKey, PROPS);
        log.debug("Engine facts template check provider={} model={} router={}", config.id(), modelId, server.router());
        if (!server.router()) {
            return server.readsEnableThinking();
        }
        return known(config, serverKey + KEY_SEPARATOR + modelId, PROPS + "?model=" + modelId)
                .readsEnableThinking();
    }

    /**
     * Adds the template's thinking switch to what a server said about a model: on an engine whose off switch is the
     * template variable, a template that reads {@code enable_thinking} declares an on/off switch even when the server
     * said nothing else about thinking, and one that does not read it declares none.
     *
     * @param config the provider; non-null
     * @param modelId the model; non-null
     * @param facts what the server said; non-null
     * @return the facts with the values {@code true} and {@code false} offered when the template reads the variable,
     *     else the facts unchanged
     */
    public ModelFacts withTemplateSwitch(final ProviderConfig config, final String modelId, final ModelFacts facts) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(facts, "facts");
        final boolean templateEngine = EngineTraits.of(config.effectiveEngine(), config.engineVersion())
                        .offSwitch()
                == EngineTraits.OffSwitch.TEMPLATE_KWARG;
        if (!templateEngine || !facts.thinkingOptions().isEmpty()) {
            log.debug(
                    "Template switch not added provider={} model={} templateEngine={}",
                    config.id(),
                    modelId,
                    templateEngine);
            return facts;
        }
        final boolean reads = readsEnableThinking(config, modelId);
        log.debug("Template switch provider={} model={} readsEnableThinking={}", config.id(), modelId, reads);
        return reads ? facts.withThinkingOptions(TEMPLATE_SWITCH) : facts;
    }

    /**
     * Whether a llama-server is a router, which loads whichever listed model an id names, so the id is checked
     * against its list first. Read from the same {@code /props} reply the template check reads.
     *
     * @param config the llama-server provider; non-null
     * @return {@code true} if its {@code /props} says {@code "role":"router"}, {@code false} otherwise
     */
    public boolean isRouter(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final boolean router = known(config, config.id() + KEY_SEPARATOR + config.baseUrl(), PROPS)
                .router();
        log.debug("Engine facts router check provider={} router={}", config.id(), router);
        return router;
    }

    /**
     * Forgets what was read about a provider, so its next call reads the server again.
     *
     * @param providerId the provider's id; non-null
     */
    public void forget(final String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        final String prefix = providerId + KEY_SEPARATOR;
        final boolean removed = props.keySet().removeIf(key -> key.startsWith(prefix));
        modelFacts.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Engine facts forgotten provider={} removed={}", providerId, removed);
    }

    /**
     * Keeps what a server last said about a model, for the session only; the next person's action reads again.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model id; non-null
     * @param facts what the server said; non-null
     */
    public void remember(final String providerId, final String modelId, final ModelFacts facts) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(facts, "facts");
        modelFacts.put(providerId + KEY_SEPARATOR + modelId, facts);
    }

    /**
     * What a server last said about a model in this session.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model id; non-null
     * @return the facts, or empty when none were read this session
     */
    public Optional<ModelFacts> lastRead(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        return Optional.ofNullable(modelFacts.get(providerId + KEY_SEPARATOR + modelId));
    }

    private Props known(final ProviderConfig config, final String key, final String path) {
        final Props kept = props.get(key);
        if (kept != null) {
            return kept;
        }
        final Optional<Props> read = read(config, path);
        read.ifPresent(found -> props.putIfAbsent(key, found));
        return read.orElse(Props.NONE);
    }

    // Empty when the server did not answer at all or not with 200; a 200 that is not llama-server's props reads as
    // "no kwarg" and is kept.
    private Optional<Props> read(final ProviderConfig config, final String path) {
        final Optional<ProviderConfig> root = BaseUrlRoot.of(config).map(BaseUrlRoot::root);
        if (root.isEmpty()) {
            log.debug("Engine facts not read: no usable root provider={}", config.id());
            return Optional.empty();
        }
        final Result<HttpReply> reply = exchange.get(root.orElseThrow(), path);
        if (reply.isErr()) {
            log.debug(
                    "Engine facts not read provider={} code={}",
                    config.id(),
                    Objects.requireNonNull(reply.error(), "error").code());
            return Optional.empty();
        }
        final HttpReply answered = Objects.requireNonNull(reply.data(), "reply");
        if (answered.status() != HTTP_OK) {
            log.debug("Engine facts not kept provider={} status={}", config.id(), answered.status());
            return Optional.empty();
        }
        final Props found =
                shapes.object(Optional.of(answered)).map(EngineFacts::props).orElse(Props.NONE);
        log.info(
                "props read engine={} runner=none templateReads={} router={} perModel={}",
                ServerEngine.LLAMA_CPP,
                found.readsEnableThinking() ? "enable_thinking" : "none",
                found.router(),
                path.contains("?"));
        return Optional.of(found);
    }

    private static Props props(final JsonNode body) {
        final String template = body.path("chat_template").asText("");
        return new Props(
                ReplyShapes.isRouter(body),
                READS_ENABLE_THINKING.matcher(template).find());
    }

    // A router's own /props describes no model, so what its template reads is asked per model id.
    private record Props(boolean router, boolean readsEnableThinking) {
        static final Props NONE = new Props(false, false);
    }
}
