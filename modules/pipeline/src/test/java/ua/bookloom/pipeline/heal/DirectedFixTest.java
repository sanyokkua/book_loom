package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftSchema;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link DirectedFix}: the single-segment directed-fix call and its reply classification. */
class DirectedFixTest {

    private static final DirectedFix FIX =
            new DirectedFix(new PromptTemplates(), new DraftReplyParser(new ObjectMapper()));
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private static final String THREE_TOKEN_SOURCE = "⟦g0⟧ ⟦g1⟧ ⟦g2⟧ items remain.";
    private static final String THREE_TOKEN_TARGET = "Залишаються ⟦g0⟧ ⟦g1⟧ ⟦g2⟧ предмети.";
    private static final String EXPECTED_THREE_TOKENS = "⟦g0⟧ ⟦g1⟧ ⟦g2⟧";

    private static final String ECHO_SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";
    private static final String ECHO_REJECTED_TARGET = "HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.";
    private static final int ECHO_SOURCE_ALLOWANCE = 16;

    private static final String REFUSAL_SOURCE = "He opened the old door.";
    private static final String REFUSAL_REJECTED_TARGET = "I'm sorry, but I can't translate this text.";

    private static final String REWRITTEN_TARGET = "Він пішов.";
    private static final String TARGET_ONLY_REPLY = "{\"target\":\"Він пішов.\"}";
    private static final String SEGMENTS_ARRAY_REPLY =
            "{\"segments\":[{\"id\":\"ch07.xhtml:41\",\"target\":\"Він пішов.\"}]}";
    private static final String BLANK_TARGET_REPLY = "{\"target\":\"  \"}";
    private static final String EMPTY_SEGMENTS_REPLY = "{\"segments\":[]}";
    private static final String SPACES_ONLY_CONTENT = "   ";
    private static final String LENGTH_CUT_CONTENT = "{\"target\":\"Він п";

    private static final QaFinding PLACEHOLDER_FINDING =
            new QaFinding("markup", Severity.HIGH, "placeholder token mismatch", "placeholder");
    private static final QaFinding ECHO_FINDING =
            new QaFinding("language", Severity.MEDIUM, "echoes the source untranslated", "echo");
    private static final QaFinding REFUSAL_FINDING =
            new QaFinding("meaning", Severity.HIGH, "the model refused to translate", "refusal");

    @Test
    void fix_placeholderFinding_putsExpectedTokenSequenceInUserMessage() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(
                segment("Book.md:0"),
                FRAME,
                THREE_TOKEN_SOURCE,
                THREE_TOKEN_TARGET,
                List.of(PLACEHOLDER_FINDING),
                calls(model));

