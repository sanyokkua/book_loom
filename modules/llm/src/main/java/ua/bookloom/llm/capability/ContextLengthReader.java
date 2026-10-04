package ua.bookloom.llm.capability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads a model's context length out of a provider's model-detail reply. Tolerant by design: any missing, malformed
 * or non-positive figure is "not reported", never a failure, because the figure only refines a default.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextLengthReader {

    private static final String OLLAMA_MODEL_INFO = "model_info";
    private static final String OLLAMA_SUFFIX = ".context_length";
    private static final String LM_STUDIO_LOADED = "loaded_context_length";
    private static final String LM_STUDIO_MAX = "max_context_length";

    /**
     * Reads Ollama's {@code /api/show} reply: the architecture-prefixed {@code <arch>.context_length} entry of
     * {@code model_info}.
     *
     * @param body the reply body; never null
     * @param mapper the shared tolerant mapper; never null
     * @return the trained context length, or empty when the reply names none
     */
    public static Optional<Integer> fromOllamaShow(final String body, final ObjectMapper mapper) {
        final Optional<JsonNode> root = parse(body, mapper);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        for (final Map.Entry<String, JsonNode> entry :
                root.get().path(OLLAMA_MODEL_INFO).properties()) {
            if (entry.getKey().endsWith(OLLAMA_SUFFIX) && positive(entry.getValue())) {
                log.debug(
                        "Ollama context length key={} tokens={}",
                        entry.getKey(),
                        entry.getValue().intValue());
                return Optional.of(entry.getValue().intValue());
            }
        }
        log.debug("Ollama show reply names no context length");
        return Optional.empty();
    }

    /**
     * Reads LM Studio's {@code /api/v0/models/<id>} reply: the length the model is loaded with when it is loaded, else
     * the most it supports.
     *
     * @param body the reply body; never null
     * @param mapper the shared tolerant mapper; never null
     * @return the context length, or empty when the reply names none
     */
    public static Optional<Integer> fromLmStudioModel(final String body, final ObjectMapper mapper) {
        final Optional<JsonNode> root = parse(body, mapper);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        for (final String key : new String[] {LM_STUDIO_LOADED, LM_STUDIO_MAX}) {
            final JsonNode value = root.get().path(key);
            if (positive(value)) {
                log.debug("LM Studio context length key={} tokens={}", key, value.intValue());
                return Optional.of(value.intValue());
            }
        }
        log.debug("LM Studio model reply names no context length");
        return Optional.empty();
    }

    private static boolean positive(final JsonNode value) {
        return value.canConvertToInt() && value.intValue() > 0;
    }

    private static Optional<JsonNode> parse(final String body, final ObjectMapper mapper) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(mapper, "mapper");
        try {
            return Optional.ofNullable(mapper.readTree(body));
        } catch (JsonProcessingException failure) {
            log.debug("Model detail reply is not JSON; no context length read");
            return Optional.empty();
        }
    }
}
