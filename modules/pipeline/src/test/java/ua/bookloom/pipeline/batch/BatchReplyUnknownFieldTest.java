package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The batch reply parser stays tolerant: a field it does not know is ignored, never a reason to drop the item. */
class BatchReplyUnknownFieldTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."),
            new BatchItem("3", "1881"));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";

    @ParameterizedTest
    @ValueSource(strings = {"notes", "flavour"})
    void parse_itemWithUnknownNotesField_isStillRead(final String extraField) {
        final String reply = "{\"items\":["
                + "{\"id\":\"1\",\"target\":\"" + T1 + "\",\"" + extraField + "\":\"a remark\"},"
                + "{\"id\":\"2\",\"target\":\"" + T2 + "\"}]}";

        final BatchReply parsed = PARSER.parse(reply, ITEMS, "en", "uk");

        assertThat(parsed.acceptedIds()).containsExactly("1", "2");
        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.target()).isEqualTo(T1));
    }

    // A reply cut inside its last entry keeps the closed ones, even when each carries a notes field the parser
    // does not know.
    @Test
    void parse_cutReplyWithAnUnknownNotesField_stillSalvagesTheClosedEntries() {
        final String reply = "{\"items\":["
                + "{\"id\":\"1\",\"notes\":\"looked outside - a plain verb\",\"target\":\"" + T1 + "\"},"
                + "{\"id\":\"2\",\"notes\":\"a plain clause\",\"target\":\"" + T2 + "\"},"
                + "{\"id\":\"3\",\"notes\":\"a year\",\"tar";

        final BatchReply parsed = PARSER.parse(reply, ITEMS, "en", "uk");

        assertThat(parsed.acceptedIds()).containsExactly("1", "2");
        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.target()).isEqualTo(T1));
    }
}
