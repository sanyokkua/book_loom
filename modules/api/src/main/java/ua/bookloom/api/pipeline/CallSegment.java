package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * One segment a model call is about, as a person reads it.
 *
 * @param id the segment's stable id
 * @param locator the segment's human-readable position ({@code ch1 · p01}), or its id when the run knows no locator
 * @param displaySource the source as a person reads it, the document's placeholders removed
 */
public record CallSegment(String id, String locator, String displaySource) {

    /** Rejects a missing part. */
    public CallSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(displaySource, "displaySource");
    }
}
