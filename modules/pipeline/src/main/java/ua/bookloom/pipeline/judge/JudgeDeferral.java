package ua.bookloom.pipeline.judge;

import java.util.Objects;

/**
 * A fact the judge says a segment needs, revealed later in the book — recorded here only; task 9.7 turns it into a
 * stored deferral.
 *
 * @param segmentId the segment the deferral is against
 * @param reason the judge's short explanation of what is missing
 */
public record JudgeDeferral(String segmentId, String reason) {

    /** Rejects an incomplete deferral at the boundary. */
    public JudgeDeferral {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(reason, "reason");
    }
}
