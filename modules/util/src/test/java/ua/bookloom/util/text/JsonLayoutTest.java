package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonLayoutTest {

    @Test
    void pretty_nestedObject_putsEachMemberOnItsOwnIndentedLine() {
        assertThat(JsonLayout.pretty("{\"a\":1,\"b\":{\"c\":[1,2]}}"))
                .isEqualTo("{\n  \"a\": 1,\n  \"b\": {\n    \"c\": [\n      1,\n      2\n    ]\n  }\n}");
    }

    @Test
    void pretty_stringHoldingBracesCommasQuotesAndColons_isLeftAlone() {
        assertThat(JsonLayout.pretty("{\"t\":\"a, {b}: \\\"c\\\" \\\\\"}"))
                .isEqualTo("{\n  \"t\": \"a, {b}: \\\"c\\\" \\\\\"\n}");
    }

    @Test
    void pretty_emptyContainers_stayOnOneLine() {
        assertThat(JsonLayout.pretty("{\"a\":[],\"b\":{}}")).isEqualTo("{\n  \"a\": [],\n  \"b\": {}\n}");
    }

    @Test
    void pretty_oneDocumentPerLine_laysEachOutAndDropsBlankLines() {
        assertThat(JsonLayout.pretty("{\"a\":1}\n\n{\"b\":2}")).isEqualTo("{\n  \"a\": 1\n}\n{\n  \"b\": 2\n}");
    }

    @Test
    void pretty_textThatIsNotJson_comesBackUnchanged() {
        assertThat(JsonLayout.pretty("plain text, not {json}")).isEqualTo("plain text, not {json}");
    }

    @Test
    void pretty_escapedQuotesAroundSpacedText_keepsEverySpaceAndNoLineBreak() {
        final String json = "{\"m\":\"He sailed, then \\\"she said: go {on}\\\" and left\\n\",\"n\":1}";
        assertThat(JsonLayout.pretty(json))
                .isEqualTo("{\n  \"m\": \"He sailed, then \\\"she said: go {on}\\\" and left\\n\",\n  \"n\": 1\n}");
    }
}
