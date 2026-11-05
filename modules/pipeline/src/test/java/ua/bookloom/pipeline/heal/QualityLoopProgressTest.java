package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.itemReply;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * A segment gets one repair, kept only when it is no worse, and a repair interrupted by a provider error is sent again
 * from the draft kept in memory ({@code specs/resume/spec.md} "Make a cut-off repair again from the draft kept in
 * memory").
 */
class QualityLoopProgressTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";
    private static final String ECHO = "HE OPENED THE OLD DOOR.";
    private static final String STILL_ECHO = "HE OPENED THE OLD DOOR!";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // The repair still echoes the source, so it is not kept and the segment is flagged after its one call.
    @Test
    void nextDecision_repairThatLeavesTheSameBlockers_flagsAfterTheOneRepair() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(itemReply(STILL_ECHO)))
                .answer(readable(itemReply(GOOD_TARGET)));

        final SegmentOutcome decided = first(decider(ECHO, model, new DialParameters(RepairMode.ONE, 0, false, false)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(model.requests()).hasSize(1);
    }

    // The repair call failed; the next decision sends the repair again and the repaired target is then accepted.
    @Test
    void nextDecision_afterTheRepairCallFailed_sendsTheRepairAgain() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")))
                .answer(readable(itemReply(GOOD_TARGET)));
        final ChunkDecider decider = decider(ECHO, model, new DialParameters(RepairMode.ONE, 0, false, false));

        final Result<SegmentOutcome> failed = decider.nextDecision();
        final SegmentOutcome decided = first(decider);

        assertThat(Objects.requireNonNull(failed.error()).code()).isEqualTo(ErrorCode.auth);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.machineTarget()).isEqualTo(GOOD_TARGET);
        assertThat(model.requests())
                .extracting(request -> ScriptedChatModel.callName(request))
                .containsExactly("repair", "repair");
    }

    // A repaired target is decided by the checks alone: no reviewer call follows the repair.
    @Test
    void nextDecision_repairedTargetPassingTheChecks_isAcceptedWithoutAReviewerCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(itemReply(GOOD_TARGET)));

        final SegmentOutcome decided = first(decider(ECHO, model, new DialParameters(RepairMode.ONE, 1, false, false)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().responseFormat()).isNotNull();
        assertThat(model.requests().getFirst().callKind()).isEqualTo(CallKind.REPAIR);
    }

    private ChunkDecider decider(final String draft, final ScriptedChatModel model, final DialParameters dial) {
        final DraftOutcome.Drafted outcome = QualityLoopFixtures.repairing(
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), draft, draft, draft, null));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED, dial, QualityLoopFixtures.FRAME, NamePolicy.TRANSLITERATE, List.of());
        return Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data(),
                "decider");
    }

    private static SegmentOutcome first(final ChunkDecider decider) {
        return Objects.requireNonNull(decider.nextDecision().data(), "decision");
    }
}
