package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
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
     * @param autoRepair the low {@code markup} finding saying the placeholders were put back without a model, or
     *     {@code null} when the candidate passed as the model wrote it
     */
    record Restored(
            String maskedForm, String restored, @Nullable QaFinding autoRepair) implements GateResult {

        /** Rejects a missing component. */
        public Restored {
            Objects.requireNonNull(maskedForm, "maskedForm");
            Objects.requireNonNull(restored, "restored");
        }

        /** A candidate that passed as the model wrote it. */
        public Restored(final String maskedForm, final String restored) {
            this(maskedForm, restored, null);
        }

        /**
         * The same restoration with no auto-repair finding, for a repair the caller expected — a folded drop cap.
         *
         * @return this restoration without its finding
         */
        public Restored withoutAutoRepair() {
            return new Restored(maskedForm, restored, null);
        }
    }

    /**
     * A gate refused the candidate; the caller may repair it.
     *
     * @param finding the {@code high} finding the refusing gate raised
     * @param error the gate's own error, kept whole with its safe details
     * @param candidate the refused candidate with every protected span put back and the document's own tokens in
     *     place — what review can show as the rejected reply — or {@code null} when a protected span itself failed
     */
    record GateFailed(
            QaFinding finding, AppError error, @Nullable String candidate) implements GateResult {

        /** Rejects a missing component. */
        public GateFailed {
            Objects.requireNonNull(finding, "finding");
            Objects.requireNonNull(error, "error");
        }

        /** A refusal with no candidate to show. */
        public GateFailed(final QaFinding finding, final AppError error) {
            this(finding, error, null);
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
