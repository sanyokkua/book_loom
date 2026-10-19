package ua.bookloom.pipeline.eval;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One book's result in the names suite.
 *
 * @param book the book's id
 * @param score what the run left of the book, scored against its gold; null when the run failed
 * @param diagnostics the cases behind the score, for reading the misses; null when the run failed or none was taken
 * @param error the failing step's error code, or null when the run completed
 */
record NamesRow(
        String book,
        @Nullable NamesScore score,
        @Nullable NamesDiagnostics diagnostics,
        @Nullable String error) {

    /** Rejects a missing id. */
    NamesRow {
        Objects.requireNonNull(book, "book");
    }

    /** A row with no diagnostics. */
    NamesRow(final String book, @Nullable final NamesScore score, @Nullable final String error) {
        this(book, score, null, error);
    }
}
