package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopJudgeUnavailableTest.calls;
import static ua.bookloom.pipeline.heal.QualityLoopJudgeUnavailableTest.readable;
import static ua.bookloom.pipeline.heal.QualityLoopJudgeUnavailableTest.segment;
import static ua.bookloom.pipeline.heal.QualityLoopJudgeUnavailableTest.targetReply;

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
 * Self-heal rounds stop when they make no progress, and a round interrupted by a provider error continues at the call
 * that failed instead of starting the segment over.
 */
class QualityLoopProgressTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD_TARGET = "Він відчинив старі двері.";
    private static final String FIXED_TARGET = "Він відчинив старі дубові двері.";
    private static final String ECHO = "HE OPENED THE OLD DOOR.";
    private static final String STILL_ECHO = "HE OPENED THE OLD DOOR!";
    private static final String MEANING_AT_085 = "{\"score\":0.85,\"verdict\":\"revise\",\"findings\":[{\"segmentId\":"
            + "\"s1\",\"type\":\"meaning\",\"severity\":\"medium\",\"note\":\"drops a word\"}],\"deferrals\":[]}";
    private static final String ACCEPT = "{\"score\":0.95,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // Three rounds are allowed; the second fix returns the first fix's text, so a third would change nothing.
    @Test
    void nextDecision_twoFixesWithIdenticalText_flagsAfterTheSecondRound() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(STILL_ECHO)))
                .answer(readable(targetReply(STILL_ECHO)))
                .answer(readable(targetReply(GOOD_TARGET)));

        final SegmentOutcome decided = first(decider(ECHO, model, new DialParameters(1, 3, false, false, false, 4)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.repairRounds()).isEqualTo(2);
        assertThat(model.requests()).hasSize(2);
    }

    // The re-judge repeats the chunk judge's medium "meaning" finding at the same 0.85: another fix is not tried.
    @Test
    void nextDecision_sameBlockingFindingAtAnUnchangedScore_flagsAfterTheFirstRound() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(MEANING_AT_085))
                .answer(readable(targetReply(FIXED_TARGET)))
                .answer(readable(MEANING_AT_085))
                .answer(readable(targetReply(GOOD_TARGET)));

        final SegmentOutcome decided =
                first(decider(GOOD_TARGET, model, new DialParameters(2, 3, true, false, false, 4)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(decided.machineTarget()).isEqualTo(FIXED_TARGET);
        assertThat(model.requests()).hasSize(3);
    }

    // The fix already answered; only the re-judge that failed is sent again.
    @Test
    void nextDecision_afterTheRejudgeFailed_continuesAtTheRejudge() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(MEANING_AT_085))
                .answer(readable(targetReply(FIXED_TARGET)))
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")))
                .answer(readable(ACCEPT));
        final ChunkDecider decider = decider(GOOD_TARGET, model, new DialParameters(2, 2, true, false, false, 4));

        final Result<SegmentOutcome> failed = decider.nextDecision();
        final SegmentOutcome decided = first(decider);

        assertThat(Objects.requireNonNull(failed.error()).code()).isEqualTo(ErrorCode.auth);
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(decided.machineTarget()).isEqualTo(FIXED_TARGET);
        assertThat(model.requests())
                .extracting(request ->
                        Objects.requireNonNull(request.responseFormat()).name())
                .containsExactly("judge", "directed-fix", "judge", "judge");
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
