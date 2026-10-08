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

    private static final String SOURCE = "Come on, I said.";

    @ParameterizedTest(name = "{0}")
    @MethodSource("structuredReplyCases")
    void parse_exactTargetObject_returnsStructuredTranslation(
            final String name, final String reply, final String expectedTranslation) {
        final ParsedReply parsed = parser().parse(reply, SOURCE);

        assertThat(parsed.kind()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(parsed.translation()).isEqualTo(expectedTranslation);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStructuredReplyCases")
    void parse_wrongReplyShape_isNeverTranslation(final String name, final String reply) {
        final ParsedReply parsed = parser().parse(reply, SOURCE);

        assertThat(parsed.kind()).isEqualTo(ReplyKind.INVALID_STRUCTURED);
        assertThat(parsed.translation()).isEmpty();
    }

    // IF a control character the source holds (U+001C-U+001F, VT or FF in an old TXT or FB2) were refused in the
    // reply, THEN that segment could never be translated; one the source does not hold, or holds fewer times, still is.
    @ParameterizedTest(name = "{0}")
    @MethodSource("sourceControlCases")
    void parse_controlCharacterAgainstTheSource_isAcceptedOnlyAsOftenAsTheSourceHasIt(
            final String name, final String source, final String reply, final ReplyKind expected) {
        assertThat(parser().parse(reply, source).kind()).isEqualTo(expected);
    }

    private static Stream<Arguments> sourceControlCases() {
        return Stream.of(
                Arguments.of(
                        "form feed the source has",
                        "Page one.\fPage two.",
                        "{\"target\":\"Сторінка один.\\fСторінка два.\"}",
                        ReplyKind.STRUCTURED),
                Arguments.of(
                        "file separator the source has",
                        "\u001cRun\u001c",
                        "{\"target\":\"\\u001cБіжи\\u001c\"}",
                        ReplyKind.STRUCTURED),
                Arguments.of(
                        "one form feed more than the source",
                        "Page one.\fPage two.",
                        "{\"target\":\"\\fСторінка один.\\fСторінка два.\"}",
                        ReplyKind.INVALID_STRUCTURED),
                Arguments.of(
                        "another control than the source's",
                        "Page one.\fPage two.",
                        "{\"target\":\"Сторінка один.\\u000bСторінка два.\"}",
                        ReplyKind.INVALID_STRUCTURED));
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
                Arguments.of("plain text", "Привіт, світе."),
                Arguments.of(
                        "controls written for quotes and dash",
                        "{\"target\":\"\\u001eДавай\\u001d, \\u0013сказав я\"}"),
                Arguments.of("control at the end", "{\"target\":\"Цюрих і Джек\\u001f\"}"),
                Arguments.of("control at the start", "{\"target\":\"\\u001cЦюрих і Джек\"}"));
    }

    private static DraftReplyParser parser() {
        return new DraftReplyParser(new ObjectMapper());
    }
}
