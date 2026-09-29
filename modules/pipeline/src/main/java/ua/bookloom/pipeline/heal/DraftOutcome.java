package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.LockedRendering;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * One chunk segment's outcome from the draft phase: a reply the quality loop can evaluate, content design D3's rules
 * 2-4 (or a model {@code emptyCompletion}/{@code contextWindow} reply) already flagged without a self-heal round
 * ({@code specs/translation-pipeline/spec.md} "Flag a segment whose reply cannot be used, and continue"), or a
 * context-matched memory reuse that already passed its checks and is neither drafted nor judged.
 */
public sealed interface DraftOutcome {

    /** The segment this outcome is about. */
    Segment segment();

    /**
     * The text the draft was shown — the segment's masked source with its protected spans behind tokens — which every
     * self-heal rewrite shows under {@code [Source]}, because the span gate needs each token back exactly once. The
     * judge reads {@link Segment#masked()} instead, beside a candidate whose names are back in place.
     */
    String maskedSource();

    /** The locked glossary terms present in this segment; empty when none is protected. */
    List<LockedRendering> lockedRenderings();

    /**
     * A drafted reply ready for the quality loop.
     *
     * @param segment the segment this outcome is about
     * @param maskedSource the text the draft was shown, protected spans behind tokens
     * @param lockedRenderings the locked glossary terms present in this segment
     * @param maskedReply the draft's masked reply, already trimmed and restored into its segment's own whitespace —
     *     the text the model returns to when a round rewrites it
     * @param maskedForm the reply with every protected span restored and the document's own tokens still in place,
     *     as the gate answered it — what is evaluated and recorded as the masked target; {@code null} exactly when
     *     {@code restoredTarget} is
     * @param restoredTarget the reply restored through the placeholder gate, or {@code null} when the draft still
     *     failed that gate after its own placeholder repair
     * @param gateFinding the high {@code markup} finding raised by {@code placeholder} when {@code restoredTarget}
     *     is {@code null}; {@code null} when the gate passed
     * @param pieceRedraft how to draft the segment's pieces again, or {@code null} when it was drafted whole
     */
    record Drafted(
            Segment segment,
            String maskedSource,
            List<LockedRendering> lockedRenderings,
            String maskedReply,
            @Nullable String maskedForm,
            @Nullable String restoredTarget,
            @Nullable QaFinding gateFinding,
            @Nullable PieceRedraft pieceRedraft)
            implements DraftOutcome {

        /** A segment drafted whole. */
        public Drafted(
                final Segment segment,
                final String maskedSource,
                final List<LockedRendering> lockedRenderings,
                final String maskedReply,
                @Nullable final String maskedForm,
                @Nullable final String restoredTarget,
                @Nullable final QaFinding gateFinding) {
            this(segment, maskedSource, lockedRenderings, maskedReply, maskedForm, restoredTarget, gateFinding, null);
        }

        /**
         * Validates the invariants a caller is entitled to assume, defensively copies the list component, and
         * rejects a {@code restoredTarget}/{@code gateFinding} pair that is not exactly one null and one non-null —
         * the gate either passed (a target, no finding) or it didn't (no target, a finding), never both or
         * neither — and a {@code maskedForm} that is not null exactly when {@code restoredTarget} is, since both
         * come from the one successful gate call.
         */
        public Drafted {
            Objects.requireNonNull(segment, "segment");
            Objects.requireNonNull(maskedSource, "maskedSource");
            Objects.requireNonNull(lockedRenderings, "lockedRenderings");
            Objects.requireNonNull(maskedReply, "maskedReply");
            if ((restoredTarget == null) != (gateFinding != null)) {
                throw new IllegalArgumentException(
                        "restoredTarget and gateFinding must be exactly one null and one non-null, but were "
                                + "restoredTarget=" + (restoredTarget == null ? "null" : "present") + " gateFinding="
                                + (gateFinding == null ? "null" : "present"));
            }
            if ((maskedForm == null) != (restoredTarget == null)) {
                throw new IllegalArgumentException("maskedForm and restoredTarget must be both null or both present");
            }
            lockedRenderings = List.copyOf(lockedRenderings);
        }

        /** Whether the segment was drafted in pieces, so a repair round drafts them again. */
        public boolean inPieces() {
            return pieceRedraft != null;
        }
    }

    /**
     * A segment design D3's rules 2-4 already flagged, with no self-heal round and no machine target.
     *
     * @param segment the segment this outcome is about
     * @param maskedSource the text the draft was shown, protected spans behind tokens
     * @param lockedRenderings the locked glossary terms present in this segment
     * @param error the reason the segment was flagged at once
     */
    record FlaggedAtOnce(Segment segment, String maskedSource, List<LockedRendering> lockedRenderings, AppError error)
            implements DraftOutcome {

        /** Validates the invariants a caller is entitled to assume and defensively copies the list component. */
        public FlaggedAtOnce {
            Objects.requireNonNull(segment, "segment");
            Objects.requireNonNull(maskedSource, "maskedSource");
            Objects.requireNonNull(lockedRenderings, "lockedRenderings");
            Objects.requireNonNull(error, "error");
            lockedRenderings = List.copyOf(lockedRenderings);
        }
    }

    /**
     * A translation-memory target reused because the segment's source and both neighbours match the entry's; it
     * already passed its hard gates, checks and τ when the run found it, so it is accepted in its turn with no call
     * ({@code specs/quality-gates/spec.md} "Accept a context-matched memory reuse without the judge").
     *
     * @param segment the segment this outcome is about
     * @param maskedSource the text a draft would have been shown, protected spans behind tokens
     * @param lockedRenderings the locked glossary terms present in this segment
     * @param maskedTarget the stored masked target, protected spans restored and the document's own tokens in place
     * @param restoredTarget {@code maskedTarget} restored into the segment's markup
     * @param qa the reused target's hard-gate and soft outcome, whose confidence its record stores
     */
    record Reused(
            Segment segment,
            String maskedSource,
            List<LockedRendering> lockedRenderings,
            String maskedTarget,
            String restoredTarget,
            QaResult qa)
            implements DraftOutcome {

        /** Rejects a missing component and copies the list. */
        public Reused {
            Objects.requireNonNull(segment, "segment");
            Objects.requireNonNull(maskedSource, "maskedSource");
            Objects.requireNonNull(lockedRenderings, "lockedRenderings");
            Objects.requireNonNull(maskedTarget, "maskedTarget");
            Objects.requireNonNull(restoredTarget, "restoredTarget");
            Objects.requireNonNull(qa, "qa");
            lockedRenderings = List.copyOf(lockedRenderings);
        }
    }
}
