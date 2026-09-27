package ua.bookloom.api.document;

import java.nio.file.Path;
import ua.bookloom.api.Result;

/**
 * Inspects a candidate file before it is opened, and profiles an already-opened {@link Document}. Implemented by
 * {@code :document}.
 *
 * <p>A refusing {@link InspectionVerdict} ({@code DRM_PROTECTED}, {@code UNSUPPORTED}) is a normal, successful
 * answer from {@link #inspect} — the import screen uses it to tell "this book is protected" apart from "this is a
 * PDF" without a new error code (ADR-0039). Actually calling {@link DocumentPort#open} on such a file still fails,
 * carrying {@code ErrorCode.validation}; {@code inspect} only previews what {@code open} would do.
 *
 * <p>Every method is a boundary method: no exception may cross this interface, per
 * {@code 02_Architecture/09_ERROR_HANDLING.md#boundary-discipline}.
 */
public interface BookInspector {

    /**
     * Inspects {@code source} without fully opening it, reporting whether it can be opened and, when it cannot,
     * why.
     *
     * @param source the candidate file to inspect
     * @return the inspection result, or a failed result when inspection itself cannot complete (for example the
     *     file cannot be read at all)
     */
    Result<BookInspection> inspect(Path source);

    /**
     * Computes {@code document}'s profile — title, author, cover, structure tree and statistics.
     *
     * @param document the already-opened document to profile
     * @return the computed profile, or a failed result
     */
    Result<BookProfile> profile(Document document);
}
