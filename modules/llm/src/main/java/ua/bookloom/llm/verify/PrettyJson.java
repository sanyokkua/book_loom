package ua.bookloom.llm.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Lays a body out for the Settings test card with Jackson's own pretty printer: a text that is wholly one JSON object
 * or array is indented, anything else (plain words, a fragment, a lone value) is returned exactly as it came, so the
 * model's own words are never changed. The line break is always {@code \n}, whatever the platform.
 */
@Slf4j
final class PrettyJson {

    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    PrettyJson(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.writer = mapper.writer(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));
    }

    /**
     * The text laid out for reading.
     *
     * @param text a body or a reply; never null
     * @return the indented JSON when {@code text} is one JSON object or array and nothing else, else {@code text}
     */
    String of(final String text) {
        Objects.requireNonNull(text, "text");
        try {
            final JsonNode node = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(text);
            final boolean container = node != null && node.isContainerNode();
            log.trace("test body read as json container={} chars={}", container, text.length());
            return container ? writer.writeValueAsString(node) : text;
        } catch (JsonProcessingException notJson) {
            log.trace("test body is not json, shown as it came chars={}", text.length());
            return text;
        }
    }
}
