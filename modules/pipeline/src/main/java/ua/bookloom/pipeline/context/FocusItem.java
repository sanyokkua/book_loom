package ua.bookloom.pipeline.context;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One segment a call works on, as block 13 shows it.
 *
 * @param segmentId the segment's id
 * @param maskedSource its masked source, tokens included
 * @param currentTarget the decided text a proofread call reads beside the source, document tokens included, or null
 *     for a call that drafts the segment
 */
public record FocusItem(
        String segmentId, String maskedSource, @Nullable String currentTarget) {

    /** Rejects a missing part. */
    public FocusItem {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedSource, "maskedSource");
    }

    /**
     * An item a call drafts: its source alone.
     *
     * @param segmentId the non-null segment id
     * @param maskedSource the non-null masked source
     */
    public FocusItem(final String segmentId, final String maskedSource) {
        this(segmentId, maskedSource, null);
    }
}
