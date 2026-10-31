package ua.bookloom.api.project;

import java.util.Objects;

/**
 * The quotation marks of the translated book, as the Brief declares them.
 *
 * @param outerOpen the mark that opens a quotation; not blank
 * @param outerClose the mark that closes a quotation; not blank
 * @param innerOpen the mark that opens a quotation inside a quotation; not blank
 * @param innerClose the mark that closes a quotation inside a quotation; not blank
 */
public record QuoteStyle(String outerOpen, String outerClose, String innerOpen, String innerClose) {

    /** Rejects a missing or blank mark. */
    public QuoteStyle {
        requireMark(outerOpen, "outerOpen");
        requireMark(outerClose, "outerClose");
        requireMark(innerOpen, "innerOpen");
        requireMark(innerClose, "innerClose");
    }

    private static void requireMark(final String mark, final String name) {
        Objects.requireNonNull(mark, name);
        if (mark.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
