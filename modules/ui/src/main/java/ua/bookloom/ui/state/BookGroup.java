package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.document.BookFormat;

/**
 * The translations of one book, which share a content hash, under one header.
 *
 * @param contentHash the hash of the book file's content that ties the translations together
 * @param title the book's title as stored with its first translation
 * @param fileName the book file's name
 * @param format the book's format
 * @param totalSegments the segments stored for the book
 * @param rows the translations, in list order; never empty
 */
public record BookGroup(
        String contentHash,
        String title,
        String fileName,
        BookFormat format,
        int totalSegments,
        List<ProjectRow> rows) {

    /** Rejects a missing part and copies the rows. */
    public BookGroup {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(format, "format");
        rows = List.copyOf(rows);
    }
}
