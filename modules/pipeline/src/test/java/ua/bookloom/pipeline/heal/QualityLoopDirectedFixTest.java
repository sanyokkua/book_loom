package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop}/{@link SegmentHealer}: a concrete finding (a failed hard gate or a soft check failed
 * outright) sends a round to a directed fix, naming the expected token sequence for a placeholder finding
 * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before flagging
 * it").
 */
class QualityLoopDirectedFixTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A placeholder finding's directed fix names the full expected token sequence in its user message — on
    // Balanced, whose two rounds both stay directed-fix since the gate never lets the placeholder finding clear.
    @Test
    void nextDecision_placeholderFindingOnThreeTokens_directedFixNamesTheExpectedSequence() {
        final Segment segment = threeTokenSegment();
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment, segment.masked(), List.of(), "ЗАЛИШАЄТЬСЯ ⟦g1⟧ ⟦g2⟧ РЕЧІ.", null, null, placeholderFinding());
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"target\":\"x\"}")).answer(readable("{\"target\":\"x\"}"));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.UNATTENDED,
                QualityDial.BALANCED,
                QualityLoopFixtures.ALWAYS_FAILS_PLACEHOLDER,
                model);
        decider.nextDecision();

        // The initial gate failure excludes this outcome from the chunk's reviewer call, so both calls are the
        // directed fix's own rounds.
        assertThat(model.requests()).hasSize(2);
        assertThat(Objects.requireNonNull(model.requests().getFirst().responseFormat())
                        .name())
                .isEqualTo("directed-fix");
        final String userMessage =
                model.requests().getFirst().messages().getLast().content();
        assertThat(userMessage).contains("⟦g0⟧ ⟦g1⟧ ⟦g2⟧");
    }

    // Fast (N=1): the draft's placeholder gate failed and the one directed fix crosses the pair the same way, so
    // the round is wasted and the segment is FLAGGED with no machine target after exactly one model call.
    @Test
    void nextDecision_fastCrossedPairPersistsThroughTheOneRound_flagsWithNoMachineTarget() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("crossed.md"), "He opened the *old* door.");
        final DraftOutcome.Drafted outcome =
                QualityLoopFixtures.drafted(segment, documents, "ВІН ВІДЧИНИВ ⟦g1⟧OLD⟦g0⟧ ДВЕРІ.");
        assertThat(outcome.restoredTarget()).isNull();
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"target\":\"ВІН ВІДЧИНИВ ⟦g1⟧OLD⟦g0⟧ ДВЕРІ.\"}"));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.UNATTENDED,
                QualityDial.FAST,
                GateFunction.of(documents, BookFormat.MARKDOWN),
                model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isNull();
        assertThat(outcomeResult.findings())
                .filteredOn(finding -> finding.kind().equals("markup"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.severity()).isEqualTo(Severity.HIGH));
    }

    // A directed fix cut off by length flags at once, keeping the earlier target that passed every hard gate.
    @Test
    void nextDecision_directedFixCutOffByLength_flagsAtOnceKeepingTheEarlierMachineTarget() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR.");
        assertThat(outcome.restoredTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"target\":\"HE OPEN", FinishReason.LENGTH)));

        final ChunkDecider decider = start(
                List.of(outcome), ReviewMode.ASSISTED, QualityDial.FAST, QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        assertThat(Objects.requireNonNull(outcomeResult.flagReason()).code()).isEqualTo(ErrorCode.validation);
    }

    // Balanced (N=2), reviewer on: the echo is refused by the checks, so the reviewer never reads it, and the one
    // directed fix it takes is cut off by length — FLAGGED at once, no second fix, keeping the draft's own target.
    @Test
    void nextDecision_balancedCutOffByLength_flagsAtOnceWithNoSecondFix() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo3.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"target\":\"HE OPEN", FinishReason.LENGTH)));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.ASSISTED,
                QualityDial.BALANCED,
                QualityLoopFixtures.PASSTHROUGH_GATE,
                model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().stream()
                        .map(request ->
                                Objects.requireNonNull(request.responseFormat()).name())
                        .toList())
                .containsExactly("directed-fix");
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        assertThat(Objects.requireNonNull(outcomeResult.flagReason()).code()).isEqualTo(ErrorCode.validation);
    }

    // A malformed directed-fix reply wastes its round with no structural repair; the next round is tried too, and
    // it accepts the segment.
    @Test
    void nextDecision_directedFixAnsweredEmptySegmentsArray_wastesTheRoundAndTriesTheNext() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo2.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"segments\":[]}"))
                .answer(readable("{\"target\":\"Він відчинив старі двері.\"}"));
        // Reviewer off, repair budget 2, so the round wasted by the malformed reply still leaves one more to try.
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(1, 2, 0, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());

        final ChunkDecider decider = start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(2);
        assertThat(Objects.requireNonNull(model.requests().get(1).responseFormat())
                        .name())
                .isEqualTo("directed-fix");
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(outcomeResult.repairRounds()).isEqualTo(2);
    }

    // D-3, Fast (N=1), Unattended (τ=0.60): each row is drafted and directed-fixed with the same reply. Despite
    // confidence reaching τ, the soft check named still fails outright and FLAGS the segment with a medium finding
    // of the given kind (specs/quality-gates/spec.md "Block acceptance when a soft check fails outright").
    @ParameterizedTest
    @CsvSource(delimiterString = "|", textBlock = """
                    He opened the old door.|He opened the old door.|0.65|language
                    He opened the old door.|Vin vidchynyv stari dveri.|0.80|language
                    He opened the old door and walked into the dark hall.|Він відчинив старі двері.|0.75|omission
                    The door opened again, and again, and again in the night.|двері відчинилися знову двері відчинилися знову двері відчинилися знову|0.90|fluency
                    """)
    void nextDecision_d3RowsOnFastUnattended_flagsWithItsMediumFindingDespiteReachingTau(
            final String source, final String target, final double confidence, final String findingKind) {
        final Segment segment = segmentFor(source);
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment, source, List.of(), target, target, target, null);
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(target)));

        final ChunkDecider decider = start(
                List.of(outcome), ReviewMode.UNATTENDED, QualityDial.FAST, QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        final SegmentOutcome result = Objects.requireNonNull(decision.data());
        assertThat(result.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(result.confidence()).isCloseTo(confidence, within(1e-9));
        assertThat(result.findings())
                .filteredOn(finding -> finding.severity() == Severity.MEDIUM)
                .extracting(QaFinding::kind)
                .contains(findingKind);
    }

    // A segment drafted in pieces is too large for any one repair call, so a failing round drafts its pieces again
    // naming the finding and sends no directed fix; a Malformed answer wastes that round like any malformed repair.
    @Test
    void nextDecision_pieceRedraftAnsweringMalformed_wastesTheRoundAndTriesTheNext() {
        final ScriptedRedraft redraft = new ScriptedRedraft(
                new RepairReply.Malformed("not the target object"),
                new RepairReply.Rewritten("Він відчинив старі двері."));
        final ScriptedChatModel model = new ScriptedChatModel();

        final ChunkDecider decider = start(
                List.of(inPieces("HE OPENED THE OLD DOOR.", redraft)),
                twoRoundsReviewerOff(),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                model);
        final SegmentOutcome decision =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(model.requests()).isEmpty();
        assertThat(redraft.findingKinds()).hasSize(2);
        assertThat(redraft.findingKinds().getFirst()).contains("language");
        assertThat(decision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision.repairRounds()).isEqualTo(2);
    }

    private static DraftOutcome.Drafted inPieces(final String reply, final PieceRedraft redraft) {
        final String source = "He opened the old door.";
        return new DraftOutcome.Drafted(segmentFor(source), source, List.of(), reply, reply, reply, null, redraft);
    }

    private static LoopSettings twoRoundsReviewerOff() {
        return new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(1, 2, 0, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
    }

    /** Answers each redraft with the next scripted reply and remembers the kinds of the findings it was given. */
    private static final class ScriptedRedraft implements PieceRedraft {

        private final Deque<RepairReply> replies = new ArrayDeque<>();
        private final List<List<String>> findingKinds = new ArrayList<>();

        ScriptedRedraft(final RepairReply... scripted) {
            replies.addAll(List.of(scripted));
        }

        @Override
        public Result<RepairReply> redraft(final List<QaFinding> findings, final ModelCalls calls) {
            findingKinds.add(findings.stream().map(QaFinding::kind).toList());
            return Result.ok(replies.removeFirst());
        }

        List<List<String>> findingKinds() {
            return findingKinds;
        }
    }

    private static Segment segmentFor(final String source) {
        return new Segment(
                "Book.md:0",
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

    private static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    private ChunkDecider start(
            final List<DraftOutcome> outcomes,
            final ReviewMode mode,
            final QualityDial dial,
            final GateFunction gate,
            final ScriptedChatModel model) {
        return start(outcomes, QualityLoopFixtures.settings(mode, dial), gate, model);
    }

    private ChunkDecider start(
            final List<DraftOutcome> outcomes,
            final LoopSettings settings,
            final GateFunction gate,
            final ScriptedChatModel model) {
        final Result<ChunkDecider> started = loop.start(outcomes, settings, gate, calls(model));
        return Objects.requireNonNull(started.data());
    }

    private static Segment threeTokenSegment() {
        final String masked = "⟦g0⟧ ⟦g1⟧ ⟦g2⟧ items remain.";
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of("g0", "*", "g1", "_", "g2", "`"),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static QaFinding placeholderFinding() {
        return new QaFinding("markup", Severity.HIGH, "placeholder token mismatch", "placeholder");
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
