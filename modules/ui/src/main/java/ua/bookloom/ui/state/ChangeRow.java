package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One line of an operation's results.
 *
 * @param id the row's number within its results, by which it is reverted
 * @param kind what the operation did to it
 * @param term the source term
 * @param before the row as it was, worded for the person; a dash for an added row
 * @param after the row as it is, worded for the person; a dash for a removed row
 * @param reason the model's own words for the change, or null when it gave none
 * @param origin where an added row came from; {@link ChangeOrigin#MODEL} for every other row
 */
public record ChangeRow(
        long id,
        ChangeKind kind,
        String term,
        String before,
        String after,
        @Nullable String reason,
        ChangeOrigin origin) {

    /** Rejects missing parts. */
    public ChangeRow {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /**
     * A row the model produced.
     *
     * @param id the row's number within its results
     * @param kind what the operation did to it
     * @param term the source term
     * @param before the row as it was
     * @param after the row as it is
     * @param reason the model's own words, or null
     */
    public ChangeRow(
            final long id,
            final ChangeKind kind,
            final String term,
            final String before,
            final String after,
            final @Nullable String reason) {
        this(id, kind, term, before, after, reason, ChangeOrigin.MODEL);
    }
}
