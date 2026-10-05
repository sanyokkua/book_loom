package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
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
}
