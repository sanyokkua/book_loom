package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that a segment's draft has come back from the model, before any repair or judge stage.
 *
 * @param segmentId the segment's stable id
 * @param displayTarget the draft's text as shown to a person
 * @param confidence the QA confidence in {@code [0,1]} computed for this draft
 */
public record SegmentDrafted(String segmentId, String displayTarget, double confidence) implements JobEvent {

    /** Rejects an event without a segment id or display text. */
    public SegmentDrafted {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(displayTarget, "displayTarget");
    }
}
