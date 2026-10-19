package ua.bookloom.pipeline.checks;

import java.util.Objects;

/**
 * The exact place a finding points at.
 *
 * @param start the index of the first char of the span in the checked text
 * @param end the index after the last char; never before {@code start}
 * @param text the spanned text itself, kept so a fix prompt can quote it without the whole segment
 */
public record TextSpan(int start, int end, String text) {

    /** Rejects a negative or inverted range and a missing text. */
    public TextSpan {
        Objects.requireNonNull(text, "text");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("span " + start + ".." + end + " is not a range");
        }
    }
}
