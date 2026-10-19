package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The recurring-term reading's schema (15i.N7): per window the quote first, then its usage from a closed list; per
 * term the number of senses the windows show and the one rendering that fits every window.
 */
class LexiconReadingSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode schema() throws Exception {
        return MAPPER.readTree(LexiconReadingSchema.SCHEMA);
    }

    private static JsonNode item() throws Exception {
        return schema().path("properties").path("windows").path("items");
    }

    private static List<String> texts(final JsonNode array) {
        final List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<String> fields(final JsonNode properties) {
        final List<String> names = new ArrayList<>();
        properties.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void schema_topLevel_isTheWindowsThenTheSensesThenTheRenderingAndNothingElse() throws Exception {
        assertThat(fields(schema().path("properties"))).containsExactly("windows", "senses", "rendering");
        assertThat(texts(schema().path("required"))).containsExactly("windows", "senses", "rendering");
        assertThat(schema().path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema().path("properties").path("senses").path("type").asText())
                .isEqualTo("integer");
        assertThat(schema().path("properties").path("rendering").path("type").asText())
                .isEqualTo("string");
    }

    // IF the usage came first, THEN the model would label the window first and look for a quote that fits.
    @Test
    void schema_window_putsTheQuoteBeforeItsUsage() throws Exception {
        assertThat(fields(item().path("properties"))).containsExactly("window", "quote", "usage");
        assertThat(texts(item().path("required"))).containsExactly("window", "quote", "usage");
        assertThat(item().path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void schema_usage_isAClosedListOfTermWordAndName() throws Exception {
        assertThat(texts(item().path("properties").path("usage").path("enum"))).containsExactly("term", "word", "name");
    }

    // A looping reply cannot run on: at most four windows (Max) and a short quote.
    @Test
    void schema_sizes_capTheWindowsAndTheQuote() throws Exception {
        assertThat(schema().path("properties").path("windows").path("maxItems").asInt())
                .isEqualTo(4);
        assertThat(item().path("properties").path("quote").path("maxLength").asInt())
                .isEqualTo(200);
    }

    @Test
    void promptName_lexiconReading_sendsThisSchema() {
        assertThat(PromptName.LEXICON_READING.responseSchema()).isEqualTo(LexiconReadingSchema.SCHEMA);
    }
}
