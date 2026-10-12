package ua.bookloom.pipeline.eval;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One book's result in the names suite.
 *
 * @param book the book's id
 * @param score what the run left of the book, scored against its gold; null when the run failed
 * @param error the failing step's error code, or null when the run completed
 */
record NamesRow(
        String book, @Nullable NamesScore score, @Nullable String error) {

    /** Rejects a missing id. */
    NamesRow {
        Objects.requireNonNull(book, "book");
    }
}
