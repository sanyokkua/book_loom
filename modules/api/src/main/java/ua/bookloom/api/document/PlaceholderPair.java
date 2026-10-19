package ua.bookloom.api.document;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The opening and closing token of one inline element in a segment's masked form, recorded so a translation that
 * swaps, overlaps or empties the pair can be refused before it is restored.
 *
 * @param open the opening token, brackets included, e.g. {@code ⟦g0⟧}
 * @param close the closing token, brackets included, e.g. {@code ⟦g1⟧}
 * @param language the {@code xml:lang} (else {@code lang}) value the element itself declares, as written, or
 *     {@code null} when it declares none or the format has no such notion (Markdown, TXT); lets the pipeline keep a
 *     marked foreign passage verbatim
 */
public record PlaceholderPair(
        String open, String close, @Nullable String language) {

    /** Rejects a missing token. */
    public PlaceholderPair {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(close, "close");
    }
}
