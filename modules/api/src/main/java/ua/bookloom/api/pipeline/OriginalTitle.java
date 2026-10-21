package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A book's original title and where it came from.
 *
 * @param text the title as the reader sees it, never blank
 * @param origin which source gave it
 * @param segmentId the stored segment holding the title, or null when the title is the file name and no segment holds
 *     it
 */
public record OriginalTitle(
        String text, TitleOrigin origin, @Nullable String segmentId) {

    /** Rejects a missing text or origin. */
    public OriginalTitle {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(origin, "origin");
    }
}
