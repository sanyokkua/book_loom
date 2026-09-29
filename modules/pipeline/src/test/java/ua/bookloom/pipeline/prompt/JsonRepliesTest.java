package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The tolerant reader: the first JSON value, even with prose after it; nothing for anything else. */
class JsonRepliesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void tolerant_jsonFollowedByProse_readsTheFirstValue() {
        assertThat(JsonReplies.tolerant(mapper, "{\"score\":0.9} thanks"))
                .hasValueSatisfying(
                        node -> assertThat(node.path("score").asDouble()).isEqualTo(0.9));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "", "   ", "{\"score\":"})
    void tolerant_textThatIsNotJson_isEmpty(final String reply) {
        assertThat(JsonReplies.tolerant(mapper, reply)).isEmpty();
    }
}
