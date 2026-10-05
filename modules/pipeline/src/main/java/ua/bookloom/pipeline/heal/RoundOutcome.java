package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.project.QaFinding;
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
     * @param maskedCandidate the model's reply, restored into the segment's own whitespace — the text a next round
     *     rewrites
     * @param maskedForm the candidate as the gate answered it, protected spans restored and the document's own
     *     tokens in place — what was evaluated and what is recorded as the masked target
     * @param restoredTarget the candidate restored through the gate
     * @param qa the candidate's hard-gate and soft outcome
     */
    record Evaluated(String maskedCandidate, String maskedForm, String restoredTarget, QaResult qa)
            implements RoundOutcome {

        /** Rejects a missing component. */
        public Evaluated {
            Objects.requireNonNull(maskedCandidate, "maskedCandidate");
            Objects.requireNonNull(maskedForm, "maskedForm");
            Objects.requireNonNull(restoredTarget, "restoredTarget");
            Objects.requireNonNull(qa, "qa");
        }
    }

    /**
     * A {@code Malformed} reply, or a {@code Rewritten} reply a gate refused.
     *
     * @param gateFinding the finding the refusing gate raised, which the next round repairs; {@code null} for a
     *     malformed reply, which no gate saw
     */
    record Failed(@Nullable QaFinding gateFinding) implements RoundOutcome {}

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
