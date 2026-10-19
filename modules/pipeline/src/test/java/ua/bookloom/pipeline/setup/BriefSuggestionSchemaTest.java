package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The brief window's reply schema: each field is a short chain of thought, the quote first and the label after it. */
class BriefSuggestionSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<String> namesIn(final JsonNode object) {
        final List<String> names = new ArrayList<>();
        final Iterator<String> iterator = object.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }

    private static List<String> required(final JsonNode field) {
        final List<String> names = new ArrayList<>();
        field.get("required").forEach(name -> names.add(name.asText()));
        return names;
    }

    // IF the label came first, THEN the model would pick it before it had looked for the words that show it.
    @ParameterizedTest
    @ValueSource(strings = {"genre", "register", "voice", "audience", "narrator", "narratorGender"})
    void schema_field_listsItsEvidenceBeforeItsValue(final String field) throws Exception {
        final JsonNode node =
                MAPPER.readTree(BriefSuggestionSchema.SCHEMA).get("properties").get(field);

        assertThat(namesIn(node.get("properties"))).containsSubsequence("evidence", "value", "confidence");
        assertThat(required(node)).containsSubsequence("evidence", "value", "confidence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"genre", "register", "voice", "audience", "narrator", "narratorGender"})
    void schema_field_stillRequiresAllThreeAnswersAndNothingElse(final String field) throws Exception {
        final JsonNode node =
                MAPPER.readTree(BriefSuggestionSchema.SCHEMA).get("properties").get(field);

        assertThat(node.get("additionalProperties").asBoolean()).isFalse();
        assertThat(required(node)).contains("evidence", "value", "confidence");
    }

    // The narrator's gender names the person its quote calls the narrator by; the code genders that name itself.
    @Test
    void schema_narratorGender_keepsTheNameThatTheCodeGenders() throws Exception {
        final JsonNode node =
                MAPPER.readTree(BriefSuggestionSchema.SCHEMA).get("properties").get("narratorGender");

        assertThat(namesIn(node.get("properties"))).contains("name");
        assertThat(required(node)).contains("name");
    }
}
