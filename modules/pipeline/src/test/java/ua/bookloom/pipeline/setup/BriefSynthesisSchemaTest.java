package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The strict schema the brief's synthesis answer is held to (15i.B4): genre, audience and voice, each with its basis
 * before its value, so the model names what it stands on before it decides; the genre one of the forty names and the
 * audience one of five words, so a structured-output server cannot return anything else; and the story shape with its
 * quotes before its value (design D6).
 */
class BriefSynthesisSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode schema() throws java.io.IOException {
        return MAPPER.readTree(BriefSynthesisSchema.SCHEMA);
    }

    private static List<String> texts(final JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static List<String> names(final JsonNode object) {
        final List<String> names = new java.util.ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void schema_isAnObjectOfTheSixFieldsAllRequiredAndNothingMore() throws java.io.IOException {
        final JsonNode schema = schema();

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(names(schema.path("properties")))
                .containsExactly("genre", "audience", "voice", "register", "narrator", "storyShape");
        assertThat(texts(schema.path("required")))
                .containsExactlyInAnyOrder("genre", "audience", "voice", "register", "narrator", "storyShape");
    }

    // The model always gives its most probable narrator gender, so the schema offers no empty or unknown gender.
    @Test
    void schema_narrator_isAGenderOfTwoWithItsQuoteAndName() throws java.io.IOException {
        final JsonNode narrator = schema().path("properties").path("narrator");

        assertThat(names(narrator.path("properties"))).containsExactly("gender", "quote", "name");
        assertThat(texts(narrator.path("properties").path("gender").path("enum")))
                .containsExactly("male", "female");
    }

    // IF the value came first, THEN the model would pick a shape and then look for two passages to fit it.
    @Test
    void schema_storyShape_holdsItsQuotesWithTheirPassageBeforeOneOfTwoValues() throws java.io.IOException {
        final JsonNode shape = schema().path("properties").path("storyShape");
        final JsonNode quote = shape.path("properties").path("quotes").path("items");

        assertThat(names(shape.path("properties"))).containsExactly("quotes", "value");
        assertThat(texts(shape.path("required"))).containsExactlyInAnyOrder("quotes", "value");
        assertThat(shape.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(texts(shape.path("properties").path("value").path("enum")))
                .containsExactly("one story", "separate stories");
        assertThat(names(quote.path("properties"))).containsExactly("passage", "quote");
        assertThat(quote.path("properties").path("passage").path("type").asText())
                .isEqualTo("integer");
        assertThat(texts(quote.path("required"))).containsExactlyInAnyOrder("passage", "quote");
    }

    // IF the value came first, THEN the model would pick a label and then look for a reason to cite.
    @ParameterizedTest
    @ValueSource(strings = {"genre", "audience", "voice", "register"})
    void schema_eachField_holdsItsBasisBeforeItsValue(final String field) throws java.io.IOException {
        final JsonNode object = schema().path("properties").path(field);

        assertThat(names(object.path("properties"))).containsExactly("basis", "value");
        assertThat(texts(object.path("required"))).containsExactlyInAnyOrder("basis", "value");
        assertThat(object.path("additionalProperties").asBoolean(true)).isFalse();
    }

    // The basis is what the code checks: whether the field stands on what the model knows, and which passages.
    @ParameterizedTest
    @ValueSource(strings = {"genre", "audience", "voice", "register"})
    void schema_eachBasis_isTheKnowledgeFlagAndTheCitedPassageNumbers(final String field) throws java.io.IOException {
        final JsonNode basis =
                schema().path("properties").path(field).path("properties").path("basis");

        assertThat(names(basis.path("properties"))).containsExactlyInAnyOrder("knowledge", "passages");
        assertThat(basis.path("properties").path("knowledge").path("type").asText())
                .isEqualTo("boolean");
        assertThat(basis.path("properties").path("passages").path("type").asText())
                .isEqualTo("array");
        assertThat(basis.path("properties")
                        .path("passages")
                        .path("items")
                        .path("type")
                        .asText())
                .isEqualTo("integer");
    }

    @Test
    void schema_genreValue_isOneOfTheFortyNamesAndNeverEmpty() throws java.io.IOException {
        final List<String> genres = texts(schema().path("properties")
                .path("genre")
                .path("properties")
                .path("value")
                .path("enum"));

        assertThat(genres)
                .hasSize(40)
                .contains("Literary fiction", "Gothic novel", "Graphic novel")
                .doesNotContain("");
    }

    @Test
    void schema_audienceValue_isOneOfTheFiveWordsAndNeverEmpty() throws java.io.IOException {
        final List<String> audiences = texts(schema().path("properties")
                .path("audience")
                .path("properties")
                .path("value")
                .path("enum"));

        assertThat(audiences).containsExactlyInAnyOrder("adult", "young adult", "children", "general", "academic");
    }

    @Test
    void promptName_briefSynthesis_usesTheSchemaItsKindAndTemperatureZero() {
        assertThat(PromptName.BRIEF_SYNTHESIS.responseSchema()).isEqualTo(BriefSynthesisSchema.SCHEMA);
        assertThat(PromptName.BRIEF_SYNTHESIS.resourceBaseName()).isEqualTo("brief-synthesis");
        assertThat(PromptName.BRIEF_SYNTHESIS.responseFormatName()).isEqualTo("brief-synthesis");
        assertThat(PromptName.BRIEF_SYNTHESIS.temperature(false)).isEqualTo(0.0);
    }
}
