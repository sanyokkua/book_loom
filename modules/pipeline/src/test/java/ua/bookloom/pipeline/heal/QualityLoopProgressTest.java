package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * Repair rounds stop when the blocker set does not shrink, and a round interrupted by a provider error continues at the call
 * that failed instead of starting the segment over.
 */
class QualityLoopProgressTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";
    private static final String ECHO = "HE OPENED THE OLD DOOR.";
    private static final String STILL_ECHO = "HE OPENED THE OLD DOOR!";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // Three rounds are allowed; the first fix still echoes the source, so the blocker set did not shrink and the loop
    // stops there with one call instead of spending the rest of the budget.
    @Test
    void nextDecision_fixThatLeavesTheSameBlockers_flagsAfterTheFirstRound() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(STILL_ECHO)))
                .answer(readable(targetReply(GOOD_TARGET)));

        final SegmentOutcome decided = first(decider(ECHO, model, new DialParameters(1, 3, 0, false, false, 4)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
    }

    // The fix call failed; the next decision sends that same round again and the repaired target is then accepted.
    @Test
    void nextDecision_afterTheFixCallFailed_continuesAtThatRound() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")))
                .answer(readable(targetReply(GOOD_TARGET)));
        final ChunkDecider decider = decider(ECHO, model, new DialParameters(2, 2, 0, false, false, 4));

        final Result<SegmentOutcome> failed = decider.nextDecision();
        final SegmentOutcome decided = first(decider);

        assertThat(Objects.requireNonNull(failed.error()).code()).isEqualTo(ErrorCode.auth);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(decided.machineTarget()).isEqualTo(GOOD_TARGET);
        assertThat(model.requests())
                .extracting(request ->
                        Objects.requireNonNull(request.responseFormat()).name())
                .containsExactly("directed-fix", "directed-fix");
    }

    // A repaired target is decided by the checks alone: no reviewer call follows the fix.
    @Test
    void nextDecision_repairedTargetPassingTheChecks_isAcceptedWithoutAReviewerCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(GOOD_TARGET)));

        final SegmentOutcome decided = first(decider(ECHO, model, new DialParameters(2, 2, 1, false, false, 4)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().getFirst().responseFormat()).isNotNull();
        assertThat(Objects.requireNonNull(model.requests().getFirst().responseFormat())
                        .name())
                .isEqualTo("directed-fix");
    }

    private ChunkDecider decider(final String draft, final ScriptedChatModel model, final DialParameters dial) {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), draft, draft, draft, null);
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
