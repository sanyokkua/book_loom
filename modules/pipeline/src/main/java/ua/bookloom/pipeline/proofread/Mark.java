package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Objects;

/**
 * One entry of the mark list: a decided story segment worth a second look, and why.
 *
 * @param segmentId the segment's id
 * @param tier how sure the code is that something is wrong
 * @param reasons each reason in words, the marked ones first; never empty
 */
public record Mark(String segmentId, Tier tier, List<String> reasons) {

    /** How sure the code is that a listed segment holds a mistake (design D15 "Terms"). */
    public enum Tier {
        /** Tier 1: FLAGGED, a repair-level finding left after the one repair, or a deferral whose fact arrived. */
        MARKED,

        /** Tier 2: a note-level finding, an audit doubt or one of the code's detectors. */
        DOUBTFUL
    }

    /** Rejects a missing part and copies the reasons. */
    public Mark {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(tier, "tier");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("a mark needs a reason");
        }
    }
}
