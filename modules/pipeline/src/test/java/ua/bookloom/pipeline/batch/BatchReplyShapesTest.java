package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Odd reply shapes a small model writes, and the item whose quotes it moved across an id. */
class BatchReplyShapesTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."),
            new BatchItem("3", "1881"),
            new BatchItem("4", "⟦g0⟧Hello⟦g1⟧ there, my old friend."));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";
    private static final String T3 = "1881";
    private static final String T4 = "⟦g0⟧Привіт⟦g1⟧, мій старий друже.";

    private static BatchReply parse(final String reply) {
        return PARSER.parse(reply, ITEMS, "en", "uk");
    }

    // IF an entry under another key were ignored, THEN seven good drafts of one real reply would each be redone alone.
    @Test
    void parse_entriesUnderOtherKeys_areSalvagedAsIfInTheItemsArray() {
        final String reply = "{\"items\": [{\"id\": \"1\", \"target\": \"" + T1 + "\"}],"
                + " \"items_2\": {\"id\": \"2\", \"target\": \"" + T2 + "\"},"
                + " \"items_3\": {\"id\": \"3\", \"target\": \"" + T3 + "\"},"
                + " \"items_4\": {\"id\": \"4\", \"target\": \"" + T4 + "\"}}";

        final BatchReply result = parse(reply);

        assertThat(result.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(result.isClean()).isTrue();
    }

    @Test
    void parse_entriesInNestedUnknownWrappers_areSalvaged() {
        final String reply = "{\"result\":{\"batch\":[{\"id\":1,\"target\":\"" + T1 + "\"}," + "{\"id\":2,\"target\":\""
                + T2 + "\"}]},\"note\":\"done\"}";

        assertThat(parse(reply).acceptedIds()).containsExactly("1", "2");
    }

    @Test
    void parse_oddShapedReplyCutOffInsideAnotherKey_stillSalvagesTheClosedEntries() {
        final String reply = "{\"items\": [{\"id\": \"1\", \"target\": \"" + T1 + "\"}], \"items_2\": "
                + "{\"id\": \"2\", \"target\": \"" + T2 + "\"}, \"items_3\": {\"id\": \"3\", \"tar";

        assertThat(parse(reply).acceptedIds()).containsExactly("1", "2");
    }

    private static final List<BatchItem> SPEECH = List.of(
            new BatchItem("1", "\"They had a passport,\" he said. \"They had credit cards and a watch.\""),
            new BatchItem("2", "He passed me a clear envelope with something inside."));

    // IF a closing quote with no opening one stayed in an accepted item, THEN the sentence the model moved across the
    // id would reach the quality loop as a broken quote and cost a repair call instead of one clean redraft.
    @Test
    void parse_targetWhoseQuotesDoNotPairWhileTheSourcesDo_failsOnlyThatItem() {
        final String reply =
                "{\"items\":[{\"id\":\"1\",\"target\":\"Мудрий дурень». «У них був паспорт», — сказав він. "
                        + "«У них були кредитні картки та годинник.»\"},"
                        + "{\"id\":\"2\",\"target\":\"Він передав мені прозорий конверт із чимось усередині.\"}]}";

        final BatchReply result = PARSER.parse(reply, SPEECH, "en", "uk");

        assertThat(result.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).contains(ItemProblem.QUOTES));
        assertThat(result.acceptedIds()).containsExactly("2");
    }

    @Test
    void parse_targetWhoseQuotesPair_isAccepted() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"«У них був паспорт», — сказав він. "
                + "«У них були кредитні картки та годинник.»\"},"
                + "{\"id\":\"2\",\"target\":\"Він передав мені прозорий конверт із чимось усередині.\"}]}";

        assertThat(PARSER.parse(reply, SPEECH, "en", "uk").acceptedIds()).containsExactly("1", "2");
    }

    // A source that opens a quotation and leaves it open (it runs on in the next paragraph) lets its target stay open.
    @Test
    void parse_sourceOpenQuoteAndTargetOpenToo_isAccepted() {
        final List<BatchItem> open = List.of(new BatchItem("1", "\u201cHe came back at noon and said nothing."));
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"«Він повернувся опівдні й нічого не сказав.\"}]}";

        assertThat(PARSER.parse(reply, open, "en", "uk").outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).isEmpty());
    }

    // IF a control character the source holds were refused in the reply, THEN an old TXT with a form feed could never
    // be translated.
    @Test
    void parse_controlCharacterTheSourceHolds_isAccepted() {
        final List<BatchItem> items = List.of(new BatchItem("1", "She opened the door.\fHe looked outside."));
        final String reply =
                "{\"items\": [{\"id\": \"1\", \"target\": \"Вона відчинила двері.\\fВін визирнув надвір.\"}]}";

        final BatchReply parsed = PARSER.parse(reply, items, "en", "uk");

        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).isEmpty());
        assertThat(parsed.acceptedIds()).containsExactly("1");
    }
}
