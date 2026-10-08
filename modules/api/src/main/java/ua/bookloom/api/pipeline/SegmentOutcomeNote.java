package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * What became of one segment a model call was about, added to the call's snapshot after its reply was read.
 *
 * @param segmentId the segment the note is about
 * @param kind what became of it
 * @param detail the machine-readable reason behind the kind — the problem names of a fallback ({@code MISSING},
 *     {@code TOKENS}) or the finding kinds of a flag — comma-separated, or empty when there is none
 */
public record SegmentOutcomeNote(String segmentId, Kind kind, String detail) {

    /** What became of a segment; a screen words each kind in its own language. */
    public enum Kind {
        /** A batch item's answer was taken as the segment's draft. */
        ADOPTED,
        /** A batch item's answer was refused and the segment is drafted again on its own. */
        FELL_BACK,
        /** The segment was decided ACCEPTED. */
        ACCEPTED,
        /** The segment was decided FLAGGED. */
        FLAGGED
    }

    /** Rejects a missing part. */
    public SegmentOutcomeNote {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }

    /**
     * Whether the note ends the segment's story: no later note follows a decision.
     *
     * @return {@code true} if the segment was decided, {@code false} for a batch item's adoption or fallback
     */
    public boolean isDecision() {
        return kind == Kind.ACCEPTED || kind == Kind.FLAGGED;
    }
}
