package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One segment a model call is about, as a person reads it.
 *
 * @param id the segment's stable id
 * @param locator the segment's human-readable position ({@code ch1 · p01}), or its id when the run knows no locator
 * @param displaySource the source as a person reads it, the document's placeholders removed
 * @param currentTarget the translation the call was given to check or revise, as a person reads it, or null when the
 *     call is about a segment not yet translated
 */
public record CallSegment(
        String id,
        String locator,
        String displaySource,
        @Nullable String currentTarget) {

    /**
     * A segment of a call that is given no translation.
     *
     * @param id the segment's stable id
     * @param locator the segment's human-readable position, or its id
     * @param displaySource the source as a person reads it
     */
    public CallSegment(final String id, final String locator, final String displaySource) {
        this(id, locator, displaySource, null);
    }

    /** Rejects a missing part. */
    public CallSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(displaySource, "displaySource");
    }
}
