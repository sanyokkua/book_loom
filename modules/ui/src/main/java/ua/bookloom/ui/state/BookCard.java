package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.CoverImage;

/**
 * What the inspection and the profile found about an opened book, and nothing they did not: a field the book did not
 * declare is {@code null} so the view omits its row instead of showing a blank.
 *
 * @param fileName the name of the file that was opened, without its directory
 * @param format the format it was parsed as
 * @param formatVersion the format's version, such as an EPUB's package version, or {@code null}
 * @param title the title the book declares, or {@code null}
 * @param author the author the book declares, or {@code null}
 * @param declaredLanguage the normalized tag of the language the book declares, or {@code null}
 * @param chapters how many top-level entries the book's structure has
 * @param words the approximate word count
 * @param images how many images the book carries
 * @param fonts how many embedded fonts the book carries
 * @param cover the cover image, or {@code null} when the book has none
 */
public record BookCard(
        String fileName,
        BookFormat format,
        @Nullable String formatVersion,
        @Nullable String title,
        @Nullable String author,
        @Nullable String declaredLanguage,
        int chapters,
        int words,
        int images,
        int fonts,
        @Nullable CoverImage cover) {

    /** Rejects a missing file name or format. */
    public BookCard {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(format, "format");
    }
}
