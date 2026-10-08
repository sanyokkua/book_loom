package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** One scenario table: what each failure of a batch reply is named, per id. */
class BatchReplyParserTest {

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

    private static Map<String, String> clean() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("1", T1);
        entries.put("2", T2);
        entries.put("3", T3);
        entries.put("4", T4);
        return entries;
    }

    private static BatchReply parse(final String reply) {
        return PARSER.parse(reply, ITEMS, "en", "uk");
    }

    /** The reply as the model writes it, one entry per map entry in order. */
    private static String write(final List<Map.Entry<String, String>> entries) {
        return "{\"items\":["
                + entries.stream()
                        .map(entry -> "{\"id\":\"" + entry.getKey() + "\",\"target\":\"" + entry.getValue() + "\"}")
                        .collect(Collectors.joining(","))
                + "]}";
    }

    private static String write(final Map<String, String> entries) {
        return write(List.copyOf(entries.entrySet()));
    }

    @Test
    void parse_everyIdOnceWithItsTokens_acceptsAllInOrder() {
        final BatchReply reply = parse(write(clean()));

        assertThat(reply.readable()).isTrue();
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.isClean()).isTrue();
        assertThat(reply.outcome("4"))
                .hasValueSatisfying(outcome -> assertThat(outcome.target()).isEqualTo(T4));
    }

    @Test
    void parse_numberOnlyItemCopied_isAccepted() {
        final BatchReply reply = parse(write(clean()));

        assertThat(reply.outcome("3"))
                .hasValueSatisfying(outcome -> assertThat(outcome.isAccepted()).isTrue());
    }

    @Test
    void parse_missingId_isNamedMissingAndOnlyItFails() {
        final Map<String, String> entries = clean();
        entries.remove("2");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.MISSING));
        assertThat(reply.failingIds()).containsExactly("2");
        assertThat(reply.acceptedIds()).containsExactly("1", "3", "4");
    }

    @Test
    void parse_blankTargetUnderAnId_isMissing() {
        final Map<String, String> entries = clean();
        entries.put("2", "   ");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.failingIds()).containsExactly("2");
        assertThat(reply.count(ItemStatus.MISSING)).isEqualTo(1);
    }

    @Test
    void parse_duplicateId_failsThatIdWhateverItsTexts() {
        final List<Map.Entry<String, String>> entries = new java.util.ArrayList<>(clean().entrySet());
        entries.add(2, Map.entry("2", "Він мовчав дуже довго."));

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.DUPLICATE));
        assertThat(reply.failingIds()).containsExactly("2");
    }

    @Test
    void parse_mergedPair_namesTheMergedIdAndTheMissingOne() {
        final Map<String, String> entries = clean();
        entries.put("1", T1 + " " + T2);
        entries.remove("2");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.MERGED_SUSPECT));
        assertThat(reply.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.MISSING));
        assertThat(reply.failingIds()).containsExactly("1", "2");
        assertThat(reply.acceptedIds()).containsExactly("3", "4");
    }

    @Test
    void parse_mergedPairOfTokenItems_isSuspectedByTheirCombinedTokens() {
        final List<BatchItem> items = List.of(
                new BatchItem("1", "⟦g0⟧Yes.⟦g1⟧"), new BatchItem("2", "⟦g0⟧No.⟦g1⟧"), new BatchItem("3", "Maybe."));
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("1", "⟦g0⟧Так.⟦g1⟧ ⟦g0⟧Ні.⟦g1⟧");
        entries.put("3", "Може.");

        final BatchReply reply = PARSER.parse(write(entries), items, "en", "uk");

        assertThat(reply.outcome("1"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.MERGED_SUSPECT));
    }

    @Test
    void parse_extraId_isNamedExtraAndTheExpectedIdsStillAccepted() {
        final Map<String, String> entries = clean();
        entries.put("9", "Зайвий рядок.");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("9"))
                .hasValueSatisfying(outcome -> assertThat(outcome.status()).isEqualTo(ItemStatus.EXTRA));
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.failingIds()).isEmpty();
        assertThat(reply.isClean()).isFalse();
    }

    @Test
    void parse_controlCharactersInOneItem_failsOnlyThatItemAsControlCharacters() {
        final Map<String, String> entries = clean();
        entries.put("2", "\\u001eВін\\u001d довго нічого не казав.");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("2"))
                .hasValueSatisfying(
                        outcome -> assertThat(outcome.problems()).containsExactly(ItemProblem.CONTROL_CHARACTERS));
        assertThat(reply.acceptedIds()).containsExactly("1", "3", "4");
    }

    @Test
    void parse_droppedTokenInOneItem_failsOnlyThatItemWithTokens() {
        final Map<String, String> entries = clean();
        entries.put("4", "⟦g0⟧Привіт, мій старий друже.");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("4")).hasValueSatisfying(outcome -> {
            assertThat(outcome.status()).isEqualTo(ItemStatus.OK);
            assertThat(outcome.problems()).containsExactly(ItemProblem.TOKENS);
        });
        assertThat(reply.failingIds()).containsExactly("4");
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3");
    }

    @Test
    void parse_tokenMovedToAnotherItem_failsBothItems() {
        final List<BatchItem> items = List.of(
                new BatchItem("1", "⟦g0⟧Hello⟦g1⟧ there, my old friend."), new BatchItem("2", "Goodbye, my friend."));
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("1", "Привіт, мій старий друже.");
        entries.put("2", "⟦g0⟧До⟦g1⟧ побачення, друже.");

        final BatchReply reply = PARSER.parse(write(entries), items, "en", "uk");

        assertThat(reply.failingIds()).containsExactly("1", "2");
    }

    @Test
    void parse_targetFarTooLongForItsSource_failsTheLengthBand() {
        final Map<String, String> entries = clean();
        entries.put(
                "2",
                "Він довго нічого не казав, а потім ще довше мовчав, і ніхто в кімнаті не наважувався порушити цю тишу.");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).containsExactly(ItemProblem.TOO_LONG));
    }

    @Test
    void parse_refusalInProse_isNotReadableAndEveryIdFails() {
        final BatchReply reply = parse("I'm sorry, but I can't help with translating this text.");

        assertThat(reply.readable()).isFalse();
        assertThat(reply.failingIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.count(ItemStatus.MISSING)).isEqualTo(4);
    }

    @Test
    void parse_emptyReply_isNotReadable() {
        assertThat(parse("").readable()).isFalse();
    }

    @Test
    void parse_wrappedInProseAndFence_stillReadsTheEntries() {
        final String wrapped =
                "Here is the translation:\n```\n" + write(clean()) + "\n```\nLet me know if you need more.";

        assertThat(parse(wrapped).acceptedIds()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void parse_replyCutOffInTheLastEntry_failsOnlyTheLastId() {
        final String full = write(clean());
        final String cut = full.substring(0, full.indexOf(T4) + 8);

        final BatchReply reply = parse(cut);

        assertThat(reply.failingIds()).containsExactly("4");
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3");
    }

    // A small model stopped by its cap left a 10,000-character reply of one phrase repeated, cut inside its first entry
    // (the real run's "сільському сільському …"): reading it overflowed the stack and failed the whole run.
    @Test
    void parse_replyCutOffInsideAVeryLongRepeatedEntry_failsTheCutIdsWithoutOverflowing() {
        final String cut = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1 + "\"},{\"id\":\"2\",\"target\":\""
                + "у сільському ".repeat(1_000);

        final BatchReply reply = parse(cut);

        assertThat(reply.acceptedIds()).containsExactly("1");
        assertThat(reply.failingIds()).containsExactly("2", "3", "4");
    }

    @Test
    void parse_entriesOutOfOrder_areStillReadById() {
        final List<Map.Entry<String, String>> reversed = new java.util.ArrayList<>(clean().entrySet());
        java.util.Collections.reverse(reversed);

        final BatchReply reply = parse(write(reversed));

        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void parse_jsonWithNumericIdsAndTermsField_readsIdsAndIgnoresTerms() {
        final String reply = "{\"items\":[{\"id\":1,\"target\":\"" + T1 + "\",\"terms\":[\"x\"]},"
                + "{\"id\":2,\"target\":\"" + T2 + "\"},{\"id\":3,\"target\":\"" + T3 + "\"},"
                + "{\"id\":4,\"target\":\"" + T4 + "\"}]}";

        assertThat(parse(reply).acceptedIds()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void parse_jsonBareArray_isRead() {
        final String reply = "[{\"id\":\"1\",\"target\":\"" + T1 + "\"}]";

        assertThat(parse(reply).acceptedIds()).containsExactly("1");
    }

    @Test
    void parse_plainTextNoteMarkerDropped_failsThatItemWithMarkers() {
        final List<BatchItem> items = List.of(
                new BatchItem("1", "Gravity is identical everywhere on Earth.[3] The survey proved it wrong."),
                new BatchItem("2", "He said nothing for a long while."));
        final String reply =
                write(Map.of("1", "Сила тяжіння однакова всюди на Землі. Дослідження довело, що це не так.", "2", T2));

        final BatchReply parsed = PARSER.parse(reply, items, "en", "uk");

        assertThat(parsed.failingIds()).containsExactly("1");
        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.problems()).containsExactly(ItemProblem.MARKERS));
    }

    @Test
    void parse_plainTextNoteMarkerKept_isAccepted() {
        final List<BatchItem> items =
                List.of(new BatchItem("1", "Gravity is identical everywhere on Earth.[3] The survey proved it wrong."));
        final String reply =
                write(Map.of("1", "Сила тяжіння однакова всюди на Землі.[3] Дослідження довело, що це не так."));

        assertThat(PARSER.parse(reply, items, "en", "uk").acceptedIds()).containsExactly("1");
    }

    @Test
    void parse_longItemCopiedUnchanged_failsThatItemAsAnEcho() {
        final List<BatchItem> items = List.of(
                new BatchItem("1", "Gravitas omnia trahit, sed nemo videt."),
                new BatchItem("2", "He said nothing for a long while."));
        final String reply = write(Map.of("1", "Gravitas omnia trahit, sed nemo videt.", "2", T2));

        final BatchReply parsed = PARSER.parse(reply, items, "en", "uk");

        assertThat(parsed.failingIds()).containsExactly("1");
        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.problems()).contains(ItemProblem.ECHO));
    }

    @Test
    void parse_shortItemCopiedUnchanged_isAccepted() {
        final List<BatchItem> items = List.of(new BatchItem("1", "Hello, Nell."), new BatchItem("2", "1881"));
        final String reply = write(Map.of("1", "Hello, Nell.", "2", "1881"));

        assertThat(PARSER.parse(reply, items, "en", "uk").acceptedIds()).containsExactly("1", "2");
    }

    @Test
    void parse_termsObjectOnAnItem_isKeptOnThatOutcomeAsWritten() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1
                + "\",\"terms\":{\"door\":\"двері\"}},{\"id\":\"2\",\"target\":\"" + T2 + "\"}]}";

        final BatchReply parsed = PARSER.parse(reply, ITEMS.subList(0, 2), "en", "uk");

        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.terms()).containsExactly(Map.entry("door", "двері")));
        assertThat(parsed.outcome("2"))
                .hasValueSatisfying(o -> assertThat(o.terms()).isEmpty());
        assertThat(parsed.acceptedIds()).containsExactly("1", "2");
    }

    @Test
    void parse_termsListOfPairs_isReadLikeTheObject() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1
                + "\",\"terms\":[{\"source\":\"door\",\"target\":\"двері\"}]}]}";

        final BatchReply parsed = PARSER.parse(reply, ITEMS.subList(0, 1), "en", "uk");

        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.terms()).containsExactly(Map.entry("door", "двері")));
    }

    @Test
    void parse_termsThatAreNotText_areIgnoredAndTheItemStillCounts() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1
                + "\",\"terms\":{\"door\":7,\"\":\"x\",\"lock\":\" \"}}]}";

        final BatchReply parsed = PARSER.parse(reply, ITEMS.subList(0, 1), "en", "uk");

        assertThat(parsed.outcome("1"))
                .hasValueSatisfying(o -> assertThat(o.terms()).isEmpty());
        assertThat(parsed.acceptedIds()).containsExactly("1");
    }

    @Test
    void parse_targetHoldingTheTermsTail_failsThatIdAsLeaked() {
        final Map<String, String> entries = clean();
        entries.put("2", "Він довго нічого не казав.» «terms»: {«old»: «старий»}}, {");

        final BatchReply reply = parse(write(entries));

        assertThat(reply.failingIds()).containsExactly("2");
        assertThat(reply.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.problems()).containsExactly(ItemProblem.LEAKED));
    }

    @Test
    void parse_targetHoldingACodeFence_failsThatIdAsLeaked() {
        final Map<String, String> entries = clean();
        entries.put("1", T1 + "\\n```");

        assertThat(parse(write(entries)).failingIds()).containsExactly("1");
    }

    @Test
    void parse_targetWithTheWordTermsAndBraces_isAccepted() {
        final Map<String, String> entries = clean();
        entries.put("2", "Він довго казав «terms» і {щось} ще.");

        assertThat(parse(write(entries)).isClean()).isTrue();
    }

    @Test
    void parse_brokenJsonWithLeakedTail_doesNotSwallowTheRestIntoTheTarget() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"" + T1
                + "\"},{\"id\":\"2\",\"target\":\"Він довго нічого не казав.\"},"
                + "{\"id\":\"3\",\"target\":\"1881\"},{\"id\":\"4\",\"target\":\"" + T4 + "\"";

        final BatchReply parsed = parse(reply);

        assertThat(parsed.outcome("2"))
                .hasValueSatisfying(outcome -> assertThat(outcome.target()).isEqualTo(T2));
    }
}
