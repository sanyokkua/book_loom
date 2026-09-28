package ua.bookloom.pipeline.judge;

import java.util.Objects;

/**
 * One drafted pair offered to the judge for one chunk. The pair is rendered to the model as a local label, never
 * this segment id — {@code segmentId} exists only so a finding or deferral against that label can be translated
 * back to the real segment afterward.
 *
 * @param segmentId the segment's real id
 * @param maskedSource the segment's masked source text
 * @param maskedCandidate the segment's masked drafted candidate
 */
public record JudgedPair(String segmentId, String maskedSource, String maskedCandidate) {

    /** Rejects an incomplete pair at the boundary. */
    public JudgedPair {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedCandidate, "maskedCandidate");
    }
}
