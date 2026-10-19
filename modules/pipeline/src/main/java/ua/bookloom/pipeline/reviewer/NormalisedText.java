package ua.bookloom.pipeline.reviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A text read the way a quote is matched: runs of white space (including no-break spaces) count as one space and the
 * typographic apostrophes as the plain one, so a quote a model retyped with a different space or apostrophe still
 * finds its place — while every offset still maps back to the original, where the edit is applied.
 */
final class NormalisedText {

    private static final String APOSTROPHES = "’‘ʼ";
    private static final String NO_BREAK_SPACES = "   ";

    private final String text;
    private final int[] starts;
    private final int[] ends;

    private NormalisedText(final String text, final int[] starts, final int[] ends) {
        this.text = text;
        this.starts = starts;
        this.ends = ends;
    }

    static NormalisedText of(final String original) {
        Objects.requireNonNull(original, "original");
        final StringBuilder out = new StringBuilder(original.length());
        final List<Integer> from = new ArrayList<>(original.length());
        final List<Integer> to = new ArrayList<>(original.length());
        int index = 0;
        while (index < original.length()) {
            final char current = original.charAt(index);
            if (isSpace(current)) {
                int end = index + 1;
                while (end < original.length() && isSpace(original.charAt(end))) {
                    end++;
                }
                add(out, from, to, ' ', index, end);
                index = end;
            } else {
                add(out, from, to, APOSTROPHES.indexOf(current) >= 0 ? '\'' : current, index, index + 1);
                index++;
            }
        }
        return new NormalisedText(
                out.toString(),
                from.stream().mapToInt(Integer::intValue).toArray(),
                to.stream().mapToInt(Integer::intValue).toArray());
    }

    private static void add(
            final StringBuilder out,
            final List<Integer> from,
            final List<Integer> to,
            final char normalised,
            final int start,
            final int end) {
        out.append(normalised);
        from.add(start);
        to.add(end);
    }

    private static boolean isSpace(final char value) {
        return Character.isWhitespace(value) || NO_BREAK_SPACES.indexOf(value) >= 0;
    }

    String text() {
        return text;
    }

    /** How often {@code quote} occurs, counting overlapping occurrences, because an overlap is as ambiguous as a repeat. */
    int count(final NormalisedText quote) {
        int count = 0;
        int at = text.indexOf(quote.text);
        while (at >= 0) {
            count++;
            at = text.indexOf(quote.text, at + 1);
        }
        return count;
    }

    /** The original text with the single occurrence of {@code quote} replaced; the caller has checked it is single. */
    String replace(final String original, final NormalisedText quote, final String replacement) {
        final int at = text.indexOf(quote.text);
        final int from = starts[at];
        final int to = ends[at + quote.text.length() - 1];
        return original.substring(0, from) + replacement + original.substring(to);
    }
}
