package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;

/**
 * What the parse of a book actually learned, and nothing it did not: the card offers no chapter, word, image or cover
 * figure because {@code Document} carries none.
 *
 * @param fileName the name of the file that was opened, without its directory
 * @param format the format it was parsed as
 * @param title the title the book declares, or {@code null} so the view omits the row instead of showing a blank
 * @param author the author the book declares, or {@code null}
 * @param declaredLang the language the book declares, or {@code null}
 * @param unitCount how many units the book was divided into
 * @param segmentCount how many translatable segments were found across them
 */
public record BookCard(
        String fileName,
        BookFormat format,
        @Nullable String title,
        @Nullable String author,
        @Nullable String declaredLang,
        int unitCount,
        int segmentCount) {

    /** Rejects a missing file name or format. */
    public BookCard {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(format, "format");
    }
}
