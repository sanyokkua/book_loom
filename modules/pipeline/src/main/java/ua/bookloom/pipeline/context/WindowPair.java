package ua.bookloom.pipeline.context;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One segment before a call's focus, as block 10 shows it.
 *
 * @param segmentId the segment's id
 * @param maskedSource its masked source
 * @param maskedTarget its kept masked target, or null when it was not accepted and gives its source only
 */
public record WindowPair(
        String segmentId, String maskedSource, @Nullable String maskedTarget) {

    /** Rejects a missing id or source. */
    public WindowPair {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedSource, "maskedSource");
    }
}
