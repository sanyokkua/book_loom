package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link ReflectImprove}: the reflect critique's tolerant reading and the improve rewrite that consumes it. */
class ReflectImproveTest {

    private static final ReflectImprove REFLECT_IMPROVE =
            new ReflectImprove(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()), new ObjectMapper());
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static final String SOURCE = "She walked into the room.";
    private static final String CANDIDATE = "Вона зайшла в кімнату.";
    private static final String IMPROVED_TARGET = "Вона тихо зайшла в кімнату.";
    private static final String IMPROVE_TARGET_REPLY = "{\"target\":\"Вона тихо зайшла в кімнату.\"}";
    private static final String SEGMENTS_ARRAY_REPLY = "{\"segments\":[{\"id\":\"ch07.xhtml:41\",\"target\":\"x\"}]}";

    @Test
    void reflect_stringIssue_readsItVerbatimAndImproveMessageHoldsIt() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"issues\":[\"drops the second clause\"]}"))
                .answer(readable(IMPROVE_TARGET_REPLY));

        final Result<List<String>> reflected =
                REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));
        assertThat(reflected.data()).containsExactly("drops the second clause");

        REFLECT_IMPROVE.improve(
                segment(), FRAME, SOURCE, CANDIDATE, Objects.requireNonNull(reflected.data()), calls(model));

        assertThat(model.requests().get(1).messages().get(1).content()).contains("drops the second clause");
    }

    @Test
    void reflect_emptyObjectReply_givesNoIssuesAndImproveStillRuns() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{}")).answer(readable(IMPROVE_TARGET_REPLY));

        final Result<List<String>> reflected =
                REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));
        assertThat(reflected.data()).isEmpty();

        final Result<RepairReply> improved = REFLECT_IMPROVE.improve(
                segment(), FRAME, SOURCE, CANDIDATE, Objects.requireNonNull(reflected.data()), calls(model));

        assertThat(improved.data()).isEqualTo(new RepairReply.Rewritten(IMPROVED_TARGET));
    }

    @Test
    void reflect_objectIssue_readsNoteAndSuggestionJoinedByDash() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(
                        "{\"issues\":[{\"note\":\"drops the second clause\",\"suggestion\":\"restore it\"}]}"));

        final Result<List<String>> reflected =
                REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        assertThat(reflected.data()).containsExactly("drops the second clause — restore it");
    }

    @Test
    void reflect_anyCall_systemMessageHoldsStyleSheet() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable("{\"issues\":[]}"));

        REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains(FRAME.styleSheet().text());
    }

    @Test
    void reflect_anyCall_textBlockHoldsOnlyCandidateTarget() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable("{\"issues\":[]}"));

        REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        final String user = model.requests().getFirst().messages().get(1).content();
        assertThat(TextBlockAssertions.textBlockCount(user)).isEqualTo(1);
        assertThat(TextBlockAssertions.textBlockOf(user)).isEqualTo(CANDIDATE);
        assertThat(user).contains("<Source>\n" + SOURCE);
    }

    @Test
    void reflect_anyCall_statesItsShortIssueListLimit() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable("{\"issues\":[]}"));

        REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls(model));

        assertThat(model.requests().getFirst())
                .extracting(ChatRequest::expectedOutputTokens, ChatRequest::maxOutputTokens)
                .containsExactly(256, 600);
    }

    @Test
    void reflect_modelAnswersUnreachable_returnsThatError() {
        final Result<List<String>> reflected = REFLECT_IMPROVE.reflect(
                segment(),
                FRAME,
                SOURCE,
                CANDIDATE,
                calls(new ScriptedChatModel()
                        .answer(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")))));

        assertThat(reflected.isErr()).isTrue();
        assertThat(Objects.requireNonNull(reflected.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void reflect_realPseudoModel_returnsNoIssues() {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());
        final ModelCalls calls = (kind, segmentId, request) -> pseudo.chat(request);

        final Result<List<String>> reflected = REFLECT_IMPROVE.reflect(segment(), FRAME, SOURCE, CANDIDATE, calls);

        assertThat(reflected.data()).isEmpty();
    }

    @Test
    void improve_anyCall_systemMessageHoldsStyleSheet() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(IMPROVE_TARGET_REPLY));

        REFLECT_IMPROVE.improve(segment(), FRAME, SOURCE, CANDIDATE, List.of(), calls(model));

        assertThat(model.requests().getFirst().messages().getFirst().content())
                .contains(FRAME.styleSheet().text());
    }

    @Test
    void improve_anyCall_textBlockHoldsOnlyCandidateTarget() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(IMPROVE_TARGET_REPLY));

        REFLECT_IMPROVE.improve(segment(), FRAME, SOURCE, CANDIDATE, List.of(), calls(model));

        final String user = model.requests().getFirst().messages().get(1).content();
        assertThat(TextBlockAssertions.textBlockCount(user)).isEqualTo(1);
        assertThat(TextBlockAssertions.textBlockOf(user)).isEqualTo(CANDIDATE);
        assertThat(user).contains("<Source>\n" + SOURCE);
    }

    @Test
    void improve_anyCall_sendsTemperaturePointThreeFive() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(IMPROVE_TARGET_REPLY));

        REFLECT_IMPROVE.improve(segment(), FRAME, SOURCE, CANDIDATE, List.of(), calls(model));

        assertThat(model.requests().getFirst().temperature()).isEqualTo(0.35);
    }

    @Test
    void improve_replyWithSegmentsArray_isMalformed() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(SEGMENTS_ARRAY_REPLY));

        final Result<RepairReply> improved =
                REFLECT_IMPROVE.improve(segment(), FRAME, SOURCE, CANDIDATE, List.of(), calls(model));

        assertThat(improved.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    @Test
    void improve_realPseudoModel_returnsUppercaseCandidate() {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());
        final ModelCalls calls = (kind, segmentId, request) -> pseudo.chat(request);

        final Result<RepairReply> improved =
                REFLECT_IMPROVE.improve(segment(), FRAME, SOURCE, CANDIDATE, List.of(), calls);

        assertThat(improved.data()).isEqualTo(new RepairReply.Rewritten(CANDIDATE.toUpperCase(Locale.ROOT)));
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                SOURCE,
                SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
