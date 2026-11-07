package ua.bookloom.api.project;

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
 */
public record ProofreadVerdict(
        String fingerprint,
        ProofreadOutcome outcome,
        String reason,
        @Nullable String before,
        @Nullable SegmentStatus statusBefore) {

    /** Validates the components a caller is entitled to assume. */
    public ProofreadVerdict {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
    }
}
