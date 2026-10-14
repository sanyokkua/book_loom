package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The batch call in each arm of the format (15i.X1), against a model scripted at the call seam: the marked arm sends
 * no schema and reads its plain-text reply by markers, and the missing-ids re-ask, the cut flag and the adaptive size
 * work on it as on the JSON reply.
 */
class BatchFormatDrafterTest {

    private static final List<BatchItem> FOUR = BatchDrafterTest.FOUR;
    private static final List<String> IDS = BatchDrafterTest.FOUR_IDS;
    private static final List<String> SOURCES = BatchDrafterTest.FOUR_SOURCES;
    private static final String T1 = "Вона відчинила двері.";
    private static final String T2 = "Він мовчав.";
    private static final String T3 = "Дощ припинився.";
    private static final String T4 = "Настала ніч.";

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<List<String>> segments = new ArrayList<>();

    private BatchDrafter drafter(final BatchFormat format, final List<Result<ChatResponse>> answers) {
        final ModelCalls scripted = new ModelCalls() {
            private int next;

            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                requests.add(request);
                return answers.get(next++);
            }

            @Override
            public Result<ChatResponse> callAbout(
                    final CallKind kind, final List<String> segmentIds, final ChatRequest request) {
                segments.add(segmentIds);
                requests.add(request);
                return answers.get(next++);
            }
        };
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        return new BatchDrafter(
                new BatchPromptBuilder(new PromptTemplates(), frame, format, false),
                new BatchReplyParser(new ObjectMapper()),
                scripted,
                "en",
                "uk",
                8);
    }

    private static Result<ChatResponse> reply(final String content) {
        return BatchDrafterTest.reply(content);
    }

    private static BatchReply first(final BatchDrafter drafter) {
        return BatchDrafterTest.firstReply(drafter);
    }

    private static String user(final ChatRequest request) {
        return request.messages().get(1).content();
    }

    @Test
    void draft_markersFormat_sendsNoSchemaAndAcceptsTheMarkedReply() {
        final BatchDrafter drafter = drafter(
                BatchFormat.MARKERS,
                List.of(reply("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4)));

        final BatchReply reply = first(drafter);

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.responseFormat()).isNull();
            assertThat(user(request)).containsSubsequence("⟦s1⟧ " + SOURCES.get(0), "⟦s4⟧ " + SOURCES.get(3));
        });
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(reply.outcome("3").orElseThrow().target()).isEqualTo(T3);
    }

    // What a caller that only needs the request (a prompt eval) builds is what the run sends.
    @Test
    void request_markersFormat_isTheRequestDraftSends() {
        final BatchDrafter drafter = drafter(BatchFormat.MARKERS, List.of(reply("⟦s1⟧ " + T1)));

        first(drafter);

        assertThat(requests.getFirst().responseFormat()).isNull();
        assertThat(drafter.request(BatchContext.empty(), FOUR)).isEqualTo(requests.getFirst());
    }

    @Test
    void draft_dualFormat_asksForTheSchemaAndReadsJson() {
        final BatchDrafter drafter =
                drafter(BatchFormat.DUAL, List.of(reply(BatchDrafterTest.entries("1", T1, "2", T2, "3", T3, "4", T4))));

        final BatchReply reply = first(drafter);

        assertThat(requests.getFirst().responseFormat()).isNotNull();
        assertThat(user(requests.getFirst())).contains("<s id=\"1\">" + SOURCES.get(0) + "</s>");
        assertThat(reply.acceptedIds()).containsExactly("1", "2", "3", "4");
    }

    // IF the model answers a marked passage in JSON anyway, THEN nothing is read and every id falls back.
    @Test
    void draft_markersFormatAnsweredInJson_isUnreadable() {
        final BatchDrafter drafter =
                drafter(BatchFormat.MARKERS, List.of(reply(BatchDrafterTest.entries("1", T1, "2", T2))));

        final BatchReply reply = first(drafter);

        assertThat(reply.readable()).isFalse();
        assertThat(reply.failingIds()).containsExactly("1", "2", "3", "4");
    }

    // IF the reply stops after item 2, THEN the re-ask sends items 3 and 4 only, each at its own marker, no schema.
    @Test
    void reaskMissing_markersFormatReplyEndingEarly_asksOnlyTheMissingMarkersAndMergesThem() {
        final BatchDrafter drafter = drafter(
                BatchFormat.MARKERS,
                List.of(reply("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2), reply("⟦s3⟧ " + T3 + "\n\n⟦s4⟧ " + T4)));
        final BatchReply first = first(drafter);

        final BatchReply merged = BatchDrafterTest.reasked(drafter, first);

        assertThat(segments).containsExactly(IDS, List.of("s3", "s4"));
        assertThat(requests.get(1).responseFormat()).isNull();
        assertThat(user(requests.get(1)))
                .contains("⟦s3⟧ " + SOURCES.get(2), "⟦s4⟧ " + SOURCES.get(3))
                .doesNotContain("⟦s1⟧", "⟦s2⟧");
        assertThat(merged.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(merged.outcome("4").orElseThrow().target()).isEqualTo(T4);
    }

    // IF one marker is doubled and another is gone, THEN both are missing and are asked again in one call.
    @Test
    void reaskMissing_markersFormatDoubledAndOmittedMarkers_asksBothTogether() {
        final BatchDrafter drafter = drafter(
                BatchFormat.MARKERS,
                List.of(
                        reply("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s4⟧ " + T4),
                        reply("⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3)));
        final BatchReply first = first(drafter);

        final BatchReply merged = BatchDrafterTest.reasked(drafter, first);

        assertThat(first.failingIds()).containsExactly("2", "3");
        assertThat(segments).containsExactly(IDS, List.of("s2", "s3"));
        assertThat(merged.acceptedIds()).containsExactly("1", "2", "3", "4");
    }

    // IF only one marker is missing, THEN no call is made for it: its single draft costs the same.
    @Test
    void reaskMissing_markersFormatOneMarkerMissingAtTheEnd_makesNoCall() {
        final BatchDrafter drafter =
                drafter(BatchFormat.MARKERS, List.of(reply("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s3⟧ " + T3)));
        final BatchReply first = first(drafter);

        final BatchReply merged = BatchDrafterTest.reasked(drafter, first);

        assertThat(merged).isSameAs(first);
        assertThat(requests).hasSize(1);
        assertThat(merged.failingIds()).containsExactly("4");
    }

    // IF the marked reply hit the length cap, THEN it is cut, which the adaptive size reads as no signal.
    @Test
    void draft_markersFormatCutAtTheLengthCap_isCutAndKeepsTheSize() {
        final BatchDrafter drafter = drafter(
                BatchFormat.MARKERS,
                List.of(Result.ok(new ChatResponse("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2, FinishReason.LENGTH))));

        final BatchReply reply = first(drafter);
        drafter.record(reply);

        assertThat(reply.cut()).isTrue();
        assertThat(drafter.size()).isEqualTo(8);
    }

    // IF the marked reply drops a middle item with a normal stop, THEN the batch halves, as for a JSON reply.
    @Test
    void record_markersFormatReplyDroppingAMiddleItem_halvesTheSize() {
        final BatchDrafter drafter =
                drafter(BatchFormat.MARKERS, List.of(reply("⟦s1⟧ " + T1 + "\n\n⟦s2⟧ " + T2 + "\n\n⟦s4⟧ " + T4)));

        final BatchReply reply = first(drafter);
        drafter.record(reply);

        assertThat(reply.acceptedIds()).containsExactly("1", "2", "4");
        assertThat(drafter.size()).isEqualTo(4);
    }

    @Test
    void promptTokens_dualFormat_exceedsTheJsonFormatByThePassage() {
        final int json = drafter(BatchFormat.JSON, List.of()).promptTokens(BatchContext.empty(), FOUR);
        final int dual = drafter(BatchFormat.DUAL, List.of()).promptTokens(BatchContext.empty(), FOUR);

        assertThat(dual).isGreaterThan(json);
    }
}
