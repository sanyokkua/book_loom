package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;

/**
 * The repair path never makes text worse: it carries the best candidate (fewest failed hard gates, then fewest
 * blockers), discards a step whose blocker set is no better, stops when a step makes no progress and never spends more
 * rounds than the dial allows ({@code specs/quality-gates/spec.md} "Keep the best candidate through the repair
 * path"). The texts are chosen by the checks they trip against "He opened the old door.": a Latin letter inside a
 * Cyrillic word is {@code script-purity}, an unclosed guillemet is {@code quote-balance}, an upper-case English echo is
 * {@code script} and {@code echo}, a one-word target is {@code length}.
 */
class QualityLoopBestCandidateTest {

    private static final String SOURCE = "He opened the old door.";
    private static final String GOOD = "Він відчинив старі двері.";
    private static final String PURITY_AND_QUOTE = "Він відчиниw «старі двері.";
    private static final String PURITY_ONLY = "Він відчиниw старі двері.";
    private static final String QUOTE_ONLY = "Він «відчинив старі двері.";
    private static final String ECHO_AND_QUOTE = "HE OPENED THE «OLD DOOR.";
    private static final String TOO_SHORT = "Він.";
    private static final String SHORT_WITH_PURITY = "Він відчиниw.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    // A repair that fixes one blocker and breaks a hard gate must not replace the draft that only failed a soft check.
    @Test
    void nextDecision_stepThatBreaksAHardGate_isDiscardedAndTheBetterDraftIsKept() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(SHORT_WITH_PURITY)));

        final SegmentOutcome decided = decide(TOO_SHORT, model, 3);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(TOO_SHORT);
        assertThat(raisedBy(decided)).contains("length").doesNotContain("script-purity");
        assertThat(model.requests()).hasSize(1);
    }

    // The "78-worse" pattern: the first repair helps, the second adds blockers; the outcome is the first repair.
    @Test
    void nextDecision_secondRepairWorsensWhatTheFirstImproved_endsOnTheFirstRepair() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(PURITY_ONLY)))
                .answer(readable(targetReply(ECHO_AND_QUOTE)));

        final SegmentOutcome decided = decide(PURITY_AND_QUOTE, model, 3);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(raisedBy(decided)).contains("script-purity").doesNotContain("echo", "quote-balance");
        assertThat(decided.repairRounds()).isEqualTo(2);
    }

    // A swap (one blocker traded for another) is no progress, so the loop stops with the best it has.
    @Test
    void nextDecision_stepThatSwapsOneBlockerForAnother_stopsAndKeepsTheBest() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(QUOTE_ONLY)))
                .answer(readable(targetReply(PURITY_ONLY)))
                .answer(readable(targetReply(GOOD)));

        final SegmentOutcome decided = decide(PURITY_AND_QUOTE, model, 3);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(raisedBy(decided)).contains("quote-balance").doesNotContain("script-purity");
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void nextDecision_blockerSetThatKeepsShrinking_continuesUntilTheTextPasses() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(PURITY_ONLY)))
                .answer(readable(targetReply(GOOD)));

        final SegmentOutcome decided = decide(PURITY_AND_QUOTE, model, 3);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.machineTarget()).isEqualTo(GOOD);
        assertThat(decided.repairRounds()).isEqualTo(2);
    }

    @Test
    void nextDecision_blockerSetThatShrinksButTheDialAllowsOneRound_stopsAfterThatRound() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(QUOTE_ONLY)))
                .answer(readable(targetReply(GOOD)));

        final SegmentOutcome decided = decide(ECHO_AND_QUOTE, model, 1);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(raisedBy(decided)).contains("quote-balance").doesNotContain("echo", "script");
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_noRepairRoundsInTheDial_makesNoDirectedFixCall() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(targetReply(GOOD)));

        final SegmentOutcome decided = decide(PURITY_ONLY, model, 0);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(model.requests()).isEmpty();
    }

    private SegmentOutcome decide(final String draft, final ScriptedChatModel model, final int repairRounds) {
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), SOURCE, List.of(), draft, draft, draft, null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(1, repairRounds, 0, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }

    private static List<String> raisedBy(final SegmentOutcome decided) {
        return decided.findings().stream().map(QaFinding::raisedBy).toList();
    }
}
