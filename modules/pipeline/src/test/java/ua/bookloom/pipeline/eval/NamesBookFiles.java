package ua.bookloom.pipeline.eval;

import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Lends the invented names-suite books, as FB2 files, to tests outside this package. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NamesBookFiles {

    /** The ids of the committed books. */
    public static List<String> ids() {
        return NamesBook.IDS;
    }

    /** Writes the book with the given id as FB2 into {@code dir} and returns the file. */
    public static Path write(final String id, final Path dir) {
        return NamesBook.byId(id).write(dir);
    }
}
