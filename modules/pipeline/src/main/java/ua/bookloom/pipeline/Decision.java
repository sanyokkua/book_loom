package ua.bookloom.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.Segment;

/**
 * The decided segment and its reason when the decision is flagged.
 *
 * @param segment the segment with its status and plain target
 * @param flagReason why the segment was flagged, or null when it was accepted
 * @param maskedTarget the accepted target before the document's own tokens were unmasked, or null when flagged
 */
public record Decision(
        Segment segment,
        @Nullable AppError flagReason,
        @Nullable String maskedTarget) {

    /** Rejects a decision without its segment. */
    public Decision {
        Objects.requireNonNull(segment, "segment");
    }
}
