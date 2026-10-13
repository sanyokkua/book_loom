package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The term reading's schema (15i.N4): a closed answer per window — the quote first, then the usage, the kind and the
 * gender from closed lists — at most six windows a call, and the term's two flags after its windows.
 */
class TermReadingSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode schema() throws Exception {
        return MAPPER.readTree(TermReadingSchema.SCHEMA);
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
    void schema_topLevel_isTheWindowsThenTheTwoFlagsAndNothingElse() throws Exception {
        assertThat(fields(schema().path("properties"))).containsExactly("windows", "speaking", "brand");
        assertThat(texts(schema().path("required"))).containsExactly("windows", "speaking", "brand");
        assertThat(schema().path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema().path("properties").path("speaking").path("type").asText())
                .isEqualTo("boolean");
        assertThat(schema().path("properties").path("brand").path("type").asText())
                .isEqualTo("boolean");
    }

    // IF the labels came first, THEN the model would label first and look for a quote that fits.
    @Test
    void schema_window_putsTheQuoteBeforeItsLabels() throws Exception {
        assertThat(fields(item().path("properties"))).containsExactly("window", "quote", "usage", "kind", "gender");
        assertThat(texts(item().path("required"))).containsExactly("window", "quote", "usage", "kind", "gender");
        assertThat(item().path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test
    void schema_labels_areClosedLists() throws Exception {
        final JsonNode properties = item().path("properties");

        assertThat(texts(properties.path("usage").path("enum")))
                .containsExactly("name", "word", "part-of-name", "title");
        assertThat(texts(properties.path("kind").path("enum")))
                .containsExactly("person", "place", "organisation", "thing", "other");
        assertThat(texts(properties.path("gender").path("enum"))).containsExactly("male", "female", "none");
    }

    // A call shows at most six windows (Max reads twelve in two calls), and a quote is a short stretch, so a looping
    // reply cannot run on.
    @Test
    void schema_sizes_capTheWindowsAndTheQuote() throws Exception {
        assertThat(schema().path("properties").path("windows").path("maxItems").asInt())
                .isEqualTo(6);
        assertThat(item().path("properties").path("quote").path("maxLength").asInt())
                .isEqualTo(200);
    }

    @Test
    void promptName_termReading_sendsThisSchema() {
        assertThat(PromptName.TERM_READING.responseSchema()).isEqualTo(TermReadingSchema.SCHEMA);
    }
}
