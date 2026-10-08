package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;

/** Quote and dash codes the model wrote as control characters are mapped back; an unmappable reply stays refused. */
class DraftReplyParserControlTest {

    private static final String SOURCE = "\"Miles, where did she go?\" she asked.";

    private final DraftReplyParser parser = new DraftReplyParser(new ObjectMapper());

    // The reply shape of the Oct 8 run: the quote codes round the string, a code for the dash.
    @Test
    void parse_quoteAndDashCodes_areMappedToTheTargetMarks() {
        final String reply = "{\"target\": \"\\u001eМайлз, куди вона пішла?\\u001d \\u001f спитала вона.\"}";

        final ParsedReply parsed = parser.parse(reply, SOURCE, "uk");

        assertThat(parsed.kind()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(parsed.translation()).isEqualTo("«Майлз, куди вона пішла?» — спитала вона.");
    }

    @Test
    void parse_twoArgumentForm_mapsToGuillemets() {
        final ParsedReply parsed = parser.parse("{\"target\":\"\\u001eТак\\u001d\"}", "\"Yes\"");

        assertThat(parsed.translation()).isEqualTo("«Так»");
    }

    @Test
    void parse_oddCountOfCodes_isRefusedWithTheControlDiagnostic() {
        final ParsedReply parsed = parser.parse("{\"target\":\"\\u001eКуди вона пішла?\"}", SOURCE, "uk");

        assertThat(parsed.kind()).isEqualTo(ReplyKind.INVALID_STRUCTURED);
        assertThat(parsed.translation()).isEmpty();
        assertThat(parsed.diagnostic()).isEqualTo(DraftReplyParser.CONTROL_CHARACTERS_DIAGNOSTIC);
    }

    // IF closers after the object refused the reply, THEN a right target would be lost to a tail of syntax.
    @ParameterizedTest
    @ValueSource(strings = {"]}", "}]}", "\"}", "\n}\n"})
    void parse_onlyClosersAfterTheObject_areCut(final String tail) {
        final ParsedReply parsed = parser.parse("{\"target\":\"Він пішов.\"}" + tail, "He left.", "uk");

        assertThat(parsed.kind()).isEqualTo(ReplyKind.STRUCTURED);
        assertThat(parsed.translation()).isEqualTo("Він пішов.");
    }

    @Test
    void parse_proseAfterTheObject_isStillRefused() {
        final ParsedReply parsed = parser.parse("{\"target\":\"Він пішов.\"} Ось переклад.", "He left.", "uk");

        assertThat(parsed.kind()).isEqualTo(ReplyKind.INVALID_STRUCTURED);
    }
}
