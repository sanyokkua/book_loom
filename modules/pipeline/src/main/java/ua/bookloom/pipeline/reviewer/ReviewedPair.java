package ua.bookloom.pipeline.reviewer;

import java.util.Objects;

/**
 * One drafted pair offered to the reviewer. The model sees a local label ({@code s1}, {@code s2}, …), never the
 * segment id, so a small model never has to keep a long id straight.
 *
 * @param segmentId the segment's real id
 * @param maskedSource the segment's masked source
 * @param maskedCandidate the segment's masked candidate, as the model wrote it with its {@code ⟦gN⟧} tokens in place
 */
public record ReviewedPair(String segmentId, String maskedSource, String maskedCandidate) {

    /** Rejects a missing component. */
    public ReviewedPair {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedCandidate, "maskedCandidate");
    }
}
