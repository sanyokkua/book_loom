package ua.bookloom.pipeline.project;

import java.nio.file.Path;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.pipeline.ImportedBook;

/**
 * The import's refusal of a book whose body holds no word. The inspection cannot see it, since it does not read the
 * text, so it is decided once the book is open and profiled, before a project id is chosen: an empty book neither
 * replaces an open project nor stores one.
 */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class NoTextRefusal {

    /**
     * Refuses a book with no word in its body.
     *
     * @param source the imported file
     * @param inspection what the inspection found, kept apart from its verdict
     * @param profile the opened book's profile, whose body word count decides
     * @return the refused import with the {@link InspectionVerdict#NO_TEXT} verdict and no project, or empty when the
     *     body holds at least one word
     */
    static Optional<ImportedBook> of(final Path source, final BookInspection inspection, final BookProfile profile) {
        final int words = profile.stats().words();
        log.debug(
                "create path={} bodySegments={} words={}",
                source,
                profile.stats().segments(),
                words);
        if (words > 0) {
            return Optional.empty();
        }
        log.warn("import refused path={} verdict={}: the body holds no word", source, InspectionVerdict.NO_TEXT);
        final BookInspection refused = new BookInspection(
                InspectionVerdict.NO_TEXT,
                inspection.format(),
                inspection.formatVersion(),
                inspection.detectedType(),
                null,
                inspection.languageEvidence());
        return Optional.of(new ImportedBook(null, refused, null, null));
    }
}
