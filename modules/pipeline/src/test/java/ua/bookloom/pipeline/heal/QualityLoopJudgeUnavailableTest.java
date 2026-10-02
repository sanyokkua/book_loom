package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * A judge that stalls — timed out after the provider's own retries — never holds the run: the segment keeps its latest
 * target, decided by the quality checks alone, and is flagged with a {@code judge-unavailable} finding, never accepted.
 * A provider outage is not a stall: it ends the step with its error, so the run waits for the provider.
 */
class QualityLoopJudgeUnavailableTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";
    private static final String FIXED_TARGET = "Він відчинив старі дубові двері.";
    private static final String MEANING_AT_085 = "{\"score\":0.85,\"verdict\":\"revise\",\"findings\":[{\"segmentId\":"
            + "\"s1\",\"type\":\"meaning\",\"severity\":\"medium\",\"note\":\"drops a word\"}],\"deferrals\":[]}";
    private static final DialParameters JUDGE_TWO_ROUNDS = new DialParameters(2, 2, true, false, false, 4);

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"timeout"})
    void nextDecision_rejudgeAfterAFixUnavailable_flagsTheFixedTargetWithJudgeUnavailable(final ErrorCode code) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(MEANING_AT_085))
                .answer(readable(targetReply(FIXED_TARGET)))
                .answer(Result.err(AppError.of(code, "No answer", "the judge did not answer")));

        final SegmentOutcome decided = decide(model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(FIXED_TARGET);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(Objects.requireNonNull(decided.flagReason()).code()).isEqualTo(code);
        assertThat(decided.findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple("judge-unavailable", Severity.MEDIUM, "judge"));
        assertThat(model.requests()).hasSize(3);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"timeout"})
    void nextDecision_chunkJudgeUnavailable_flagsTheDraftWithoutAnotherCall(final ErrorCode code) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.err(AppError.of(code, "No answer", "the judge did not answer")));

        final SegmentOutcome decided = decide(model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(GOOD_TARGET);
        assertThat(decided.repairRounds()).isZero();
        assertThat(decided.judgeScore()).isNull();
        assertThat(decided.findings()).extracting(QaFinding::kind).contains("judge-unavailable");
        assertThat(model.requests()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"unreachable", "upstream", "rateLimited"})
    void nextDecision_rejudgeDuringAnOutage_endsTheStepWithThatErrorInsteadOfFlagging(final ErrorCode code) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(MEANING_AT_085))
                .answer(readable(targetReply(FIXED_TARGET)))
                .answer(Result.err(AppError.of(code, "Provider down", "the provider did not answer")));

        final Result<SegmentOutcome> decision = decision(model);

        assertThat(decision.isErr()).isTrue();
        assertThat(Objects.requireNonNull(decision.error()).code()).isEqualTo(code);
    }

    private SegmentOutcome decide(final ScriptedChatModel model) {
        return Objects.requireNonNull(decision(model).data(), "decision");
    }

    private Result<SegmentOutcome> decision(final ScriptedChatModel model) {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), GOOD_TARGET, GOOD_TARGET, GOOD_TARGET, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED, JUDGE_TWO_ROUNDS, QualityLoopFixtures.FRAME, NamePolicy.TRANSLITERATE, List.of());
        final Result<ChunkDecider> started =
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model));
        return Objects.requireNonNull(started.data(), "decider").nextDecision();
    }

    static Segment segment() {
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

    static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
