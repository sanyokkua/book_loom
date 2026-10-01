package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** The judge schema bounds every list and every free-text field, so a looping reply has nowhere to grow. */
class JudgeSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void schema_findingsAndDeferrals_areBoundedInCountAndText() throws Exception {
        final JsonNode properties = MAPPER.readTree(JudgeSchema.SCHEMA).path("properties");

        assertThat(properties.path("verdict").path("maxLength").asInt()).isEqualTo(40);
        assertThat(properties.path("findings").path("maxItems").asInt()).isEqualTo(12);
        assertThat(properties.path("deferrals").path("maxItems").asInt()).isEqualTo(8);
        final JsonNode finding = properties.path("findings").path("items").path("properties");
        assertThat(finding.path("note").path("maxLength").asInt()).isEqualTo(240);
        assertThat(finding.path("type").path("maxLength").asInt()).isEqualTo(40);
        final JsonNode deferral = properties.path("deferrals").path("items").path("properties");
        assertThat(deferral.path("reason").path("maxLength").asInt()).isEqualTo(240);
    }

    @Test
    void schema_required_isStillOnlyTheScore() throws Exception {
        assertThat(MAPPER.readTree(JudgeSchema.SCHEMA).path("required").toString())
                .isEqualTo("[\"score\"]");
    }
}
