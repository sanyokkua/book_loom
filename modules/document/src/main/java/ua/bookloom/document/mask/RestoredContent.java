package ua.bookloom.document.mask;

import java.util.List;
import java.util.Objects;

/**
 * The result of substituting placeholders back into a translated target ({@link Unmasker#restore}).
 *
 * <p>{@code fragmentRanges} records where each mapped fragment landed in {@code text}, in restore order — the
 * ranges a later change (task group 8, the Markdown model-introduced-punctuation escape and structure check) needs
 * to tell a construct the model wrote from one that arrived verbatim inside a restored fragment. Computing it here,
 * during the single substitution pass, is what avoids a second pass over the same text once that change lands.
 *
 * @param text the restored content; never null
 * @param fragmentRanges each mapped fragment's range within {@code text}, in the order the fragments were
 *     substituted; never null, empty if the target carried no placeholders
 */
public record RestoredContent(String text, List<FragmentRange> fragmentRanges) {

    /** Defensively copies {@code fragmentRanges} into an unmodifiable, order-preserving list. */
    public RestoredContent {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(fragmentRanges, "fragmentRanges");
        fragmentRanges = List.copyOf(fragmentRanges);
    }
}
