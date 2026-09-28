package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * One chunk segment's outcome from the draft step (task 10.2): either a reply the quality loop can evaluate, or
 * content design D3's rules 2-4 (or a model {@code emptyCompletion}/{@code contextWindow} reply) already flagged
 * without a self-heal round ({@code specs/translation-pipeline/spec.md} "Flag a segment whose reply cannot be used,
 * and continue").
 */
public sealed interface DraftOutcome {

    /** The segment this outcome is about. */
    Segment segment();

    /** The segment's masked source, shown to every self-heal call under {@code [Source]}. */
    String maskedSource();

    /** The locked glossary terms and kept foreign runs masked in this segment; empty until task 9.3/10.3. */
    List<LockedRendering> lockedRenderings();

    /**
     * A drafted reply ready for the quality loop.
     *
     * @param segment the segment this outcome is about
     * @param maskedSource the segment's masked source
     * @param lockedRenderings the locked terms and kept foreign runs masked in this segment
     * @param maskedReply the draft's masked reply, already trimmed and restored into its segment's own whitespace
     * @param restoredTarget the reply restored through the placeholder gate, or {@code null} when the draft still
     *     failed that gate after its own placeholder repair
     * @param gateFinding the high {@code markup} finding raised by {@code placeholder} when {@code restoredTarget}
     *     is {@code null}; {@code null} when the gate passed
     */
    record Drafted(
            Segment segment,
            String maskedSource,
            List<LockedRendering> lockedRenderings,
            String maskedReply,
            @Nullable String restoredTarget,
            @Nullable QaFinding gateFinding)
            implements DraftOutcome {

        /**
         * Validates the invariants a caller is entitled to assume, defensively copies the list component, and
         * rejects a {@code restoredTarget}/{@code gateFinding} pair that is not exactly one null and one non-null —
         * the gate either passed (a target, no finding) or it didn't (no target, a finding), never both or
         * neither.
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
            lockedRenderings = List.copyOf(lockedRenderings);
        }
    }

    /**
     * A segment design D3's rules 2-4 already flagged, with no self-heal round and no machine target.
     *
     * @param segment the segment this outcome is about
     * @param maskedSource the segment's masked source
     * @param lockedRenderings the locked terms and kept foreign runs masked in this segment
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
}
