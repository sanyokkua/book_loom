package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.DisplayText;

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

    /**
     * What a draft is shown of the offer: one {@code source → target} line per hint, then per suggestion, the target as
     * display text. A reuse is taken without a call and so is never shown, and an entry whose target is empty once its
     * tokens are removed would teach the model nothing.
     *
     * @return never null; empty when the offer holds no hint or suggestion with text
     */
    public List<String> promptLines() {
        return Stream.concat(hints.stream(), suggestions.stream())
                .flatMap(entry -> lineOf(entry).stream())
                .toList();
    }

    private static Optional<String> lineOf(final TmEntry entry) {
        final String target = DisplayText.of(entry.targetInner());
        return target.isEmpty() ? Optional.empty() : Optional.of(entry.sourceInner() + " → " + target);
    }
}
