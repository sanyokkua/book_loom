package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * What each arm of the batch format (15i.X1) sends: the JSON items as today, one passage marked {@code ⟦sN⟧} at its
 * items' starts with no response schema, or the whole passage as context beside the numbered items under the same
 * schema as today.
 */
class BatchFormatPromptTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final String A = "She opened the door.";
    private static final String B = "He said nothing for a long while.";
    private static final String C = "⟦g0⟧Hello⟦g1⟧ there, my old friend.";
    private static final List<BatchItem> ITEMS =
            List.of(new BatchItem("1", A), new BatchItem("2", B), new BatchItem("3", C));

    private static BatchPromptBuilder builder(final BatchFormat format) {
        return new BatchPromptBuilder(
                TEMPLATES,
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP),
                format,
                false);
    }

    private static List<ChatMessage> messages(final BatchFormat format) {
        return builder(format).messagesFor(BatchContext.empty(), ITEMS);
    }

    private static String system(final BatchFormat format) {
        return messages(format).get(0).content();
    }

    private static String user(final BatchFormat format) {
        return messages(format).get(1).content();
    }

    private static ChatRequest request(final BatchFormat format) {
        final BatchPromptBuilder builder = builder(format);
        return builder.requestFor(builder.messagesFor(BatchContext.empty(), ITEMS), ITEMS, false);
    }

    private static int occurrences(final String text, final String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void format_builtWithAFormat_isThatFormat() {
        assertThat(builder(BatchFormat.JSON).format()).isEqualTo(BatchFormat.JSON);
        assertThat(builder(BatchFormat.MARKERS).format()).isEqualTo(BatchFormat.MARKERS);
        assertThat(builder(BatchFormat.DUAL).format()).isEqualTo(BatchFormat.DUAL);
    }

    // Arm (a): today's prompt, unchanged by the switch existing.
    @Test
    void messagesFor_jsonFormat_numbersTheItemsAndAsksForTheSchema() {
        assertThat(user(BatchFormat.JSON))
                .contains("<s id=\"1\">" + A + "</s>", "<s id=\"3\">" + C + "</s>")
                .doesNotContain("⟦s1⟧", "⟦s2⟧");
        assertThat(system(BatchFormat.JSON)).contains("JSON");
        assertThat(request(BatchFormat.JSON).responseFormat()).isNotNull();
    }

    // Arm (b): the items are one passage, each starting at its marker, in order.
    @Test
    void messagesFor_markersFormat_showsOnePassageWithAMarkerAtEachItemStart() {
        assertThat(user(BatchFormat.MARKERS)).containsSubsequence("⟦s1⟧ " + A, "⟦s2⟧ " + B, "⟦s3⟧ " + C);
        assertThat(user(BatchFormat.MARKERS)).doesNotContain("<s id=", "⟦s4⟧");
        assertThat(occurrences(user(BatchFormat.MARKERS), A)).isEqualTo(1);
    }

    // Arm (b): there is no JSON anywhere in the prompt, since the reply is plain text.
    @Test
    void messagesFor_markersFormat_mentionsNoJsonNoItemEntriesAndNoSchemaReminder() {
        assertThat(system(BatchFormat.MARKERS)).doesNotContain("JSON", "\"items\"", "<s id=");
        assertThat(user(BatchFormat.MARKERS)).doesNotContain("JSON", "\"items\"", "\"target\"", "schema");
    }

    // Arm (b): the rules and the worked example tell the model to start each translated item with its marker.
    @Test
    void messagesFor_markersFormat_systemMessageTeachesTheMarkersWithAnExample() {
        assertThat(system(BatchFormat.MARKERS)).contains("⟦s1⟧", "⟦s2⟧").containsIgnoringCase("marker");
    }

    // Arm (b): the token list per item still names each item's own placeholders, by the item id.
    @Test
    void messagesFor_markersFormat_stillListsEachItemsPlaceholders() {
        assertThat(user(BatchFormat.MARKERS)).contains("[Immutable tokens per item", "3: ⟦g0⟧ ⟦g1⟧");
    }

    // Arm (b): no constrained decoding — the request carries no response format at all.
    @Test
    void requestFor_markersFormat_sendsNoResponseFormatButTheSameCallKind() {
        final ChatRequest markers = request(BatchFormat.MARKERS);
        final ChatRequest json = request(BatchFormat.JSON);

        assertThat(markers.responseFormat()).isNull();
        assertThat(markers.callKind()).isEqualTo(CallKind.DRAFT).isEqualTo(json.callKind());
        assertThat(markers.maxOutputTokens()).isNotNull().isEqualTo(json.maxOutputTokens());
    }

    // Arm (c): the whole passage once as context, then the numbered items, in the prompt's order.
    @Test
    void messagesFor_dualFormat_showsThePassageAsContextBeforeTheNumberedItems() {
        final String user = user(BatchFormat.DUAL);

        assertThat(user).containsSubsequence(A, B, C, "<Items>", "<s id=\"1\">" + A + "</s>");
        assertThat(user).contains("<s id=\"2\">" + B + "</s>", "<s id=\"3\">" + C + "</s>");
        assertThat(occurrences(user, A)).isEqualTo(2);
        assertThat(occurrences(user, B)).isEqualTo(2);
        assertThat(user).doesNotContain("⟦s1⟧", "⟦s2⟧");
    }

    // Arm (c): the reply is the JSON of today, under the very schema of today.
    @Test
    void requestFor_dualFormat_asksForTheSameSchemaAsJson() {
        assertThat(request(BatchFormat.DUAL).responseFormat())
                .isNotNull()
                .isEqualTo(request(BatchFormat.JSON).responseFormat());
        assertThat(system(BatchFormat.DUAL)).contains("JSON");
    }

    // Arm (c): the passage is context only, which the rules say, so it is never translated twice.
    @Test
    void messagesFor_dualFormat_saysThePassageIsContextOnly() {
        assertThat(user(BatchFormat.DUAL) + system(BatchFormat.DUAL)).containsIgnoringCase("passage");
        assertThat(user(BatchFormat.DUAL)).containsIgnoringCase("context only");
    }

    // The window fit sizes a batch by its prompt, so the passage the dual arm repeats must show in the count.
    @Test
    void promptTokens_dualFormat_countsThePassageTheItemsRepeat() {
        final int json = tokens(BatchFormat.JSON);
        final int dual = tokens(BatchFormat.DUAL);

        assertThat(dual).isGreaterThan(json);
    }

    private static int tokens(final BatchFormat format) {
        return ua.bookloom.pipeline.prompt.PromptBreakdown.of(messages(format)).total();
    }
}
