package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * One segment's state carried from one self-heal round attempt into the next: the last evaluation, the last target
 * that passed every hard gate, and the text the next round rewrites — plus two verdicts kept deliberately apart.
 *
 * <p>{@code routingVerdict} decides the <em>next</em> round's kind (a medium/high finding on it sends the round to
 * a directed fix): it is the verdict that actually judged the text just evaluated, so it resets to {@code null}
 * whenever a round's target was not re-judged — a stale verdict must never be read as an opinion about brand-new
 * text. {@code recordedVerdict} is what a decided segment's {@code judgeScore}/findings ultimately report: it is
 * the last verdict that judged <em>any</em> text of this segment, so an unjudged round leaves it exactly as it was
 * rather than discarding the chunk's original opinion (design D8; {@code specs/quality-gates/spec.md} "Record each
 * segment's findings for review and repair").
 *
 * @param qa the last evaluated candidate's hard-gate and soft outcome
 * @param routingVerdict the verdict that judged {@code qa}'s own candidate, or {@code null} when none did
 * @param recordedVerdict the last verdict that judged this segment at all, or {@code null} when none ever has
 * @param machine the last target that passed every hard gate, or neither form
 * @param rewriteBase the latest target that passed the placeholder gate, or the original rejected masked reply
 */
record RoundState(
        QaResult qa,
        @Nullable JudgeVerdict routingVerdict,
        @Nullable JudgeVerdict recordedVerdict,
        MachineTarget machine,
        String rewriteBase) {

    /** Rejects a missing non-nullable component. */
    RoundState {
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(rewriteBase, "rewriteBase");
    }
}
