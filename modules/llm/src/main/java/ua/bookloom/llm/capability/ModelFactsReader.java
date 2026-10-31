package ua.bookloom.llm.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Asks a model's own server what it is: thinking control, template, quant, size and window. Each call is one person's
 * action (selecting a model, the Models tab, Test, Start, Continue) — nothing here runs on its own, and a chat call
 * never triggers it. Every failure is "the server could not say": the answer is {@link ModelFacts#unknown()} or a
 * partly empty one, never an error, because the facts only refine a default (offline-and-privacy: optional, safe). The
 * latest answer is kept in {@link EngineFacts} for the session and never stored.
 */
@Slf4j
public final class ModelFactsReader {

    private static final String OLLAMA_SHOW = "/api/show";
    private static final String OLLAMA_PS = "/api/ps";
    private static final String LM_STUDIO_V0 = "/api/v0/models/";
    private static final String LM_STUDIO_V1 = "/api/v1/models";
    private static final String PROPS = "/props";
    private static final String MODELS = "/models";
    private static final String CONTEXT_SUFFIX = ".context_length";
    private static final String UNKNOWN = "unknown";
    private static final int HTTP_OK = 200;

    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final EngineFacts session;

    /** Shares the clients' exchange, tolerant mapper and the session's engine facts. */
    public ModelFactsReader(final HttpExchange exchange, final ObjectMapper mapper, final EngineFacts session) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Reads what the provider's engine says about a model.
     *
     * @param config the provider; never null
     * @param modelId the model id as the provider lists it; never null
     * @return the facts, with every part the server did not give empty; never null
     */
    public ModelFacts read(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        final ServerEngine engine = config.effectiveEngine();
        log.debug(
                "Model facts read host={} engine={} model={}", config.baseUrl().getHost(), engine, modelId);
        final Draft draft = new Draft();
        switch (engine) {
            case OLLAMA -> ollama(config, modelId, draft);
            case LM_STUDIO -> lmStudio(config, modelId, draft);
            case LLAMA_CPP -> llamaServer(config, modelId, draft);
            case UNSLOTH, OPENROUTER -> listingRow(config, modelId, draft);
            case GENERIC -> {
                lmStudioModel(config, modelId, draft);
                if (draft.contextLength == null) {
                    listingRow(config, modelId, draft);
                }
            }
        }
        final ModelFacts facts = session.withTemplateSwitch(config, modelId, draft.toFacts());
        session.remember(config.id(), modelId, facts);
        logRead(config, engine, facts);
        return facts;
    }

    private void ollama(final ProviderConfig config, final String modelId, final Draft draft) {
        final Optional<ProviderConfig> root = ollamaRoot(config);
        if (root.isEmpty()) {
            return;
        }
        final String request = mapper.createObjectNode().put("model", modelId).toString();
        body(exchange.post(root.get(), OLLAMA_SHOW, request)).ifPresent(show -> readOllamaShow(show, draft));
        body(exchange.get(root.get(), OLLAMA_PS)).ifPresent(ps -> readOllamaPs(ps, modelId, draft));
    }

    private static void readOllamaShow(final JsonNode show, final Draft draft) {
        draft.capabilities = strings(show.path("capabilities"));
        draft.thinkingOptions = strings(show.path("thinking").path("values"));
        draft.thinkingDefault = text(show.path("thinking").path("default"));
        draft.renderer = text(show.path("renderer"));
        draft.parser = text(show.path("parser"));
        final JsonNode details = show.path("details");
        draft.family = text(details.path("family"));
        draft.format = text(details.path("format"));
        draft.quant = text(details.path("quantization_level"));
        draft.parameterSize = text(details.path("parameter_size"));
        draft.runner = text(details.path("runner"));
        final JsonNode info = show.path("model_info");
        draft.architecture = text(info.path("general.architecture"));
        for (final Map.Entry<String, JsonNode> entry : info.properties()) {
            if (entry.getKey().endsWith(CONTEXT_SUFFIX) && positive(entry.getValue())) {
                draft.contextLength = entry.getValue().intValue();
                return;
            }
        }
    }

    private static void readOllamaPs(final JsonNode ps, final String modelId, final Draft draft) {
        for (final JsonNode entry : ps.path("models")) {
            if (modelId.equals(entry.path("name").asText())
                    || modelId.equals(entry.path("model").asText())) {
                draft.loadedContext = positive(entry.path("context_length"))
                        ? Integer.valueOf(entry.path("context_length").intValue())
                        : null;
                final String runner = text(entry.path("runner"));
                draft.runner = runner != null ? runner : draft.runner;
                return;
            }
        }
    }

    private void lmStudio(final ProviderConfig config, final String modelId, final Draft draft) {
        lmStudioModel(config, modelId, draft);
        final Optional<ProviderConfig> root = EndpointPaths.atHostRoot(config);
        root.flatMap(at -> body(exchange.get(at, LM_STUDIO_V1)))
                .ifPresent(list -> readLmStudioReasoning(list, modelId, draft));
    }

    // Asked for LM Studio and for a server of unknown engine, whose answer (or its 404) then decides.
    private void lmStudioModel(final ProviderConfig config, final String modelId, final Draft draft) {
        final Optional<JsonNode> model =
                EndpointPaths.atHostRoot(config).flatMap(root -> body(exchange.get(root, LM_STUDIO_V0 + modelId)));
        model.ifPresent(found -> {
            draft.architecture = text(found.path("arch"));
            draft.type = text(found.path("type"));
            draft.compatibility = text(found.path("compatibility_type"));
            draft.state = text(found.path("state"));
            draft.format = text(found.path("format"));
            draft.quant = text(found.path("quantization"));
            final Integer loaded = positiveOrNull(found.path("loaded_context_length"));
            final Integer max = positiveOrNull(found.path("max_context_length"));
            draft.loadedContext = loaded;
            draft.contextLength = loaded != null ? loaded : max;
        });
    }

    private static void readLmStudioReasoning(final JsonNode list, final String modelId, final Draft draft) {
        final JsonNode rows = list.path("models").isArray() ? list.path("models") : list.path("data");
        for (final JsonNode row : rows) {
            if (modelId.equals(row.path("key").asText())
                    || modelId.equals(row.path("id").asText())) {
                final JsonNode reasoning = row.path("capabilities").path("reasoning");
                draft.thinkingOptions = strings(reasoning.path("allowed_options"));
                draft.thinkingDefault = text(reasoning.path("default"));
                return;
            }
        }
    }

    // A router's own /props describes no model and says n_ctx 0, so the model's own page is asked.
    private void llamaServer(final ProviderConfig config, final String modelId, final Draft draft) {
        final String path = session.isRouter(config) ? PROPS + "?model=" + encode(modelId) : PROPS;
        final Optional<Integer> window = EndpointPaths.atHostRoot(config)
                .flatMap(root -> body(exchange.get(root, path)))
                .map(props -> positiveOrNull(props.path("n_ctx")));
        window.ifPresent(tokens -> {
            draft.contextLength = tokens;
            draft.loadedContext = tokens;
        });
        if (draft.contextLength == null) {
            listingRow(config, modelId, draft);
        }
    }

    // Unsloth and generic servers put what they know on the model's own row of the OpenAI list; llama-server's
    // per-slot window is in the row's meta.
    private void listingRow(final ProviderConfig config, final String modelId, final Draft draft) {
        body(exchange.get(config, EndpointPaths.modelsPath(config, MODELS))).ifPresent(list -> {
            for (final JsonNode row : list.path("data")) {
                if (modelId.equals(row.path("id").asText())) {
                    readListingRow(row, draft);
                    return;
                }
            }
        });
    }

    private static void readListingRow(final JsonNode row, final Draft draft) {
        draft.quant = text(row.path("quant"));
        final Integer slot = positiveOrNull(row.path("meta").path("n_ctx"));
        final Integer window = positiveOrNull(row.path("context_length"));
        final Integer most = positiveOrNull(row.path("max_context_length"));
        final Integer served = positiveOrNull(row.path("max_model_len"));
        final boolean loaded = row.path("loaded").asBoolean(false);
        draft.loadedContext = slot != null ? slot : (loaded ? window : null);
        draft.contextLength = draft.loadedContext != null ? draft.loadedContext : firstOf(most, served, window);
    }

    private static @Nullable Integer firstOf(final @Nullable Integer... values) {
        for (final Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Optional<ProviderConfig> ollamaRoot(final ProviderConfig config) {
        return config.kind() == ProviderKind.OLLAMA ? Optional.of(config) : EndpointPaths.atHostRoot(config);
    }

    private Optional<JsonNode> body(final Result<HttpReply> reply) {
        if (reply.isErr()) {
            log.debug(
                    "Model facts unanswered code={}",
                    Objects.requireNonNull(reply.error(), "error").code());
            return Optional.empty();
        }
        final HttpReply data = Objects.requireNonNull(reply.data(), "reply");
        if (data.status() != HTTP_OK) {
            log.debug("Model facts refused status={}", data.status());
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readTree(data.body())).filter(JsonNode::isObject);
        } catch (JsonProcessingException notJson) {
            log.debug("Model facts reply is not JSON");
            return Optional.empty();
        }
    }

    // Counts and choices only: no model name, no text.
    private static void logRead(final ProviderConfig config, final ServerEngine engine, final ModelFacts facts) {
        log.info(
                "facts read server={} {} engine={} runner={} renderer={} family={} capabilities={} window={}",
                engine.name().toLowerCase(Locale.ROOT),
                config.engineVersion() == null ? UNKNOWN : config.engineVersion(),
                engine,
                facts.runner() == null ? "none" : facts.runner(),
                facts.renderer() == null ? "none" : facts.renderer(),
                facts.family() == null ? "none" : facts.family(),
                facts.capabilities().size(),
                facts.contextLength() == null ? UNKNOWN : facts.contextLength());
    }

    private static String encode(final String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8);
    }

    private static @Nullable String text(final JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static List<String> strings(final JsonNode node) {
        final List<String> values = new ArrayList<>();
        for (final JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static boolean positive(final JsonNode value) {
        return value.canConvertToInt() && value.intValue() > 0;
    }

    private static @Nullable Integer positiveOrNull(final JsonNode value) {
        return positive(value) ? Integer.valueOf(value.intValue()) : null;
    }

    private static final class Draft {
        @Nullable
        String architecture;

        @Nullable
        String family;

        @Nullable
        String format;

        @Nullable
        String quant;

        @Nullable
        String parameterSize;

        List<String> capabilities = List.of();
        List<String> thinkingOptions = List.of();

        @Nullable
        String thinkingDefault;

        @Nullable
        String renderer;

        @Nullable
        String parser;

        @Nullable
        String runner;

        @Nullable
        String type;

        @Nullable
        String state;

        @Nullable
        String compatibility;

        @Nullable
        Integer contextLength;

        @Nullable
        Integer loadedContext;

        ModelFacts toFacts() {
            return new ModelFacts(
                    architecture,
                    family,
                    format,
                    quant,
                    parameterSize,
                    capabilities,
                    thinkingOptions,
                    thinkingDefault,
                    renderer,
                    parser,
                    runner,
                    type,
                    state,
                    compatibility,
                    contextLength,
                    loadedContext);
        }
    }
}
