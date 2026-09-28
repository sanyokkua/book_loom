package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * How one self-heal round's raw model reply, once classified into a {@link RepairReply}, ended up: a usable
 * candidate that went through the gate and {@code QaEvaluator}, a reply that produced nothing usable and wastes the
 * round with no new state, content that flags the segment at once, or a call failure that ends the whole step.
 */
sealed interface RoundOutcome {

    /**
     * A {@code Rewritten} reply that passed the placeholder gate and was evaluated.
     *
     * @param maskedCandidate the candidate masked target that was evaluated
     * @param restoredTarget the candidate restored through the placeholder gate
     * @param qa the candidate's hard-gate and soft outcome
     */
    record Evaluated(String maskedCandidate, String restoredTarget, QaResult qa) implements RoundOutcome {

        /** Rejects a missing component. */
        public Evaluated {
            Objects.requireNonNull(maskedCandidate, "maskedCandidate");
            Objects.requireNonNull(restoredTarget, "restoredTarget");
            Objects.requireNonNull(qa, "qa");
        }
    }

    /** A {@code Malformed} reply, or a {@code Rewritten} reply whose gate call failed with {@code validation}. */
    record Failed() implements RoundOutcome {}

    /**
     * Content that flags the segment at once, with no evaluated target behind it — the draft's or an earlier
     * round's {@link SegmentOutcome#machineTarget()} is kept unchanged.
     *
     * @param error the error to record against the segment
     */
    record FlagNow(AppError error) implements RoundOutcome {

        /** Rejects a missing error. */
        public FlagNow {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * Content that flags the segment at once <em>after</em> an improved target already passed every hard gate — a
     * polish call that answers {@code FlagNow} must not discard that improved target: it becomes the machine
     * target and its QA the last evaluation, not whatever the round started from
     * ({@code specs/quality-gates/spec.md} "A near miss is polished").
     *
     * @param error the error to record against the segment
     * @param evaluated the improved target that passed every hard gate before the polish call was made
     */
    record FlagNowAfterEvaluation(AppError error, Evaluated evaluated) implements RoundOutcome {

        /** Rejects a missing component. */
        public FlagNowAfterEvaluation {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(evaluated, "evaluated");
        }
    }

    /**
     * A call failure — the self-heal call's own, or the gate's — that ends the whole {@code nextDecision()} step.
     *
     * @param error the failure to return from the step
     */
    record StepError(AppError error) implements RoundOutcome {

        /** Rejects a missing error. */
        public StepError {
            Objects.requireNonNull(error, "error");
        }
    }
}
