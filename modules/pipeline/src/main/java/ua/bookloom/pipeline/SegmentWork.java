package ua.bookloom.pipeline;

import java.util.Objects;
import ua.bookloom.api.document.Segment;

/** A pending segment paired with its zero-based source section. */
record SegmentWork(Segment segment, int section) {

    SegmentWork {
        Objects.requireNonNull(segment, "segment");
    }
}
