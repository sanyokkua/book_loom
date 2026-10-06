package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.document.Segment;

/**
 * One earlier segment of the unit and the masked target it has: what a batch shows as a previous pair.
 *
 * @param segment the earlier segment, whose masked source is the pair's source
 * @param target its masked target, effective as the run last decided or drafted it
 */
public record EarlierPair(Segment segment, String target) {

    /** Rejects a missing part. */
    public EarlierPair {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(target, "target");
    }
}
