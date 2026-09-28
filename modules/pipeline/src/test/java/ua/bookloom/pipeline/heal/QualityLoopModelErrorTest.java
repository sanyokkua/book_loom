package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

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
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link ChunkDecider}/{@link SegmentHealer}: every model or gate call a self-heal round makes can end the step
 * with an error other than one the design already routes to a flag — a self-heal call, a re-judge or a gate call
 * answering something other than {@code emptyCompletion}/{@code contextWindow}/{@code validation} (I1, I3).
 */
class QualityLoopModelErrorTest {

    private static final GateFunction PASSTHROUGH_GATE = (segment, maskedTarget) -> Result.ok(maskedTarget);
    private static final GateFunction ALWAYS_INTERNAL_ERROR =
            (segment, maskedTarget) -> Result.err(AppError.of(ErrorCode.internal, "Gate exploded", "boom"));
    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // I1: a directed fix answering unreachable ends the step with that error, index unchanged; a retried
    // nextDecision() call — with the model now able to answer — redoes the very same segment rather than skipping
    // it.
    @Test
    void nextDecision_directedFixAnswersUnreachable_retryRedoesTheSameSegment() {
        final String echo = SOURCE.toUpperCase(Locale.ROOT);
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(segment(), SOURCE, List.of(), echo, echo, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")))
                .answer(readable(targetReply(GOOD_TARGET)));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED,
                new DialParameters(1, 1, false, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(outcome), settings, PASSTHROUGH_GATE, calls(model))
                        .data());

        final Result<SegmentOutcome> first = decider.nextDecision();
        assertThat(first.isErr()).isTrue();
        assertThat(Objects.requireNonNull(first.error()).code()).isEqualTo(ErrorCode.unreachable);
        assertThat(decider.hasNext()).isTrue();

        final Result<SegmentOutcome> retried = decider.nextDecision();
        assertThat(retried.isOk()).isTrue();
        assertThat(Objects.requireNonNull(retried.data()).segmentId())
                .isEqualTo(outcome.segment().id());
        assertThat(decider.hasNext()).isFalse();
    }

    // I3: a re-judge answering unreachable ends the step.
    @Test
    void nextDecision_rejudgeAnswersUnreachable_endsTheStepWithThatError() {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), GOOD_TARGET, GOOD_TARGET, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"score\":0.60,\"verdict\":\"revise\",\"findings\":[],\"deferrals\":[]}"))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(GOOD_TARGET)))
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(2, 1, true, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(outcome), settings, PASSTHROUGH_GATE, calls(model))
                        .data());

        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(decision.isErr()).isTrue();
        assertThat(Objects.requireNonNull(decision.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    // I3: a gate failure that is not `validation` ends the step rather than wasting the round.
    @Test
    void nextDecision_gateAnswersANonValidationError_endsTheStepWithThatError() {
        final String echo = SOURCE.toUpperCase(Locale.ROOT);
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(segment(), SOURCE, List.of(), echo, echo, null);
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(GOOD_TARGET)));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED,
                new DialParameters(1, 1, false, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(outcome), settings, ALWAYS_INTERNAL_ERROR, calls(model))
                        .data());

        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(decision.isErr()).isTrue();
        assertThat(Objects.requireNonNull(decision.error()).code()).isEqualTo(ErrorCode.internal);
    }

    // I3: a reflect call answering unreachable ends the step before improve is ever attempted.
    @Test
    void nextDecision_reflectAnswersUnreachable_endsTheStepWithThatError() {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), GOOD_TARGET, GOOD_TARGET, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"score\":0.60,\"verdict\":\"revise\",\"findings\":[],\"deferrals\":[]}"))
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(2, 1, true, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider =
                Objects.requireNonNull(loop.start(List.of(outcome), settings, PASSTHROUGH_GATE, calls(model))
                        .data());

        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(decision.isErr()).isTrue();
        assertThat(Objects.requireNonNull(decision.error()).code()).isEqualTo(ErrorCode.unreachable);
        assertThat(model.requests()).hasSize(2);
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

    private static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
