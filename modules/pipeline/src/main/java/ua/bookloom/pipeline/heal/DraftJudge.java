package ua.bookloom.pipeline.heal;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The one place a drafted reply is judged the way the run judges it: every hard gate and soft check
 * ({@link DraftEvaluation}), the code's own quote-mark repair, then the {@link AcceptanceRule}. The quality loop and
 * the prompt eval both call it, so a reply an eval calls blocked is one the run would not accept either.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DraftJudge {

    /**
     * A draft after evaluation and quote repair.
     *
     * @param outcome the draft, replaced by its quote-repaired form when the code repaired the quote marks
     * @param qa the evaluation of {@code outcome}
     * @param accepted whether the acceptance rule takes the draft as it stands, with no reviewer verdict
     */
    public record Judged(DraftOutcome.Drafted outcome, QaResult qa, boolean accepted) {

        /** Rejects a missing component. */
        public Judged {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(qa, "qa");
        }
    }

    /**
     * Judges one drafted reply.
     *
     * @param drafted the non-null draft, whose placeholder-gate failure (if any) it already carries
     * @param settings the non-null loop settings of its chunk
     * @param gate the non-null gate the quote repair restores through
     * @return the draft as the run goes on with it, its evaluation and whether the acceptance rule takes it
     */
    public static Judged judge(
            final DraftOutcome.Drafted drafted, final LoopSettings settings, final GateFunction gate) {
        Objects.requireNonNull(drafted, "drafted");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(gate, "gate");
        final QaResult qa = DraftEvaluation.evaluate(drafted, settings);
        final Optional<QuoteFixUp.FixedDraft> fixed = QuoteFixUp.fixDraft(drafted, qa, gate, settings);
        final DraftOutcome.Drafted kept =
                fixed.map(QuoteFixUp.FixedDraft::outcome).orElse(drafted);
        final QaResult keptQa = fixed.map(QuoteFixUp.FixedDraft::qa).orElse(qa);
        final boolean accepted = AcceptanceRule.accepts(keptQa, 0);
        if (fixed.isPresent()) {
            log.debug(
                    "Judged draft segmentId={} quote marks repaired accepted={}",
                    drafted.segment().id(),
                    accepted);
        } else {
            log.trace("Judged draft segmentId={} accepted={}", drafted.segment().id(), accepted);
        }
        return new Judged(kept, keptQa, accepted);
    }
}
