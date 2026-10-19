package ua.bookloom.document.mask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.document.PlaceholderPair;

/**
 * A segment's masked form and the map from each emitted token's bare key to the exact source fragment it
 * replaced.
 *
 * @param masked the content with every protected span replaced by a {@code ⟦gN⟧} token; never null
 * @param placeholders the map from each token's bare key form ({@code g0}) to its mapped fragment, in
 *     first-appearance order; never null
 * @param pairs the opening and closing token of each inline element, in the order the elements open; never null
 * @param lineBreakTokens the tokens that stand for a line break inside the content; never null
 */
public record MaskedContent(
        String masked, Map<String, String> placeholders, List<PlaceholderPair> pairs, List<String> lineBreakTokens) {

    /** Defensively copies the map and lists into unmodifiable, order-preserving copies — order is meaningful. */
    public MaskedContent {
        Objects.requireNonNull(masked, "masked");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(lineBreakTokens, "lineBreakTokens");
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
        pairs = List.copyOf(pairs);
        lineBreakTokens = List.copyOf(lineBreakTokens);
    }

    /**
     * Content with no inline pair and no line break.
     *
     * @param masked the masked form
     * @param placeholders the token map
     */
    public MaskedContent(String masked, Map<String, String> placeholders) {
        this(masked, placeholders, List.of(), List.of());
    }
}
