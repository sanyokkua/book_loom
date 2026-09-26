package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import ua.bookloom.api.document.Document;

/**
 * The book the person has open, kept with the file it came from because {@link Document} does not know its own path
 * and the later screens need it (for instance to propose where the translation is written).
 *
 * @param source the file the book was opened from
 * @param document the parsed book
 */
public record OpenedBook(Path source, Document document) {

    /** Rejects a missing source or document. */
    public OpenedBook {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(document, "document");
    }
}
