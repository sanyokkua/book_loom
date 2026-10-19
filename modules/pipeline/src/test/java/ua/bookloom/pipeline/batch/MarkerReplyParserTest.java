package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The plain-text reply of the marked-passage arm (15i.X1) is split by code at its {@code ⟦sN⟧} markers: an id with
 * its own text is read as a JSON entry is, a missing, doubled or reordered marker leaves that id MISSING (so the
 * missing-ids re-ask picks it up, then the single draft), and no marker survives into any text.
 */
class MarkerReplyParserTest {

    private static final BatchReplyParser PARSER = new BatchReplyParser(new ObjectMapper());
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."),
            new BatchItem("3", "The rain stopped."),
            new BatchItem("4", "⟦g0⟧Hello⟦g1⟧ there, my old friend."));
    private static final String T1 = "Вона відчинила двері й визирнула надвір.";
    private static final String T2 = "Він довго нічого не казав.";
    private static final String T3 = "Дощ припинився.";
    private static final String T4 = "⟦g0⟧Привіт⟦g1⟧, мій старий друже.";

    private static BatchReply parse(final String reply) {
        return PARSER.parse(reply, ITEMS, "en", "uk", BatchFormat.MARKERS);
    }

    private static String targetOf(final BatchReply reply, final String id) {
        return reply.outcome(id).orElseThrow().target();
    }

    private static ItemStatus statusOf(final BatchReply reply, final String id) {
        return reply.outcome(id).orElseThrow().status();
    }

    @Test
    void parse_everyMarkerOnceInOrder_acceptsAllWithTheirExactText() {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(reply.readable()).isTrue();
        assertThat(reply.isClean()).isTrue();
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(targetOf(reply, "1")).isEqualTo(T1);
        assertThat(targetOf(reply, "2")).isEqualTo(T2);
        assertThat(targetOf(reply, "3")).isEqualTo(T3);
        assertThat(targetOf(reply, "4")).isEqualTo(T4);
    }

    // The model may run the passage on one line or glue a marker to its text; the split does not care.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "⟦s1⟧ Вона відчинила двері й визирнула надвір. ⟦s2⟧ Він довго нічого не казав. ⟦s3⟧ Дощ припинився."
                        + " ⟦s4⟧ ⟦g0⟧Привіт⟦g1⟧, мій старий друже.",
                "⟦s1⟧Вона відчинила двері й визирнула надвір.\n⟦s2⟧Він довго нічого не казав.\n⟦s3⟧Дощ припинився."
                        + "\n⟦s4⟧⟦g0⟧Привіт⟦g1⟧, мій старий друже.\n"
            })
    void parse_otherLayoutsOfTheSameMarkedReply_acceptAllWithTrimmedText(final String reply) {
        final BatchReply parsed = parse(reply);

        assertThat(parsed.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(targetOf(parsed, "1")).isEqualTo(T1);
        assertThat(targetOf(parsed, "4")).isEqualTo(T4);
    }

    // The text before the first marker is the model talking, not an item: it is dropped (and logged), never attached.
    @Test
    void parse_textBeforeTheFirstMarker_isDroppedNotAttachedToAnItem() {
        final BatchReply reply = parse(
                "Here is the translation:\n\n⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(targetOf(reply, "1")).isEqualTo(T1);
    }

    @Test
    void parse_replyWithNoMarker_isUnreadableSoEveryIdFallsBack() {
        final BatchReply reply = parse("I'm sorry, but I can't help with translating this text.");

        assertThat(reply.readable()).isFalse();
        assertThat(reply.failingIds()).containsExactly("1", "2", "3", "4");
    }

    // IF the reply stops after item 2, THEN items 3 and 4 are MISSING and the re-ask can pick them up together.
    @Test
    void parse_replyEndingAfterTheSecondMarker_leavesTheLastTwoMissing() {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2);

        assertThat(reply.readable()).isTrue();
        assertThat(reply.acceptedIds()).containsExactly("1", "2");
        assertThat(statusOf(reply, "3")).isEqualTo(ItemStatus.MISSING);
        assertThat(statusOf(reply, "4")).isEqualTo(ItemStatus.MISSING);
        assertThat(reply.count(ItemStatus.MISSING)).isEqualTo(2);
    }

    // IF the marker of item 3 is gone, THEN item 3 is MISSING and item 2, which swallowed its text, is not trusted.
    @Test
    void parse_missingMiddleMarker_failsTheMissingIdAndTheItemThatSwallowedIt() {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + " " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(statusOf(reply, "3")).isEqualTo(ItemStatus.MISSING);
        assertThat(statusOf(reply, "2")).isEqualTo(ItemStatus.MERGED_SUSPECT);
        assertThat(reply.acceptedIds()).containsExactly("1", "4");
        assertThat(reply.failingIds()).containsExactly("2", "3");
    }

    // IF a marker is written twice, THEN the model lost its place and that id is MISSING, whichever text is right.
    @Test
    void parse_doubledMarker_leavesThatIdMissingAndKeepsTheOthers() {
        final BatchReply reply =
                parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(statusOf(reply, "2")).isEqualTo(ItemStatus.MISSING);
        assertThat(reply.acceptedIds()).containsExactly("1", "3", "4");
        assertThat(reply.count(ItemStatus.DUPLICATE)).isZero();
        assertThat(reply.count(ItemStatus.EXTRA)).isZero();
    }

    // IF a marker comes after a later one, THEN the model lost the order and that id is MISSING; the rest keep theirs.
    @Test
    void parse_reorderedMarker_leavesTheOutOfOrderIdMissing() {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s4⟧ " + T4);

        assertThat(statusOf(reply, "2")).isEqualTo(ItemStatus.MISSING);
        assertThat(reply.acceptedIds()).containsExactly("1", "3", "4");
        assertThat(targetOf(reply, "3")).isEqualTo(T3);
    }

    @Test
    void parse_markerWithNoTextAfterIt_isMissingLikeAnEmptyEntry() {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(statusOf(reply, "2")).isEqualTo(ItemStatus.MISSING);
        assertThat(reply.acceptedIds()).containsExactly("1", "3", "4");
    }

    @Test
    void parse_markerOfAnIdTheBatchNeverHeld_isExtraAndTakenFromNoExpectedId() {
        final BatchReply reply =
                parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4 + "\n\n⟦s9⟧ Зайве.");

        assertThat(statusOf(reply, "9")).isEqualTo(ItemStatus.EXTRA);
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.count(ItemStatus.EXTRA)).isEqualTo(1);
    }

    // The placeholders of an item are checked as in the JSON arm, per item: a dropped pair fails only that item.
    @Test
    void parse_itemThatDropsItsPlaceholders_failsOnlyThatItemWithATokenProblem() {
        final BatchReply reply =
                parse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ Привіт, мій старий друже.");

        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3");
        assertThat(reply.outcome("4").orElseThrow().problems()).contains(ItemProblem.TOKENS);
    }

    // No s-marker survives into a target: variants of the marker's spelling are still markers, and are stripped
    // before the placeholder check, which would read a stray ⟦ or ⟧ as a broken token.
    @ParameterizedTest
    @ValueSource(strings = {"⟦S2⟧", "⟦ s2 ⟧"})
    void parse_markerSpelledLoosely_isStillSplitOnAndNeverLeftInATarget(final String marker) {
        final BatchReply reply = parse("⟦s1⟧ " + T1 + "\n\n" + marker + " " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4);

        assertThat(reply.outcomes())
                .allSatisfy(outcome -> assertThat(outcome.target()).doesNotContain("⟦s", "⟦S", "⟦ s"));
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(targetOf(reply, "2")).isEqualTo(T2);
    }

    // Control codes in a plain-text reply are resolved by the same mapper as in a JSON entry.
    @Test
    void parse_controlCodesForQuotesInAQuotedItem_becomeTheTargetLanguagesMarks() {
        final List<BatchItem> items = List.of(new BatchItem("1", "\"He said nothing,\" she wrote."));

        final BatchReply reply = PARSER.parse(
                "⟦s1⟧ \u001eВін нічого не казав\u001d, написала вона.", items, "en", "uk", BatchFormat.MARKERS);

        assertThat(targetOf(reply, "1")).isEqualTo("«Він нічого не казав», написала вона.");
        assertThat(reply.acceptedIds()).containsExactly("1");
    }

    // A JSON reply to a marked passage has no marker, so it is not mistaken for an answer.
    @Test
    void parse_jsonReplyToAMarkedPassage_isUnreadable() {
        final BatchReply reply = parse("{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"}]}");

        assertThat(reply.readable()).isFalse();
    }

    // The JSON and dual arms read their reply exactly as before the format existed.
    @Test
    void parse_jsonAndDualFormats_readTheJsonReplyAsTheFourArgumentParseDoes() {
        final String json = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"},{\"id\":\"2\",\"target\":\"" + T2
                + "\"},{\"id\":\"3\",\"target\":\"" + T3 + "\"},{\"id\":\"4\",\"target\":\"" + T4 + "\"}]}";

        assertThat(PARSER.parse(json, ITEMS, "en", "uk", BatchFormat.JSON).acceptedIds())
                .containsExactly("1", "2", "3", "4");
        assertThat(PARSER.parse(json, ITEMS, "en", "uk", BatchFormat.DUAL).acceptedIds())
                .containsExactly("1", "2", "3", "4");
    }
}
