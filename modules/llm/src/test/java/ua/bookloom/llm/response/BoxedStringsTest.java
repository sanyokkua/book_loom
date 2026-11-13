package ua.bookloom.llm.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** A JSON string holding a boxed answer is unwrapped, an unterminated one is left, and long input is read promptly. */
class BoxedStringsTest {

    private static final String OPEN = "\"\\\\boxed{";

    @Test
    void unwrap_boxedStrings_keepsTheirContentAsPlainStrings() {
        final String text = "{\"a\": " + OPEN + "one \\\" two}\", \"b\": " + OPEN + "x}\"}";

        final BoxedStrings.Unboxed result = BoxedStrings.unwrap(text);

        assertThat(result.count()).isEqualTo(2);
        assertThat(result.text()).isEqualTo("{\"a\": \"one \\\" two\", \"b\": \"x\"}");
    }

    @Test
    void unwrap_unterminatedOrBrokenStrings_areLeftAlone() {
        final String open = OPEN + "no end";
        final String quoted = OPEN + "a \"quote} \"";
        final String escapedEnd = OPEN + "a\\";

        assertThat(BoxedStrings.unwrap(open)).isEqualTo(new BoxedStrings.Unboxed(open, 0));
        assertThat(BoxedStrings.unwrap(quoted)).isEqualTo(new BoxedStrings.Unboxed(quoted, 0));
        assertThat(BoxedStrings.unwrap(escapedEnd)).isEqualTo(new BoxedStrings.Unboxed(escapedEnd, 0));
    }

    @Test
    void unwrap_aStringAfterABrokenOne_isStillUnwrapped() {
        final String text = OPEN + "broken \" " + OPEN + "fine}\"";

        assertThat(BoxedStrings.unwrap(text).text()).endsWith("\"fine\"");
    }

    @Test
    void unwrap_manyOpeningsWithoutAnEnd_isPrompt() {
        final String text = ("\\" + OPEN).repeat(100_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(BoxedStrings.unwrap(text).count()).isZero());
    }
}
