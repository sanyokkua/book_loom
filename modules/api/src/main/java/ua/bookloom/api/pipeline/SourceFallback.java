package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A segment an export wrote in its source language although the run had worked on it, so a person can find every
 * place the written book is not a translation: its translation broke the segment's formatting (its placeholders no
 * longer matched the source's, or the written book re-opened with different markup in it), or it was flagged with no
 * target at all.
 *
 * @param segmentId the segment's stable id
 * @param locator where a person finds it, such as {@code ch12 · p02}
 * @param reason why the source was written
 */
public record SourceFallback(String segmentId, String locator, Reason reason) {

    /** Why a segment was written in its source. */
    public enum Reason {
        /** Its stored translation broke the segment's formatting. */
        BROKEN_FORMATTING,
        /** It was flagged and no draft ever passed the gates, so nothing was stored to write. */
        NO_TARGET
    }

    /** Rejects a missing component. */
    public SourceFallback {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(reason, "reason");
    }

    /**
     * A fallback for a translation that broke the formatting, the common case.
     *
     * @param segmentId the segment's stable id
     * @param locator where a person finds it
     */
    public SourceFallback(final String segmentId, final String locator) {
        this(segmentId, locator, Reason.BROKEN_FORMATTING);
    }
}
