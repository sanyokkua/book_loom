package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link ChunkDecider}: a {@link DraftOutcome.FlaggedAtOnce} outcome becomes FLAGGED with its error and no machine
 * target; decisions arrive one per {@code nextDecision()} call, in document order; a FLAGGED segment records its
 * last evaluation's findings and the reviewer's findings of any severity, de-duplicated
 * ({@code specs/quality-gates/spec.md} "Record each segment's findings for review and repair", "Flag a segment
 * whose reply cannot be used, and continue").
 */
class QualityLoopFlaggingAndOrderTest {

    private static final String SOURCE = "He left the house at dawn and never once looked back at the old road.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void nextDecision_flaggedAtOnceOutcome_flagsWithItsErrorAndNoMachineTarget() {
        final AppError error = AppError.of(ErrorCode.contextWindow, "Context window exceeded", "too long");
        final DraftOutcome.FlaggedAtOnce outcome =
                new DraftOutcome.FlaggedAtOnce(segment("Book.md:0"), SOURCE, List.of(), error);
        final ChunkDecider decider = start(List.of(outcome), new ScriptedChatModel());

        final Result<SegmentOutcome> decision = decider.nextDecision();

        final SegmentOutcome result = Objects.requireNonNull(decision.data());
        assertThat(result.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(result.machineTarget()).isNull();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.repairRounds()).isEqualTo(0);
        assertThat(Objects.requireNonNull(result.flagReason()).code()).isEqualTo(ErrorCode.contextWindow);
    }

    // ch05.xhtml:11 (specs/quality-gates/spec.md "A flagged segment keeps its reasons"): a draft with length ratio
    // 0.41 (a 100-character source, a 41-character Cyrillic target) is refused by the length check, so the reviewer
    // never reads it; Balanced's directed fix returns a target that still fails length, which is no progress, so no
    // second
    // round is spent. The FLAGGED record
    // carries the last evaluation's confidence and the medium omission from the length check.
    @Test
    void nextDecision_directedFixStillFailsLength_flagsAfterOneRoundWithTheLengthFindingAndNoReviewerCall() {
        final String source =
                "He left the small house at dawn and never once looked back at the old road far from the sleepy"
                        + " town.";
        assertThat(source).hasSize(100);
        final Segment segment = segment("ch05.xhtml:11", source);
        final String shortTarget = "Він покинув дім на світанку і жодного раз";
        assertThat(shortTarget).hasSize(41);
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment, source, List.of(), shortTarget, shortTarget, shortTarget, null);
        // The fix rewrites the draft but stays as short, so it still fails length and the loop stops there.
        final String shortFix = "Він покинув дах на світанку і жодного раз";
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(shortFix)));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                DialParameters.of(ua.bookloom.api.pipeline.QualityDial.BALANCED),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());

        final Result<ChunkDecider> started =
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model));
        final Result<SegmentOutcome> decision =
                Objects.requireNonNull(started.data()).nextDecision();

        assertThat(model.requests()).hasSize(1);
        final SegmentOutcome result = Objects.requireNonNull(decision.data());
        assertThat(result.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(result.confidence()).isCloseTo(0.75, within(1e-9));
        assertThat(result.judgeScore()).isNull();
        assertSoleFindingOf(result, "length", "omission", Severity.MEDIUM);
    }

    private static void assertSoleFindingOf(
            final SegmentOutcome result, final String raisedBy, final String kind, final Severity severity) {
        assertThat(result.findings())
                .filteredOn(finding -> finding.raisedBy().equals(raisedBy))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.kind()).isEqualTo(kind);
                    assertThat(finding.severity()).isEqualTo(severity);
                });
    }

    // An accepted segment keeps the low note the reviewer left on it; a style remark is never applied.
    @Test
    void nextDecision_acceptedSegment_keepsTheReviewersStyleNoteAsALowFinding() {
        final Segment segment = segment("Book.md:0");
        final String good = "Він покинув дім на світанку і жодного разу не озирнувся на стару дорогу.";
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment, SOURCE, List.of(), good, good, good, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":"
                        + "\"style\",\"quote\":\"стару дорогу\",\"replacement\":\"давню дорогу\"}]}]}"));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                DialParameters.of(ua.bookloom.api.pipeline.QualityDial.BALANCED),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());

        final Result<ChunkDecider> started =
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model));
        final Result<SegmentOutcome> decision =
                Objects.requireNonNull(started.data()).nextDecision();

        final SegmentOutcome result = Objects.requireNonNull(decision.data());
        assertThat(result.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(result.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(result.machineTarget()).isEqualTo(good);
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.LOW);
            assertThat(finding.raisedBy()).isEqualTo("reviewer");
            assertThat(finding.kind()).isEqualTo("style");
        });
    }

    @Test
    void nextDecision_calledTwiceWithFlaggedAtOnceOutcomes_decidesInDocumentOrderOnePerCall() {
        final AppError error = AppError.of(ErrorCode.emptyCompletion, "Empty model response", "no text");
        final DraftOutcome.FlaggedAtOnce first =
                new DraftOutcome.FlaggedAtOnce(segment("Book.md:0"), SOURCE, List.of(), error);
        final DraftOutcome.FlaggedAtOnce second =
                new DraftOutcome.FlaggedAtOnce(segment("Book.md:1"), SOURCE, List.of(), error);
        final ChunkDecider decider = start(List.of(first, second), new ScriptedChatModel());

        assertThat(decider.hasNext()).isTrue();
        final SegmentOutcome firstDecision =
                Objects.requireNonNull(decider.nextDecision().data());
        assertThat(decider.hasNext()).isTrue();
        final SegmentOutcome secondDecision =
                Objects.requireNonNull(decider.nextDecision().data());
        assertThat(decider.hasNext()).isFalse();

        assertThat(firstDecision.segmentId()).isEqualTo("Book.md:0");
        assertThat(secondDecision.segmentId()).isEqualTo("Book.md:1");
    }

    // Document order holds for drafted outcomes too, not only flagged-at-once ones.
    @Test
    void nextDecision_calledTwiceWithDraftedOutcomes_decidesInDocumentOrderOnePerCall() {
        final String good = "Він покинув дім на світанку і жодного разу не озирнувся на стару дорогу.";
        final DraftOutcome.Drafted first =
                new DraftOutcome.Drafted(segment("Book.md:0"), SOURCE, List.of(), good, good, good, null);
        final DraftOutcome.Drafted second =
                new DraftOutcome.Drafted(segment("Book.md:1"), SOURCE, List.of(), good, good, good, null);
        final LoopSettings settings =
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, ua.bookloom.api.pipeline.QualityDial.FAST);
        final ChunkDecider decider = Objects.requireNonNull(loop.start(
                        List.of(first, second),
                        settings,
                        QualityLoopFixtures.PASSTHROUGH_GATE,
                        calls(new ScriptedChatModel()))
                .data());

        assertThat(decider.hasNext()).isTrue();
        final SegmentOutcome firstDecision =
                Objects.requireNonNull(decider.nextDecision().data());
        assertThat(decider.hasNext()).isTrue();
        final SegmentOutcome secondDecision =
                Objects.requireNonNull(decider.nextDecision().data());
        assertThat(decider.hasNext()).isFalse();

        assertThat(firstDecision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(secondDecision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(firstDecision.segmentId()).isEqualTo("Book.md:0");
        assertThat(secondDecision.segmentId()).isEqualTo("Book.md:1");
    }

    private ChunkDecider start(final List<DraftOutcome> outcomes, final ScriptedChatModel model) {
        final LoopSettings settings =
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, ua.bookloom.api.pipeline.QualityDial.BALANCED);
        return Objects.requireNonNull(loop.start(outcomes, settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                .data());
    }

    private static Segment segment(final String id) {
        return segment(id, SOURCE);
    }

    private static Segment segment(final String id, final String source) {
        return new Segment(
                id,
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                source,
                source,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, source.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
