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
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop}/{@link SegmentHealer}: a blocking finding (a failed hard gate or a soft check failed outright)
 * earns the one repair, naming the expected token sequence for a placeholder finding, and nothing after it
 * ({@code specs/quality-gates/spec.md} "Give a failing segment one repair at Balanced and Max").
 */
class QualityLoopOneRepairCallTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A placeholder finding's repair names the full expected token sequence in its user message — on Balanced, whose
    // one repair is all the segment gets even though the gate never lets the placeholder finding clear.
    @Test
    void nextDecision_placeholderFindingOnThreeTokens_repairNamesTheExpectedSequence() {
        final Segment segment = threeTokenSegment();
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(new DraftOutcome.Drafted(
                segment, segment.masked(), List.of(), "ЗАЛИШАЄТЬСЯ ⟦g1⟧ ⟦g2⟧ РЕЧІ.", null, null, placeholderFinding()));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"x\"}]}"))
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"x\"}]}"));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.UNATTENDED,
                QualityDial.BALANCED,
                QualityLoopFixtures.ALWAYS_FAILS_PLACEHOLDER,
                model);
        decider.nextDecision();

        // The initial gate failure excludes this outcome from the chunk's reviewer call, so the one call is the repair.
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().callKind()).isEqualTo(CallKind.REPAIR);
        final String userMessage =
                model.requests().getFirst().messages().getLast().content();
        assertThat(userMessage).contains("⟦g0⟧ ⟦g1⟧ ⟦g2⟧");
    }

    // The draft's placeholder gate failed and the one repair crosses the pair the same way, so the segment is
    // FLAGGED with no machine target after exactly one model call.
    @Test
    void nextDecision_crossedPairPersistsThroughTheOneRepair_flagsWithNoMachineTarget() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("crossed.md"), "He opened the *old* door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                QualityLoopFixtures.drafted(segment, documents, "ВІН ВІДЧИНИВ ⟦g1⟧OLD⟦g0⟧ ДВЕРІ."));
        assertThat(outcome.restoredTarget()).isNull();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"ВІН ВІДЧИНИВ ⟦g1⟧OLD⟦g0⟧ ДВЕРІ.\"}]}"));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.UNATTENDED,
                QualityDial.BALANCED,
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

    // A repair cut off by length flags at once, keeping the draft's target that passed every hard gate — at Max as
    // at Balanced.
    @Test
    void nextDecision_maxRepairCutOffByLength_flagsAtOnceKeepingTheDraftsMachineTarget() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR."));
        assertThat(outcome.restoredTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(
                        new ChatResponse("{\"items\":[{\"id\":\"1\",\"target\":\"HE OPEN", FinishReason.LENGTH)));

        final ChunkDecider decider = start(
                List.of(outcome), ReviewMode.ASSISTED, QualityDial.MAX, QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        assertThat(Objects.requireNonNull(outcomeResult.flagReason()).code()).isEqualTo(ErrorCode.validation);
    }

    // Balanced, reviewer on: the echo is refused by the checks, so the reviewer never reads it, and its one repair is
    // cut off by length — FLAGGED at once, no second call, keeping the draft's own target.
    @Test
    void nextDecision_balancedCutOffByLength_flagsAtOnceWithNoSecondCall() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo3.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR."));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(
                        new ChatResponse("{\"items\":[{\"id\":\"1\",\"target\":\"HE OPEN", FinishReason.LENGTH)));

        final ChunkDecider decider = start(
                List.of(outcome),
                ReviewMode.ASSISTED,
                QualityDial.BALANCED,
                QualityLoopFixtures.PASSTHROUGH_GATE,
                model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().stream().map(ScriptedChatModel::callName).toList())
                .containsExactly("repair");
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        assertThat(Objects.requireNonNull(outcomeResult.flagReason()).code()).isEqualTo(ErrorCode.validation);
    }

    // A malformed repair reply uses up the one repair with no structural re-ask: the draft stays and nothing else is
    // tried.
    @Test
    void nextDecision_repairAnsweredEmptySegmentsArray_keepsTheDraftWithNoSecondCall() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo2.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR."));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"segments\":[]}"))
                .answer(readable("{\"items\":[{\"id\":\"1\",\"target\":\"Він відчинив старі двері.\"}]}"));

        final ChunkDecider decider =
                start(List.of(outcome), oneRepairReviewerOff(), QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final Result<SegmentOutcome> decision = decider.nextDecision();

        assertThat(model.requests()).hasSize(1);
        final SegmentOutcome outcomeResult = Objects.requireNonNull(decision.data());
        assertThat(outcomeResult.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcomeResult.machineTarget()).isEqualTo("HE OPENED THE OLD DOOR.");
        assertThat(outcomeResult.path()).isEqualTo(SegmentPath.REPAIRED);
    }

    // D-3, Fast, Unattended (τ=0.60): each row's draft has no repair call. Despite confidence reaching τ, the soft
    // check named still fails outright and FLAGS the segment with a medium finding
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

    // A segment drafted in pieces is too large for any one repair call, so its one repair drafts its pieces again
    // naming the finding; a Malformed answer uses that repair up like any malformed repair.
    @Test
    void nextDecision_pieceRedraftAnsweringMalformed_keepsTheDraftAfterTheOneRedraft() {
        final ScriptedRedraft redraft = new ScriptedRedraft(
                new RepairReply.Malformed("not the target object"),
                new RepairReply.Rewritten("Він відчинив старі двері."));
        final ScriptedChatModel model = new ScriptedChatModel();

        final ChunkDecider decider = start(
                List.of(inPieces("HE OPENED THE OLD DOOR.", redraft)),
                oneRepairReviewerOff(),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                model);
        final SegmentOutcome decision =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(model.requests()).isEmpty();
        assertThat(redraft.findingKinds())
                .singleElement()
                .satisfies(kinds -> assertThat(kinds).contains("language"));
        assertThat(decision.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decision.path()).isEqualTo(SegmentPath.REPAIRED);
    }

    @Test
    void nextDecision_pieceRedraftThatPasses_isAcceptedAsTheOneRepair() {
        final ScriptedRedraft redraft = new ScriptedRedraft(new RepairReply.Rewritten("Він відчинив старі двері."));

        final ChunkDecider decider = start(
                List.of(inPieces("HE OPENED THE OLD DOOR.", redraft)),
                oneRepairReviewerOff(),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                new ScriptedChatModel());
        final SegmentOutcome decision =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(decision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision.machineTarget()).isEqualTo("Він відчинив старі двері.");
        assertThat(decision.path()).isEqualTo(SegmentPath.REPAIRED);
    }

    private static DraftOutcome.Drafted inPieces(final String reply, final PieceRedraft redraft) {
        final String source = "He opened the old door.";
        return new DraftOutcome.Drafted(segmentFor(source), source, List.of(), reply, reply, reply, null, redraft);
    }

    private static LoopSettings oneRepairReviewerOff() {
        return new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(RepairMode.ONE, 0, false),
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
        return "{\"items\":[{\"id\":\"1\",\"target\":\"" + target + "\"}]}";
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
