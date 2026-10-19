package ua.bookloom.pipeline.qa;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * One edit-distance similarity measure, shared by {@link EchoCheck} and, later, the translation memory's fuzzy
 * match — so untranslated-echo detection and TM reuse never disagree on what "similar" means.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextSimilarity {

    /**
     * The similarity of two texts: 1 minus their Levenshtein edit distance divided by the longer text's length, over
     * both texts after NFC normalization and {@link Locale#ROOT} lower-casing.
     *
     * @param a the first text; never null
     * @param b the second text; never null
     * @return a value in {@code [0,1]}; {@code 1.0} when both texts normalize to the same, possibly empty, text
     */
    public static double similarity(final String a, final String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        final int[] left = normalize(a).codePoints().toArray();
        final int[] right = normalize(b).codePoints().toArray();
        final int longer = Math.max(left.length, right.length);
        if (longer == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(left, right) / longer;
    }

    private static String normalize(final String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** Classic Levenshtein edit distance over two code-point arrays, one row of the DP table at a time. */
    private static int levenshtein(final int[] a, final int[] b) {
        int[] previousRow = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) {
            previousRow[j] = j;
        }
        for (int i = 1; i <= a.length; i++) {
            final int[] currentRow = new int[b.length + 1];
            currentRow[0] = i;
            for (int j = 1; j <= b.length; j++) {
                currentRow[j] = distanceCell(a, b, previousRow, currentRow, i, j);
            }
            previousRow = currentRow;
        }
        return previousRow[b.length];
    }

    private static int distanceCell(
            final int[] a, final int[] b, final int[] previousRow, final int[] currentRow, final int i, final int j) {
        if (a[i - 1] == b[j - 1]) {
            return previousRow[j - 1];
        }
        final int substitution = previousRow[j - 1];
        final int deletion = previousRow[j];
        final int insertion = currentRow[j - 1];
        return 1 + Math.min(substitution, Math.min(deletion, insertion));
    }
}
