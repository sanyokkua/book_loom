package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.prompt.PromptName;

/** The strict schema the knowledge answer is held to: ten required fields, nothing else, each of a plain type. */
class BookKnowledgeSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] FIELDS = {
        "known", "title", "author", "year", "genre", "audience", "era", "narration", "summary", "confidence"
    };

    private static JsonNode schema() throws java.io.IOException {
        return MAPPER.readTree(BookKnowledgeSchema.SCHEMA);
    }

    @Test
    void schema_isAnObjectOfExactlyTheTenFieldsAllRequiredAndNothingMore() throws java.io.IOException {
        final JsonNode schema = schema();

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(schema.path("properties").fieldNames()).toIterable().containsExactlyInAnyOrder(FIELDS);
        assertThat(StreamSupport.stream(schema.path("required").spliterator(), false)
                        .map(JsonNode::asText))
                .containsExactlyInAnyOrder(FIELDS);
    }

    @ParameterizedTest
    @CsvSource({
        "known,boolean",
        "title,string",
        "author,string",
        "year,integer",
        "genre,string",
        "audience,string",
        "era,string",
        "narration,string",
        "summary,string",
        "confidence,number"
    })
    void schema_eachField_hasItsPlainType(final String field, final String type) throws java.io.IOException {
        assertThat(schema().path("properties").path(field).path("type").asText())
                .isEqualTo(type);
    }

    @Test
    void promptName_bookKnowledge_usesTheSchemaAndSamplesLikeTheOtherSetupCalls() {
        assertThat(PromptName.BOOK_KNOWLEDGE.responseSchema()).isEqualTo(BookKnowledgeSchema.SCHEMA);
        assertThat(PromptName.BOOK_KNOWLEDGE.resourceBaseName()).isEqualTo("book-knowledge");
        assertThat(PromptName.BOOK_KNOWLEDGE.temperature(false)).isEqualTo(0.0);
    }
}
