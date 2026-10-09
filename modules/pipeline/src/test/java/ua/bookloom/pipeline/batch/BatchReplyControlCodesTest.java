package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** A batch reply that writes quote marks as control codes is resolved by the same mapper as a single reply. */
class BatchReplyControlCodesTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";

    private static String write(final Map<String, String> entries) {
        return "{\"items\":["
                + entries.entrySet().stream()
                        .map(entry -> "{\"id\":\"" + entry.getKey() + "\",\"target\":\"" + entry.getValue() + "\"}")
                        .collect(Collectors.joining(","))
                + "]}";
    }

    private static Map<String, String> clean() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("1", T1);
        entries.put("2", T2);
        return entries;
    }

    // IF the source holds quotes and a code has no clear place, THEN only that item is refused.
    @Test
    void parse_unplaceableControlCodeInOneItem_failsOnlyThatItemAsControlCharacters() {
        final List<BatchItem> items = List.of(
                new BatchItem("1", "She opened the door and looked outside."),
                new BatchItem("2", "\"He said nothing,\" she wrote for a long while."));
        final String reply = write(Map.of("1", T1, "2", "Він\\u001d довго\\u001e нічого не казав."));

        final BatchReply parsed = PARSER.parse(reply, items, "en", "uk");

        assertThat(parsed.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).contains(ItemProblem.CONTROL_CHARACTERS));
        assertThat(parsed.acceptedIds()).containsExactly("1");
    }

    // IF a code wraps a reply whose source has no quotes, THEN the batch path drops it like the single path does.
    @Test
    void parse_codesWrappingAnItemWithoutSourceQuotes_areDropped() {
        final Map<String, String> entries = clean();
        entries.put("2", "\\u001cВін довго нічого не казав.\\u001c");

        final BatchReply reply = PARSER.parse(write(entries), ITEMS, "en", "uk");

        assertThat(reply.outcome("2")).hasValueSatisfying(outcome -> {
            assertThat(outcome.target()).isEqualTo(T2);
            assertThat(outcome.problems()).isEmpty();
        });
    }

    // IF the source quotes and the reply writes its marks as codes, THEN the target language's own marks come out.
    @Test
    void parse_codesForQuotesInAQuotedItem_becomeTheTargetLanguagesMarks() {
        final List<BatchItem> items = List.of(new BatchItem("1", "\"He said nothing,\" she wrote."));

        final BatchReply parsed = PARSER.parse(
                "{\"items\":[{\"id\":\"1\",\"target\":\"\\u001eВін нічого не казав\\u001d, написала вона.\"}]}",
                items,
                "en",
                "uk");
        final BatchReply german = PARSER.parse(
                "{\"items\":[{\"id\":\"1\",\"target\":\"\\u001eEr sagte nichts\\u001d, schrieb sie.\"}]}",
                items,
                "en",
                "de");

        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(
                        outcome -> assertThat(outcome.target()).isEqualTo("«Він нічого не казав», написала вона."));
        assertThat(german.outcome("1"))
                .hasValueSatisfying(
                        outcome -> assertThat(outcome.target()).isEqualTo("„Er sagte nichts“, schrieb sie."));
    }
}
