package ua.bookloom.llm.verify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.llm.LlmModule;

/** The test card's bodies: JSON is indented by Jackson's own printer, anything else is left exactly as it came. */
class PrettyJsonTest {

    private final PrettyJson pretty = new PrettyJson(new LlmModule().objectMapper());

    // IF an object stayed on one line, THEN a long request body would be unreadable in the card.
    @Test
    void of_jsonObject_isIndentedWithNewlines() {
        assertThat(pretty.of("{\"a\":1,\"b\":[true,null]}")).isEqualTo("{\n  \"a\" : 1,\n  \"b\" : [ true, null ]\n}");
    }

    // IF text that is not wholly JSON were changed, THEN the model's own words would be altered in the card.
    @ParameterizedTest
    @ValueSource(strings = {"Fine.", "", "{\"a\":1} and more", "{\"a\":", "42", "\"just a string\"", "  "})
    void of_notAJsonContainer_isReturnedUnchanged(final String text) {
        assertThat(pretty.of(text)).isEqualTo(text);
    }
}