        assertThat(userMessageOf(model)).contains(EXPECTED_THREE_TOKENS);
    }

    @Test
    void fix_echoFinding_textBlockHoldsOnlyRejectedTargetWithSourceUnderSourceLabel() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls(model));

        final String user = userMessageOf(model);
        assertThat(TextBlockAssertions.textBlockCount(user)).isEqualTo(1);
        assertThat(TextBlockAssertions.textBlockOf(user)).isEqualTo(ECHO_REJECTED_TARGET);
        assertThat(user).contains("[Source]").contains(ECHO_SOURCE);
    }

    @Test
    void fix_refusalFinding_textBlockHoldsSourceNotRejectedTarget() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(
                segment("Book.md:0"),
                FRAME,
                REFUSAL_SOURCE,
                REFUSAL_REJECTED_TARGET,
                List.of(REFUSAL_FINDING),
                calls(model));

        final String user = userMessageOf(model);
        assertThat(TextBlockAssertions.textBlockOf(user))
                .isEqualTo(REFUSAL_SOURCE)
                .doesNotContain(REFUSAL_REJECTED_TARGET);
    }

    @Test
    void fix_anyCall_responseFormatRequiresSingleNonblankTargetSchema() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls(model));

        final ResponseFormat format =
                Objects.requireNonNull(model.requests().getFirst().responseFormat());
        assertThat(format.name()).isEqualTo("directed-fix");
        assertThat(format.jsonSchema()).isEqualTo(DraftSchema.SCHEMA);
    }

    @Test
    void fix_replyWithSegmentsArray_isMalformed() {
        final Result<RepairReply> result = fixEcho(readable(SEGMENTS_ARRAY_REPLY));

        assertThat(result.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    @Test
    void fix_replyWithTargetOnly_returnsRewritten() {
        final Result<RepairReply> result = fixEcho(readable(TARGET_ONLY_REPLY));

        assertThat(result.data()).isEqualTo(new RepairReply.Rewritten(REWRITTEN_TARGET));
    }

    @Test
    void fix_replyContentOnlySpaces_flagsEmptyCompletion() {
        final Result<RepairReply> result = fixEcho(readable(SPACES_ONLY_CONTENT));

        assertThat(result.data())
                .isInstanceOfSatisfying(
                        RepairReply.FlagNow.class,
                        flag -> assertThat(flag.error().code()).isEqualTo(ErrorCode.emptyCompletion));
    }

    @Test
    void fix_replyCutOffByLength_flagsValidation() {
        final Result<RepairReply> result = fixEcho(readable(LENGTH_CUT_CONTENT, FinishReason.LENGTH));

        assertThat(result.data())
                .isInstanceOfSatisfying(
                        RepairReply.FlagNow.class,
                        flag -> assertThat(flag.error().code()).isEqualTo(ErrorCode.validation));
    }

    @Test
    void fix_replyTargetBlank_isMalformed() {
        final Result<RepairReply> result = fixEcho(readable(BLANK_TARGET_REPLY));

        assertThat(result.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    @Test
    void fix_replySegmentsEmptyArray_isMalformed() {
        final Result<RepairReply> result = fixEcho(readable(EMPTY_SEGMENTS_REPLY));

        assertThat(result.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    @Test
    void fix_modelAnswersContextWindow_flagsContextWindow() {
        final Result<RepairReply> result =
                fixEcho(Result.err(AppError.of(ErrorCode.contextWindow, "Context window exceeded", "too long")));

        assertThat(result.data())
                .isInstanceOfSatisfying(
                        RepairReply.FlagNow.class,
                        flag -> assertThat(flag.error().code()).isEqualTo(ErrorCode.contextWindow));
    }

    @Test
    void fix_modelAnswersUnreachable_returnsThatError() {
        final Result<RepairReply> result =
                fixEcho(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")));

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void fix_anyCall_sendsTemperaturePointTwoAndSegmentsOutputAllowance() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls(model));

        final ChatRequest request = model.requests().getFirst();
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.expectedOutputTokens()).isEqualTo(ECHO_SOURCE_ALLOWANCE);
    }

    @Test
    void fix_anyCall_systemMessageHoldsStyleSheetAndForeignPassageRule() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(TARGET_ONLY_REPLY));

        FIX.fix(segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls(model));

        final String system = model.requests().getFirst().messages().getFirst().content();
        assertThat(system)
                .contains(FRAME.styleSheet().text())
                .contains(StyleSheet.foreignPassageRule(FRAME.foreignPassagePolicy(), "English (en)"));
    }

    @Test
    void fix_echoCaseWithRealPseudoModel_returnsRewrittenUppercaseEcho() {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());
        final ModelCalls calls = (kind, segmentId, request) -> pseudo.chat(request);

        final Result<RepairReply> result =
                FIX.fix(segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls);

        assertThat(result.data()).isEqualTo(new RepairReply.Rewritten(ECHO_REJECTED_TARGET));
    }

    private static Result<RepairReply> fixEcho(final Result<ChatResponse> answer) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(answer);
        return FIX.fix(
                segment("Book.md:0"), FRAME, ECHO_SOURCE, ECHO_REJECTED_TARGET, List.of(ECHO_FINDING), calls(model));
    }

    private static String userMessageOf(final ScriptedChatModel model) {
        return model.requests().getFirst().messages().get(1).content();
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return readable(content, FinishReason.STOP);
    }

    private static Result<ChatResponse> readable(final String content, final FinishReason finish) {
        return Result.ok(new ChatResponse(content, finish));
    }

    private static Segment segment(final String id) {
        return new Segment(
                id,
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                ECHO_SOURCE,
                ECHO_SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, ECHO_SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
