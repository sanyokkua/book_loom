package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.api.project.QaFinding;

/**
 * What a {@link GateFunction} answers for one candidate target, so a caller knows which gate failed and what it found
 * instead of reading an error code back into a finding.
 */
public sealed interface GateResult {

    /**
     * The candidate passed every gate.
     *
     * @param maskedForm the candidate with every protected span restored and the document's own {@code ⟦gN⟧} tokens
     *     still in place — what is evaluated, judged and recorded as the masked target
     * @param restored {@code maskedForm} restored into the segment's markup
     */
    record Restored(String maskedForm, String restored) implements GateResult {

        /** Rejects a missing component. */
        public Restored {
            Objects.requireNonNull(maskedForm, "maskedForm");
            Objects.requireNonNull(restored, "restored");
        }
    }

    /**
     * A gate refused the candidate; the caller may repair it.
     *
     * @param finding the {@code high} finding the refusing gate raised
     * @param error the gate's own error, kept whole with its safe details
     */
    record GateFailed(QaFinding finding, AppError error) implements GateResult {

        /** Rejects a missing component. */
        public GateFailed {
            Objects.requireNonNull(finding, "finding");
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * The gate itself failed, which says nothing about the candidate and ends the caller's step.
     *
     * @param error the failure to return from the step
     */
    record StepError(AppError error) implements GateResult {

        /** Rejects a missing error. */
        public StepError {
            Objects.requireNonNull(error, "error");
        }
    }
}
