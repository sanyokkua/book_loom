package ua.bookloom.api.pipeline;

import java.util.Objects;
import ua.bookloom.api.ErrorCode;

/**
 * The stable identity and typed reason for one flagged segment.
 *
 * @param segmentId the stable segment identifier
 * @param reason the error code that caused the flag
 */
public record FlaggedSegment(String segmentId, ErrorCode reason) {

    /** Rejects an incomplete flagged-segment entry. */
    public FlaggedSegment {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(reason, "reason");
    }
}
