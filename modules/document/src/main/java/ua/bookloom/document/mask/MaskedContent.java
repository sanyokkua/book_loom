package ua.bookloom.document.mask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A segment's masked form and the map from each emitted token's bare key to the exact source fragment it
 * replaced.
 *
 * @param masked the content with every protected span replaced by a {@code ⟦gN⟧} token; never null
 * @param placeholders the map from each token's bare key form ({@code g0}) to its mapped fragment, in
 *     first-appearance order; never null
 */
public record MaskedContent(String masked, Map<String, String> placeholders) {

    /** Defensively copies {@code placeholders} into an unmodifiable, order-preserving map — order is meaningful. */
    public MaskedContent {
        Objects.requireNonNull(masked, "masked");
        Objects.requireNonNull(placeholders, "placeholders");
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }
}
