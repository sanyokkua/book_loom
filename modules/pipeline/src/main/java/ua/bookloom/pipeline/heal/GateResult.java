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
     *     still in place — what is evaluated, reviewed and recorded as the masked target
     * @param restored {@code maskedForm} restored into the segment's markup
     * @param autoRepair the low {@code markup} finding saying the placeholders were put back without a model, or
     *     {@code null} when the candidate passed as the model wrote it
     * @param normalised the low {@code normalised} finding saying the typography pass changed the candidate, or
     *     {@code null} when it left it as it was
     * @param maskedCandidate the candidate after the typography pass with its protected-span tokens still in it —
     *     the text the checks and the reviewer read, so they see what is stored; {@code null} when no typography
     *     pass ran, and the candidate the caller sent stands
     */
    record Restored(
            String maskedForm,
            String restored,
            @Nullable QaFinding autoRepair,
            @Nullable QaFinding normalised,
            @Nullable String maskedCandidate)
            implements GateResult {

        /** Rejects a missing component. */
        public Restored {
            Objects.requireNonNull(maskedForm, "maskedForm");
            Objects.requireNonNull(restored, "restored");
        }

        /** A restoration with no normalised candidate of its own. */
        public Restored(
                final String maskedForm,
                final String restored,
                @Nullable final QaFinding autoRepair,
                @Nullable final QaFinding normalised) {
            this(maskedForm, restored, autoRepair, normalised, null);
        }

        /** A candidate that passed as the model wrote it. */
        public Restored(final String maskedForm, final String restored) {
            this(maskedForm, restored, null, null);
        }

        /** A candidate whose placeholders may have been put back, with its typography as written. */
        public Restored(final String maskedForm, final String restored, @Nullable final QaFinding autoRepair) {
            this(maskedForm, restored, autoRepair, null);
        }

        /**
         * The same restoration noting that the typography pass changed the candidate.
         *
         * @param finding the non-null low finding to carry
         * @return this restoration with the finding
         */
        public Restored withNormalised(final QaFinding finding) {
            return new Restored(
                    maskedForm, restored, autoRepair, Objects.requireNonNull(finding, "finding"), maskedCandidate);
        }

        /**
         * The same restoration carrying the candidate the typography pass produced.
         *
         * @param candidate the non-null normalised candidate, protected-span tokens still in it
         * @return this restoration with the candidate
         */
        public Restored withMaskedCandidate(final String candidate) {
            return new Restored(
                    maskedForm, restored, autoRepair, normalised, Objects.requireNonNull(candidate, "candidate"));
        }

        /**
         * The candidate the checks read.
         *
         * @param given the candidate the caller sent to the gate
         * @return the normalised candidate when a typography pass made one, else {@code given}
         */
        public String candidateOr(final String given) {
            return maskedCandidate == null ? given : maskedCandidate;
        }

        /**
         * The same restoration with no auto-repair finding, for a repair the caller expected — a folded drop cap.
         *
         * @return this restoration without its finding
         */
        public Restored withoutAutoRepair() {
            return new Restored(maskedForm, restored, null, normalised, maskedCandidate);
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
