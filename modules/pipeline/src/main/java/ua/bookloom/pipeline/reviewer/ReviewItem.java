package ua.bookloom.pipeline.reviewer;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The reviewer's answer about one segment, its label already translated back to the real segment id.
 *
 * @param segmentId the segment
 * @param status what the reviewer says about the candidate
 * @param edits the edits asked for; empty unless {@code status} is {@link ReviewStatus#EDITS}
 * @param rewrite the whole replacement text, in masked form; present exactly when {@code status} is
 *     {@link ReviewStatus#REWRITE}
 */
public record ReviewItem(
        String segmentId,
        ReviewStatus status,
        List<ReviewEdit> edits,
        @Nullable String rewrite) {

    /** Copies the edits and rejects a missing component. */
    public ReviewItem {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(edits, "edits");
        edits = List.copyOf(edits);
    }

    /**
     * An answer that finds nothing to change.
     *
     * @param segmentId the segment
     * @return an {@link ReviewStatus#OK} item
     */
    public static ReviewItem ok(final String segmentId) {
        return new ReviewItem(segmentId, ReviewStatus.OK, List.of(), null);
    }
}
