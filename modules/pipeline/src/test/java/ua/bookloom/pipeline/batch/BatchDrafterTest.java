package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
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

/** The batch call and the adaptive size it keeps, against a model scripted at the call seam. */
class BatchDrafterTest {

    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door and looked outside."),
            new BatchItem("2", "He said nothing for a long while."));
    private static final String CLEAN =
            "{\"items\":[{\"id\":\"1\",\"target\":\"Вона відчинила двері й визирнула надвір.\"},"
                    + "{\"id\":\"2\",\"target\":\"Він довго нічого не казав.\"}]}";

    private final List<CallKind> kinds = new ArrayList<>();
    private final List<List<String>> segments = new ArrayList<>();

    private BatchDrafter drafter(final Result<ChatResponse> answer, final int initialSize) {
        final ModelCalls recording = new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return answer;
            }

            @Override
            public Result<ChatResponse> callAbout(
                    final CallKind kind, final List<String> segmentIds, final ChatRequest request) {
                kinds.add(kind);
                segments.add(segmentIds);
                return answer;
            }
        };
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        return new BatchDrafter(
                new BatchPromptBuilder(new PromptTemplates(), frame),
                new BatchReplyParser(new ObjectMapper()),
                recording,
                "en",
                "uk",
                initialSize);
    }

    private static BatchReply drafted(final BatchDrafter drafter, final List<BatchItem> items) {
        return Objects.requireNonNull(
                drafter.draft(BatchContext.empty(), items, List.of("a", "b")).data(), "reply");
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    @Test
    void draft_cleanReply_announcesOneDraftCallAboutEverySegmentAndAcceptsBoth() {
        final BatchDrafter drafter = drafter(reply(CLEAN), 8);

        final Result<BatchReply> result = drafter.draft(BatchContext.empty(), ITEMS, List.of("Book.md:0", "Book.md:1"));

        assertThat(kinds).containsExactly(CallKind.DRAFT);
        assertThat(segments).containsExactly(List.of("Book.md:0", "Book.md:1"));
        assertThat(Objects.requireNonNull(result.data()).acceptedIds()).containsExactly("1", "2");
    }

    private static final List<BatchItem> FOUR = List.of(
            new BatchItem("1", "She opened the door."),
            new BatchItem("2", "He said nothing."),
            new BatchItem("3", "The rain stopped."),
            new BatchItem("4", "Night fell."));
    private static final List<String> FOUR_IDS = List.of("s1", "s2", "s3", "s4");
    private static final List<String> FOUR_SOURCES =
            List.of("She opened the door.", "He said nothing.", "The rain stopped.", "Night fell.");

    private BatchDrafter drafterAnswering(final List<Result<ChatResponse>> answers) {
        final ModelCalls scripted = new ModelCalls() {
            private int next;

            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return answers.get(next++);
            }

            @Override
            public Result<ChatResponse> callAbout(
                    final CallKind kind, final List<String> segmentIds, final ChatRequest request) {
                segments.add(segmentIds);
                return answers.get(next++);
            }
        };
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        return new BatchDrafter(
                new BatchPromptBuilder(new PromptTemplates(), frame),
                new BatchReplyParser(new ObjectMapper()),
                scripted,
                "en",
                "uk",
                8);
    }

    private static String entries(final String... pairs) {
        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parts.add("{\"id\":\"" + pairs[i] + "\",\"target\":\"" + pairs[i + 1] + "\"}");
        }
        return "{\"items\":[" + String.join(",", parts) + "]}";
    }

    private BatchReply firstReply(final BatchDrafter drafter) {
        return Objects.requireNonNull(
                drafter.draft(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null)
                        .data(),
                "reply");
    }

    @Test
    void reaskMissing_twoIdsLeftOut_asksOnlyThoseAndMergesTheirAnswers() {
        final BatchDrafter drafter = drafterAnswering(List.of(
                reply(entries("1", "Вона відчинила двері.", "4", "Настала ніч.")),
                reply(entries("2", "Він мовчав.", "3", "Дощ припинився."))));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = drafter.reaskMissing(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null, first);

        assertThat(segments).containsExactly(FOUR_IDS, List.of("s2", "s3"));
        assertThat(merged.acceptedIds()).containsExactly("1", "2", "3", "4");
        assertThat(first.failingIds()).containsExactly("2", "3");
    }

    @Test
    void reaskMissing_oneIdLeftOut_makesNoCall() {
        final BatchDrafter drafter = drafterAnswering(
                List.of(reply(entries("1", "Вона відчинила двері.", "2", "Він мовчав.", "4", "Настала ніч."))));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = drafter.reaskMissing(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null, first);

        assertThat(merged).isSameAs(first);
        assertThat(segments).hasSize(1);
    }

    @Test
    void reaskMissing_unreadableFirstReply_makesNoCall() {
        final BatchDrafter drafter = drafterAnswering(List.of(reply("I cannot help with that.")));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = drafter.reaskMissing(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null, first);

        assertThat(merged).isSameAs(first);
        assertThat(segments).hasSize(1);
    }

    @Test
    void reaskMissing_secondCallFails_keepsTheFirstReply() {
        final AppError error = AppError.of(ErrorCode.unreachable, "Provider unreachable", "No answer.");
        final BatchDrafter drafter =
                drafterAnswering(List.of(reply(entries("1", "Вона відчинила двері.")), Result.err(error)));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = drafter.reaskMissing(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null, first);

        assertThat(merged).isSameAs(first);
    }

    @Test
    void reaskMissing_secondReplyNamesAnotherId_ignoresThatId() {
        final BatchDrafter drafter = drafterAnswering(List.of(
                reply(entries("1", "Вона відчинила двері.", "4", "Настала ніч.")),
                reply(entries("2", "Він мовчав.", "9", "Зайве."))));
        final BatchReply first = firstReply(drafter);

        final BatchReply merged = drafter.reaskMissing(BatchContext.empty(), FOUR, FOUR_IDS, FOUR_SOURCES, null, first);

        assertThat(merged.acceptedIds()).containsExactly("1", "2", "4");
        assertThat(merged.failingIds()).containsExactly("3");
        assertThat(merged.count(ItemStatus.EXTRA)).isZero();
    }

    @Test
    void draft_blankReply_isUnreadableSoEveryItemFallsBack() {
        final BatchDrafter drafter = drafter(reply("  "), 8);

        final BatchReply reply = drafted(drafter, ITEMS);

        assertThat(reply.readable()).isFalse();
        assertThat(reply.failingIds()).containsExactly("1", "2");
    }

    @Test
    void draft_callError_isReturnedForTheRunToRoute() {
        final AppError error = AppError.of(ErrorCode.unreachable, "Provider unreachable", "No answer.");
        final BatchDrafter drafter = drafter(Result.err(error), 8);

        final Result<BatchReply> result = drafter.draft(BatchContext.empty(), ITEMS, List.of("a", "b"));

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void record_replyMissingAnId_halvesTheSize() {
        final BatchDrafter drafter = drafter(
                reply("{\"items\":[{\"id\":\"1\",\"target\":\"Вона відчинила двері й визирнула надвір.\"}]}"), 8);
        final BatchReply reply = drafted(drafter, ITEMS);

        drafter.record(reply);

        assertThat(drafter.size()).isEqualTo(4);
    }

    @Test
    void record_threeCleanRepliesInARow_growTheSizeByOne() {
        final BatchDrafter drafter = drafter(reply(CLEAN), 8);
        final BatchReply clean = drafted(drafter, ITEMS);

        drafter.record(clean);
        drafter.record(clean);
        assertThat(drafter.size()).isEqualTo(8);
        drafter.record(clean);

        assertThat(drafter.size()).isEqualTo(9);
    }

    @Test
    void record_itemThatOnlyFailsItsTokenCheck_doesNotHalveTheSize() {
        final String tokenless = "{\"items\":[{\"id\":\"1\",\"target\":\"Вона відчинила двері й визирнула надвір.\"},"
                + "{\"id\":\"2\",\"target\":\"Він довго нічого не казав.\"}]}";
        final List<BatchItem> withTokens =
                List.of(ITEMS.getFirst(), new BatchItem("2", "He ⟦g0⟧said⟦g1⟧ nothing for a long while."));
        final BatchDrafter drafter = drafter(reply(tokenless), 8);
        final BatchReply reply = drafted(drafter, withTokens);

        drafter.record(reply);

        assertThat(reply.failingIds()).containsExactly("2");
        assertThat(drafter.size()).isEqualTo(8);
    }

    @Test
    void size_startingAtOne_growsOnlyAfterACleanStreak() {
        final BatchDrafter drafter = drafter(reply(CLEAN), BatchDrafter.NO_BATCHING);
        final BatchReply clean = drafted(drafter, ITEMS);

        drafter.record(clean);
        drafter.record(clean);
        drafter.record(clean);

        assertThat(drafter.size()).isEqualTo(2);
    }

    @Test
    void record_manyFailuresInARun_neverTakeABatchingRunBelowTwo() {
        final BatchDrafter drafter = drafter(reply("  "), 8);
        final BatchReply unreadable = drafted(drafter, ITEMS);

        drafter.record(unreadable);
        drafter.record(unreadable);
        drafter.record(unreadable);
        drafter.record(unreadable);

        assertThat(drafter.size()).isEqualTo(BatchDrafter.MIN_BATCHING_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void record_failureAtTheMinimum_keepsTheSize(final int initial) {
        final BatchDrafter drafter = drafter(reply("  "), initial);

        drafter.record(drafted(drafter, ITEMS));

        assertThat(drafter.size()).isEqualTo(initial);
    }
}
