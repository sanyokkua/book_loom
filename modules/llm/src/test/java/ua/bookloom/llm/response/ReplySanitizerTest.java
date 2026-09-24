package ua.bookloom.llm.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies reasoning-tag removal, outer-fence unwrapping, and final trimming. */
class ReplySanitizerTest {

    // A bare JSON language label before an object must not become part of a translated segment.
    @Test
    void clean_bareJsonLabelBeforeObject_removesLabel() {
        assertThat(ReplySanitizer.clean("json{\"segments\":[]}")).isEqualTo("{\"segments\":[]}");
    }

    @ParameterizedTest
    @MethodSource("replies")
    void clean_removesReasoningAndFencesAndTrims(String reply, String expected) {
        assertThat(ReplySanitizer.clean(reply)).isEqualTo(expected);
    }

    private static Stream<Arguments> replies() {
        return Stream.of(
                arguments("Before <think>private</think> after", "Before  after"),
                arguments("Before <rEaSoNiNg>private</ReAsOnInG> after", "Before  after"),
                arguments("visible <THINKING>private", "visible"),
                arguments(
                        "<think>one</think>left<thinking>two</thinking>middle<reasoning>three</reasoning>right",
                        "leftmiddleright"),
                arguments("```json\n{\"ok\":true}\n```", "{\"ok\":true}"),
                arguments("```\nplain reply\n```", "plain reply"),
                arguments("  plain reply \n", "plain reply"),
                arguments("  <reasoning>only private thoughts", ""));
    }
}
