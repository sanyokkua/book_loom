package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * One segment a call works on, as block 13 shows it.
 *
 * @param segmentId the segment's id
 * @param maskedSource its masked source, tokens included
 */
public record FocusItem(String segmentId, String maskedSource) {

    /** Rejects a missing part. */
    public FocusItem {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedSource, "maskedSource");
    }
}
