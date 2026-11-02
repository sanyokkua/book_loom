package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * The segment a candidate is judged for: the segment, its masked source and the locked glossary terms it holds.
 *
 * @param segment the segment, read for its declared language and id
 * @param maskedSource the segment's masked source
 * @param lockedRenderings the locked glossary terms present in the segment; empty when none is protected
 */
public record DraftSubject(Segment segment, String maskedSource, List<LockedRendering> lockedRenderings) {

    /** Rejects a missing part. */
    public DraftSubject {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
    }
}
