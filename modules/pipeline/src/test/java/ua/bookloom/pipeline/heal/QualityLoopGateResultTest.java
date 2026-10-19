package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
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
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop}/{@link SegmentHealer}: what a failed round's gate finding does to the next round, and that the
 * masked form the gate answers — not the model's raw reply — is what gets evaluated and recorded.
 */
class QualityLoopGateResultTest {

    @TempDir
    private Path tempDir;

    private final DocumentPort documents = TestDocuments.documents();
    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A round whose reply fails the gate hands its finding to the next round in place of the same gate's older one.
    @Test
    void nextDecision_roundFailsTheGate_nextRequestNamesTheRoundsFindingInsteadOfTheInitialOne() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("notes.md"), "The *second* marked paragraph.");
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment, segment.masked(), List.of(), "x", null, null, gateFinding("initial-gate-note"));
        final GateFunction gate = (unusedSegment, maskedReply) -> new GateResult.GateFailed(
                gateFinding("round-gate-note"), AppError.of(ErrorCode.validation, "Placeholder mismatch", "bad"));
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"target\":\"x\"}")).answer(readable("{\"target\":\"x\"}"));

        final ChunkDecider decider = start(outcome, ReviewMode.UNATTENDED, QualityDial.BALANCED, gate, model);
        decider.nextDecision();

        assertThat(model.requests()).hasSize(2);
        assertThat(userMessage(model, 0)).contains("initial-gate-note").doesNotContain("round-gate-note");
        assertThat(userMessage(model, 1)).contains("round-gate-note").doesNotContain("initial-gate-note");
    }

    // The candidate a repair round is judged on is the gate's masked form: the model's ⟦g5⟧ is already a name.
    @Test
    void nextDecision_repairReplyRestoredByTheGate_isAcceptedAsRepairedWithTheGatesMaskedForm() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("echo.md"), "He opened the old door.");
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.drafted(segment, documents, "HE OPENED THE OLD DOOR.");
        final GateFunction gate = (unusedSegment, maskedReply) ->
                new GateResult.Restored("Він відчинив Гейл старі двері.", "Він відчинив Гейл старі двері.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable("{\"target\":\"Він відчинив ⟦g5⟧ старі двері.\"}"));

        final ChunkDecider decider = start(outcome, ReviewMode.UNATTENDED, QualityDial.FAST, gate, model);
        final SegmentOutcome result =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(result.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(result.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(result.machineTarget()).isEqualTo("Він відчинив Гейл старі двері.");
        assertThat(result.maskedMachineTarget()).isEqualTo("Він відчинив Гейл старі двері.");
    }

    // A draft carries both the reply text and the gate's masked form; the recorded masked target is the latter.
    @Test
    void nextDecision_draftWithAMaskedFormDifferentFromItsReply_storesTheMaskedFormAsTheMaskedMachineTarget() {
        final Segment segment =
                QualityLoopFixtures.markdownSegment(tempDir.resolve("hale.md"), "Hale opened the old door.");
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment,
                segment.masked(),
                List.of(),
                "⟦g5⟧ відчинила старі двері.",
                "Гейл відчинила старі двері.",
                "Гейл відчинила старі двері.",
                null);
        final ScriptedChatModel model = new ScriptedChatModel();

        final ChunkDecider decider =
                start(outcome, ReviewMode.UNATTENDED, QualityDial.FAST, QualityLoopFixtures.PASSTHROUGH_GATE, model);
        final SegmentOutcome result =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(model.requests()).isEmpty();
        assertThat(result.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(result.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(result.maskedMachineTarget()).isEqualTo("Гейл відчинила старі двері.");
    }

    private ChunkDecider start(
            final DraftOutcome.Drafted outcome,
            final ReviewMode mode,
            final QualityDial dial,
            final GateFunction gate,
            final ScriptedChatModel model) {
        final Result<ChunkDecider> started =
                loop.start(List.of(outcome), QualityLoopFixtures.settings(mode, dial), gate, calls(model));
        return Objects.requireNonNull(started.data());
    }

    private static String userMessage(final ScriptedChatModel model, final int request) {
        return model.requests().get(request).messages().getLast().content();
    }

    private static QaFinding gateFinding(final String note) {
        return new QaFinding("markup", Severity.HIGH, note, "placeholder");
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
