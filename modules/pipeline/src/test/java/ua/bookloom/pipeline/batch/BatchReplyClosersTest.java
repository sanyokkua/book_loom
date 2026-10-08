package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The JSON closers a model writes into its last string, and the junk it runs on with after the closing brace. */
class BatchReplyClosersTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";

    private static BatchReply parse(final String reply) {
        return PARSER.parse(reply, ITEMS, "en", "uk");
    }

    private static String entries(final String last) {
        return "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"},{\"id\":\"2\",\"target\":\"" + last + "\"}]}";
    }

    // IF closers in the last string made the item fail, THEN a clean draft would be drafted again alone for nothing.
    @ParameterizedTest
    @ValueSource(strings = {T2 + "\\\"}", T2 + "\\\"}]}", T2 + "}]}"})
    void parse_lastTargetEndingInClosers_isStillAcceptedAndKeptRawForTheGate(final String last) {
        final BatchReply result = parse(entries(last));

        assertThat(result.acceptedIds()).containsExactly("1", "2");
        assertThat(result.isClean()).isTrue();
    }

    // IF nothing but closers were stripped to an empty text, THEN a blank target would pass as a draft.
    @ParameterizedTest
    @ValueSource(strings = {"\\\"}", "}]}"})
    void parse_targetOfOnlyClosers_isALeakNotADraft(final String last) {
        final BatchReply result = parse(entries(last));

        assertThat(result.failingIds()).contains("2");
    }

    @Test
    void parse_closerInsideTheText_isALeak() {
        final BatchReply result = parse(entries("Він довго }нічого не казав."));

        assertThat(result.failingIds()).containsExactly("2");
    }

    @Test
    void parse_sourceWithBraces_keepsTheirCloser() {
        final List<BatchItem> items = List.of(new BatchItem("1", "Use the set {a, b} here."));

        final BatchReply result = PARSER.parse(
                "{\"items\":[{\"id\":\"1\",\"target\":\"Використайте набір {a, b} тут.\"}]}", items, "en", "uk");

        assertThat(result.acceptedIds()).containsExactly("1");
    }

    // IF text after the closing brace were salvaged, THEN an id the model invented in its runaway tail would be read.
    @Test
    void parse_junkWithAnEntryAfterTheClosingBrace_isCutBeforeSalvage() {
        final String broken =
                "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"},{\"id\":\"2\",\"target\":\"" + T2 + "\"}}";
        final String junk = "\n</div><p>{\"id\":\"9\",\"target\":\"Сторонній текст.\"}</p></body>";

        final BatchReply result = parse(broken + junk);

        assertThat(result.acceptedIds()).containsExactly("1", "2");
        assertThat(result.outcomes()).extracting(ItemOutcome::id).containsExactly("1", "2");
    }

    @Test
    void parse_junkAfterAClosedObject_isIgnored() {
        final BatchReply result = parse(entries(T2) + "\n</div><p>{\"id\":\"9\",\"target\":\"Інше.\"}");

        assertThat(result.outcomes()).extracting(ItemOutcome::id).containsExactly("1", "2");
    }
}
