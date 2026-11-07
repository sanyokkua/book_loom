package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;

/**
 * The record a segment keeps after a Proofread window read it, so a restart shows what was decided and Undo proofread
 * can put the earlier text and status back.
 *
 * @param fingerprint a hash of the source and the facts the window was read with, so an unchanged segment is not read
 *     again; never blank
 * @param outcome what the stage decided
 * @param reason a short human-readable reason, for a refusal the check that got worse; may be empty, never null
 * @param before the plain target before a {@link ProofreadOutcome#KEPT} edit replaced it, or null when no edit was kept
 * @param statusBefore the segment's status before a kept edit, so Undo proofread can return a flagged segment to
 *     {@code FLAGGED}, or null when no edit was kept
 * @param maskedBefore the masked form of {@code before}, which Undo proofread puts back with it, or null when no edit
 *     was kept
 * @param findingsBefore the findings the segment held before a kept edit, so Undo proofread returns a flagged segment
 *     with the findings that blocked it; empty when no edit was kept
 */
public record ProofreadVerdict(
        String fingerprint,
        ProofreadOutcome outcome,
        String reason,
        @Nullable String before,
        @Nullable SegmentStatus statusBefore,
        @Nullable String maskedBefore,
        List<QaFinding> findingsBefore) {

    /** Validates the components a caller is entitled to assume and copies the findings. */
    public ProofreadVerdict {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        findingsBefore = List.copyOf(Objects.requireNonNull(findingsBefore, "findingsBefore"));
    }

    /** A verdict that kept no edit, so there is nothing to put back. */
    public ProofreadVerdict(final String fingerprint, final ProofreadOutcome outcome, final String reason) {
        this(fingerprint, outcome, reason, null, null, null, List.of());
    }

    /**
     * Whether Undo proofread can put the earlier text back: the edit was kept and its earlier text stored.
     *
     * @return {@code true} for a kept edit with its earlier text, {@code false} otherwise
     */
    public boolean isUndoable() {
        return outcome == ProofreadOutcome.KEPT && before != null && maskedBefore != null && statusBefore != null;
    }

    /**
     * The same verdict under another fingerprint, its earlier text and status kept.
     *
     * @param now the non-null fingerprint of the window as it now stands
     * @return a copy under {@code now}
     */
    public ProofreadVerdict restamped(final String now) {
        return new ProofreadVerdict(now, outcome, reason, before, statusBefore, maskedBefore, findingsBefore);
    }
}
