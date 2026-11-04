package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * How one fixing call's raw model reply — the one repair, or the reviewer's fix of a refused edit — ended up once
 * classified into a {@link RepairReply}: a usable candidate that went through the gate and {@code QaEvaluator}, a
 * reply that produced nothing usable, content that flags the segment at once, or a call failure that ends the step.
 */
sealed interface RepairOutcome {

    /**
     * A {@code Rewritten} reply that passed the placeholder gate and was evaluated.
     *
     * @param maskedCandidate the model's reply, restored into the segment's own whitespace
     * @param maskedForm the candidate as the gate answered it, protected spans restored and the document's own
     *     tokens in place — what was evaluated and what is recorded as the masked target
     * @param restoredTarget the candidate restored through the gate
     * @param qa the candidate's hard-gate and soft outcome
     */
    record Evaluated(String maskedCandidate, String maskedForm, String restoredTarget, QaResult qa)
            implements RepairOutcome {

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
     * @param gateFinding the finding the refusing gate raised; {@code null} for a malformed reply, which no gate saw
     * @param rejectedForm the refused reply with its protected spans put back, which review can show; {@code null}
     *     for a malformed reply or when a protected span itself failed
     */
    record Failed(@Nullable QaFinding gateFinding, @Nullable String rejectedForm) implements RepairOutcome {

        /** A failure with no refused reply to show. */
        Failed(@Nullable final QaFinding gateFinding) {
            this(gateFinding, null);
        }
    }

    /**
     * Content that flags the segment at once, with no evaluated target behind it — the text in place is kept as the
     * segment's {@link SegmentOutcome#machineTarget()}.
     *
     * @param error the error to record against the segment
     */
    record FlagNow(AppError error) implements RepairOutcome {

        /** Rejects a missing error. */
        public FlagNow {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * A call failure — the fixing call's own, or the gate's — that ends the whole {@code nextDecision()} step.
     *
     * @param error the failure to return from the step
     */
    record StepError(AppError error) implements RepairOutcome {

        /** Rejects a missing error. */
        public StepError {
            Objects.requireNonNull(error, "error");
        }
    }
}
