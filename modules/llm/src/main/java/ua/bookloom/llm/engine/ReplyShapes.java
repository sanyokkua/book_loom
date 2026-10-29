package ua.bookloom.llm.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpReply;

/**
 * The body shapes each engine sends. A reply counts only by what its body holds: LM Studio answers every unknown path
 * with HTTP 200 and an error body, and KoboldCpp and LocalAI copy Ollama's routes.
 */
@Slf4j
final class ReplyShapes {

    private static final Pattern OLLAMA_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?");
    private static final Pattern BUILD_INFO = Pattern.compile("^(b\\d+)-");
    private static final String LLAMA_SERVER_HEADER = "llama.cpp";
    private static final String LOADING_MODEL = "loading model";
    private static final String UNSLOTH_SERVICE = "Unsloth UI Backend";
    private static final String OWNED_BY = "owned_by";
    private static final List<String> OPENROUTER_FIELDS = List.of("canonical_slug", "supported_parameters", "pricing");
    private static final Map<String, Found> OWNERS = Map.of(
            "library", Found.of(ServerEngine.OLLAMA),
            "organization_owner", Found.of(ServerEngine.LM_STUDIO),
            "unsloth-studio", Found.of(ServerEngine.UNSLOTH),
            "humanity", new Found(ServerEngine.GENERIC, null, false, Suitability.NOT_SUITABLE_STRUCTURED));

    private final ObjectMapper mapper;

    ReplyShapes(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** The reply's body as a JSON object, or empty for no reply, a body that is not JSON, or a JSON non-object. */
    Optional<JsonNode> object(final Optional<HttpReply> reply) {
        return reply.flatMap(this::parsed).filter(JsonNode::isObject);
    }

    /** The version of an Ollama {@code {"version":"x.y.z"}} body, or null for any other body. */
    @Nullable
    String ollamaVersion(final JsonNode body) {
        final JsonNode version = body.path("version");
        return !body.has("error")
                        && version.isTextual()
                        && OLLAMA_VERSION.matcher(version.asText()).matches()
                ? version.asText()
                : null;
    }

    /** Whether the body is Ollama's {@code {"models":[…]}} list with no row carrying another server's marker. */
    boolean isOllamaTags(final JsonNode body) {
        final JsonNode models = body.path("models");
        return models.isArray() && rows(models).noneMatch(ReplyShapes::hasFakeMarker);
    }

    /** Whether every row of an OpenAI model list has {@code owned_by}; a reply with no rows keeps the Ollama hit. */
    boolean isEveryRowOwned(final Optional<JsonNode> body) {
        final JsonNode data = body.map(node -> node.path("data")).orElse(mapper.missingNode());
        return !data.isArray() || rows(data).allMatch(row -> row.hasNonNull(OWNED_BY));
    }

    /** Whether the reply carries the {@code Server: llama.cpp} header, under any letter case of its name. */
    static boolean hasLlamaServerHeader(final HttpReply reply) {
        return reply.headers().entrySet().stream()
                .filter(header -> "server".equalsIgnoreCase(header.getKey()))
                .flatMap(header -> header.getValue().stream())
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).startsWith(LLAMA_SERVER_HEADER));
    }

    /** Whether a health body is {@code {"status":"ok"}}. */
    static boolean isStatusOk(final JsonNode body) {
        return "ok".equals(body.path("status").asText(null));
    }

    /** Whether a health body is llama-server's {@code Loading model} error, sent while it loads. */
    static boolean isLoadingModel(final JsonNode body) {
        return body.path("error")
                .path("message")
                .asText("")
                .toLowerCase(Locale.ROOT)
                .contains(LOADING_MODEL);
    }

    /** The {@code bNNNN} build of llama-server's {@code /props} {@code build_info}, or null when it has none. */
    static @Nullable String build(final JsonNode props) {
        final Matcher build = BUILD_INFO.matcher(props.path("build_info").asText(""));
        return build.find() ? build.group(1) : null;
    }

    /** Whether llama-server's {@code /props} says it routes by model id. */
    static boolean isRouter(final JsonNode props) {
        return "router".equals(props.path("role").asText(null));
    }

    /** Whether the body is Unsloth Studio's health reply. */
    static boolean isUnslothHealth(final JsonNode body) {
        return "healthy".equals(body.path("status").asText(null))
                && UNSLOTH_SERVICE.equals(body.path("service").asText(null));
    }

    /** Whether the body is LM Studio's {@code /api/v0/models} list, whose rows say {@code compatibility_type}. */
    static boolean isLmStudioModels(final JsonNode body) {
        final JsonNode data = body.path("data");
        return data.isArray() && rows(data).anyMatch(row -> row.has("compatibility_type"));
    }

    /**
     * What an OpenAI model list says about the server: OpenRouter's own row fields, else the first {@code owned_by}
     * value that names an engine, else a generic server.
     */
    Found byModelRows(final JsonNode body) {
        final JsonNode data = body.path("data");
        final List<JsonNode> rows = data.isArray() ? rows(data).toList() : List.of();
        if (rows.stream().anyMatch(row -> OPENROUTER_FIELDS.stream().anyMatch(row::has))) {
            return Found.of(ServerEngine.OPENROUTER);
        }
        final List<String> owners = rows.stream()
                .map(row -> row.path(OWNED_BY).asText(""))
                .distinct()
                .toList();
        log.debug("Engine model list owners={} rows={}", owners, rows.size());
        return owners.stream()
                .map(OWNERS::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> Found.of(ServerEngine.GENERIC));
    }

    /** The engine an {@code owned_by} value names, or null for a value that names none or a generic server. */
    static @Nullable ServerEngine ownerEngine(final @Nullable String owner) {
        final Found found = owner == null ? null : OWNERS.get(owner);
        return found == null || found.engine() == ServerEngine.GENERIC ? null : found.engine();
    }

    private Optional<JsonNode> parsed(final HttpReply reply) {
        try {
            return Optional.ofNullable(mapper.readTree(reply.body()));
        } catch (JsonProcessingException failure) {
            log.debug(
                    "Engine probe body is not JSON status={} length={}",
                    reply.status(),
                    reply.body().length());
            return Optional.empty();
        }
    }

    private static boolean hasFakeMarker(final JsonNode row) {
        final JsonNode details = row.path("details");
        return "koboldcpp".equalsIgnoreCase(details.path("family").asText(""))
                || "sglang".equalsIgnoreCase(details.path("format").asText(""))
                || row.path("digest").asText("").startsWith("sha256:sglang");
    }

    private static Stream<JsonNode> rows(final JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
