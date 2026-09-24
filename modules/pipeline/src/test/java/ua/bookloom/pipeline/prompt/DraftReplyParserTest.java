package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;

/** Verifies documented reply shapes and prevents malformed JSON from becoming book text. */
class DraftReplyParserTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("structuredReplyCases")
    void parse_exactTargetObject_returnsStructuredTranslation(
            final String name, final String reply, final String expectedTranslation) {
        final ParsedReply parsed = parser().parse(reply);

        assertThat(parsed.kind()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(parsed.translation()).isEqualTo(expectedTranslation);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStructuredReplyCases")
    void parse_wrongReplyShape_isNeverTranslation(final String name, final String reply) {
        final ParsedReply parsed = parser().parse(reply);

        assertThat(parsed.kind()).isEqualTo(ReplyKind.INVALID_STRUCTURED);
        assertThat(parsed.translation()).isEmpty();
    }

    private static Stream<Arguments> structuredReplyCases() {
        return Stream.of(Arguments.of("target", "{\"target\":\"Привіт\"}", "Привіт"));
    }

    private static Stream<Arguments> invalidStructuredReplyCases() {
        return Stream.of(
                Arguments.of("segments array", "{\"segments\":[{\"target\":\"Привіт\"}]}"),
                Arguments.of("id map", "{\"Book.md:0\":\"Привіт\"}"),
                Arguments.of("extra field", "{\"target\":\"Привіт\",\"note\":\"x\"}"),
                Arguments.of("blank target", "{\"target\":\"   \"}"),
                Arguments.of("malformed json", "{\"target\":"),
                Arguments.of("wrapper prose", "Here is {\"target\":\"Привіт\"}"),
                Arguments.of("plain text", "Привіт, світе."));
    }

    private static DraftReplyParser parser() {
        return new DraftReplyParser(new ObjectMapper());
    }
}
