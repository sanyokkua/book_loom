package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.SegmentRecord;

/**
 * A segment still to decide.
 *
 * @param segment the opened book's segment, whose masked text is what the model is shown
 * @param section the zero-based position of its unit among the book's body units
 * @param record the stored record the decision replaces
 */
public record WorkItem(Segment segment, int section, SegmentRecord record) {

    /** Rejects a missing component. */
    public WorkItem {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(record, "record");
    }
}
