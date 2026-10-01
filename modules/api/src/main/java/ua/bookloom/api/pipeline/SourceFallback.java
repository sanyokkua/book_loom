package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A segment an export wrote in its source language although it had a translation, because that translation broke the
 * segment's formatting — its placeholders no longer matched the source's, or the written book re-opened with different
 * markup in it.
 *
 * @param segmentId the segment's stable id
 * @param locator where a person finds it, such as {@code ch12 · p02}
 */
public record SourceFallback(String segmentId, String locator) {

    /** Rejects a missing component. */
    public SourceFallback {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
    }
}
