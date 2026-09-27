package ua.bookloom.api.project;

import java.nio.file.Path;
import java.util.Objects;
import ua.bookloom.api.document.BookFormat;

/**
 * A book opened for translation, keyed by a stable project id, together with its Book Brief.
 *
 * @param id the project's stable id
 * @param source the source file's path
 * @param format the source book's format
 * @param contentHash a hash identifying the source file's content
 * @param brief the project's current Book Brief
 */
public record Project(String id, Path source, BookFormat format, String contentHash, BookBrief brief) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public Project {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(brief, "brief");
    }

    /**
     * Returns a project with a replacement brief, preserving its identity.
     *
     * @param brief the non-null replacement brief
     * @return a new project with the supplied brief
     */
    public Project withBrief(final BookBrief brief) {
        Objects.requireNonNull(brief, "brief");
        return new Project(id, source, format, contentHash, brief);
    }
}
