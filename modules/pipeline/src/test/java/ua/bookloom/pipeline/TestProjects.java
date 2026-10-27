package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/** Builds stored projects for tests that do not care about their name or timestamps. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestProjects {

    /** A project named "Book" by the application, with both timestamps at the epoch. */
    public static Project of(
            final String id, final Path source, final BookFormat format, final String hash, final BookBrief brief) {
        return new Project(
                id, source, format, hash, brief, "Book", NameOrigin.AUTO, "Book", Instant.EPOCH, Instant.EPOCH);
    }
}
