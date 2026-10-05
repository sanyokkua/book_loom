package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop#start}: which pairs the chunk's reviewer call shows — only {@link DraftOutcome.Drafted}
 * outcomes whose checks passed, never a {@link DraftOutcome.Reused} one — and how a reviewer-call failure ends the step
 * ({@code specs/quality-gates/spec.md} "Review each chunk once per pass when the quality dial enables the reviewer").
 */
class QualityLoopReviewInputTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A chunk of four: the first still fails its placeholder gate (crossed pair) after the draft's own repair, the
    // third is flagged at once; only the second and fourth passed their hard gates, so the reviewer call shows them.
    @Test
    void start_balancedChunkWithGateFailureAndFlagAtOnce_reviewsOnlyTheQualifyingPairsInOrder() {
        final Segment first =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("s1.md"), "He opened the *old* door.");
        final Segment second = QualityLoopFixtures.markdownSegment(tempDir.resolve("s2.md"), "She smiled softly.");
        final Segment third = QualityLoopFixtures.markdownSegment(tempDir.resolve("s3.md"), "It was quiet.");
        final Segment fourth = QualityLoopFixtures.markdownSegment(tempDir.resolve("s4.md"), "The rain fell.");
        final List<DraftOutcome> outcomes = List.of(
                QualityLoopFixtures.drafted(first, documents, "ВІН ВІДЧИНИВ ⟦g1⟧OLD⟦g0⟧ ДВЕРІ."),
                QualityLoopFixtures.drafted(second, documents, "Вона тихо усміхнулася."),
                QualityLoopFixtures.flaggedAtOnce(third, emptyCompletion()),
                QualityLoopFixtures.drafted(fourth, documents, "Йшов дощ."));
        final ScriptedChatModel model = new ScriptedChatModel().answer(reviewerReply());

        final Result<ChunkDecider> started = loop.start(
                outcomes,
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isOk()).isTrue();
        assertThat(model.requests()).hasSize(1);
        final String userMessage =
                model.requests().getFirst().messages().getLast().content();
        assertThat(userMessage)
                .contains("<Pair id=\"s1\"><Source>She smiled softly.")
                .contains("<Pair id=\"s2\"><Source>The rain fell.")
                .doesNotContain("s3", "s4");
    }

    @Test
    void start_bothOutcomesFlaggedAtOnce_makesNoReviewerCall() {
        final Segment first = QualityLoopFixtures.markdownSegment(tempDir.resolve("a.md"), "One.");
        final Segment second = QualityLoopFixtures.markdownSegment(tempDir.resolve("b.md"), "Two.");
        final List<DraftOutcome> outcomes = List.of(
                QualityLoopFixtures.flaggedAtOnce(first, emptyCompletion()),
                QualityLoopFixtures.flaggedAtOnce(second, emptyCompletion()));
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<ChunkDecider> started = loop.start(
                outcomes,
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isOk()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void start_fastDial_makesNoReviewerCallEvenWithEightDraftedSegments() {
        final List<DraftOutcome> outcomes = eightShortDraftedOutcomes();
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<ChunkDecider> started = loop.start(
                outcomes,
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.FAST),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isOk()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    /** Eight short, individually-parsed drafted outcomes — a fixture, not a loop in the test body. */
    private List<DraftOutcome> eightShortDraftedOutcomes() {
        final List<DraftOutcome> outcomes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            final Segment segment =
                    QualityLoopFixtures.markdownSegment(tempDir.resolve("fast" + index + ".md"), "Short line.");
            outcomes.add(QualityLoopFixtures.drafted(segment, documents, "Короткий рядок."));
        }
        return List.copyOf(outcomes);
    }

    @Test
    void start_reviewerAnswersAuth_endsTheStepWithThatErrorAndNoDecider() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("u.md"), "She left quickly.");
        final List<DraftOutcome> outcomes =
                List.of(QualityLoopFixtures.drafted(segment, documents, "Вона швидко пішла."));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")));

        final Result<ChunkDecider> started = loop.start(
                outcomes,
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isErr()).isTrue();
        assertThat(Objects.requireNonNull(started.error()).code()).isEqualTo(ErrorCode.auth);
    }

    @Test
    void start_balancedChunkWithAReusedThirdOutcome_reviewsTheOtherThreeOnly() {
        final Segment first = QualityLoopFixtures.markdownSegment(tempDir.resolve("r1.md"), "It was late.");
        final Segment second = QualityLoopFixtures.markdownSegment(tempDir.resolve("r2.md"), "He paused.");
        final Segment third = QualityLoopFixtures.markdownSegment(tempDir.resolve("r3.md"), "Yes.");
        final Segment fourth = QualityLoopFixtures.markdownSegment(tempDir.resolve("r4.md"), "She smiled.");
        final List<DraftOutcome> outcomes = List.of(
                QualityLoopFixtures.drafted(first, documents, "Було пізно."),
                QualityLoopFixtures.drafted(second, documents, "Він зупинився."),
                QualityLoopFixtures.reused(third, "Так."),
                QualityLoopFixtures.drafted(fourth, documents, "Вона всміхнулася."));
        final ScriptedChatModel model = new ScriptedChatModel().answer(reviewerReply());

        final Result<ChunkDecider> started = loop.start(
                outcomes,
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isOk()).isTrue();
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().messages().getLast().content())
                .contains(
                        "<Pair id=\"s1\"><Source>It was late.",
                        "<Pair id=\"s2\"><Source>He paused.",
                        "<Pair id=\"s3\"><Source>She smiled.")
                .doesNotContain("Yes.", "Так.", "<Pair id=\"s4\">");
    }

    @Test
    void start_everyOutcomeReused_makesNoReviewerCall() {
        final Segment first = QualityLoopFixtures.markdownSegment(tempDir.resolve("y1.md"), "Yes.");
        final Segment second = QualityLoopFixtures.markdownSegment(tempDir.resolve("y2.md"), "No.");
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<ChunkDecider> started = loop.start(
                List.of(QualityLoopFixtures.reused(first, "Так."), QualityLoopFixtures.reused(second, "Ні.")),
                QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(started.isOk()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void nextDecision_reusedOutcome_isAcceptedAsReusedWithItsConfidenceAndNoCall() {
        final Segment segment = QualityLoopFixtures.markdownSegment(tempDir.resolve("y.md"), "Yes.");
        final ScriptedChatModel model = new ScriptedChatModel();
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(
                                List.of(QualityLoopFixtures.reused(segment, "Так.")),
                                QualityLoopFixtures.settings(ReviewMode.MANUAL, QualityDial.MAX),
                                QualityLoopFixtures.PASSTHROUGH_GATE,
                                calls(model))
                        .data(),
                "decider");

        final SegmentOutcome decided =
                Objects.requireNonNull(decider.nextDecision().data(), "decision");

        assertThat(decided)
                .extracting(
                        SegmentOutcome::status,
                        SegmentOutcome::path,
                        SegmentOutcome::machineTarget,
                        SegmentOutcome::maskedMachineTarget,
                        SegmentOutcome::confidence,
                        SegmentOutcome::judgeScore,
                        SegmentOutcome::repairRounds)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.TM_REUSE, "Так.", "Так.", 1.0, null, 0);
        assertThat(model.requests()).isEmpty();
    }

    private static AppError emptyCompletion() {
        return AppError.of(ErrorCode.emptyCompletion, "Empty model response", "The model returned no translated text.");
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> reviewerReply() {
        return Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP));
    }
}
