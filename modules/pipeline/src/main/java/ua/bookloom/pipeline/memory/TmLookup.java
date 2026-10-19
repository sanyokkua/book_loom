package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.TmEntry;

/**
 * What the translation memory offers for one segment.
 *
 * @param reuse the entry to use without a draft call — same source and same neighbours — or {@code null} when none
 *     matches
 * @param hints the other entries of the same source text under a different context; never null
 * @param suggestions entries of a similar source text, most similar first, none of them also a hint; never null
 */
public record TmLookup(@Nullable TmEntry reuse, List<TmEntry> hints, List<TmEntry> suggestions) {

    /** Copies both lists so the offer cannot change after it was made. */
    public TmLookup {
        Objects.requireNonNull(hints, "hints");
        Objects.requireNonNull(suggestions, "suggestions");
        hints = List.copyOf(hints);
        suggestions = List.copyOf(suggestions);
    }
}
