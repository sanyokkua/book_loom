package ua.bookloom.pipeline.project;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.OriginalTitle;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.DisplayText;

/** Takes a book's original title from the first source that has one; pure over the open document. */
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OriginalTitles {

    private static final String DETAILS_ID = "aux:title";
    private static final String FRONT_MATTER_ID = "aux:fm:title";

    /**
     * Finds the original title: for a book that is not Markdown the details' title, then the front-matter title, then
     * the first heading of the body, then the file name without its format suffix. A Markdown book's metadata title is
     * its front matter, so it is never reported as the details.
     *
     * @param document the non-null open document
     * @param project the non-null stored project, whose source names the file
     * @return the title and its origin; never null, the file name being the last resort
     */
    public static OriginalTitle of(final Document document, final Project project) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(project, "project");
        final OriginalTitle title = details(document, project)
                .or(() -> auxiliary(document, FRONT_MATTER_ID, TitleOrigin.FRONT_MATTER))
                .or(() -> firstHeading(document))
                .orElseGet(() -> fileName(project));
        log.debug("original title of {} comes from {} (segment {})", project.id(), title.origin(), title.segmentId());
        log.trace("original title '{}'", title.text());
        return title;
    }

    private static Optional<OriginalTitle> details(final Document document, final Project project) {
        if (project.format() == BookFormat.MARKDOWN) {
            log.debug("details title skipped: a Markdown book's metadata title is its front matter");
            return Optional.empty();
        }
        return auxiliary(document, DETAILS_ID, TitleOrigin.DETAILS);
    }

    private static Optional<OriginalTitle> auxiliary(
            final Document document, final String id, final TitleOrigin origin) {
        return document.units().stream()
                .filter(Unit::isAuxiliary)
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.id().equals(id))
                .findFirst()
                .flatMap(segment -> titled(segment, origin));
    }

    private static Optional<OriginalTitle> firstHeading(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == SegmentKind.HEADING)
                .findFirst()
                .flatMap(segment -> titled(segment, TitleOrigin.FIRST_HEADING));
    }

    // The text is cut at its first line: a heading or title that wraps would otherwise fill the card.
    private static Optional<OriginalTitle> titled(final Segment segment, final TitleOrigin origin) {
        final String firstLine = segment.masked().strip().lines().findFirst().orElse("");
        final String text = DisplayText.of(firstLine);
        if (text.isEmpty()) {
            log.debug("{} segment {} is blank; the next source is tried", origin, segment.id());
            return Optional.empty();
        }
        return Optional.of(new OriginalTitle(text, origin, segment.id()));
    }

    private static OriginalTitle fileName(final Project project) {
        final String name = String.valueOf(project.source().getFileName());
        final String suffix = project.format().matchedSuffix(name);
        final String stem = name.substring(0, name.length() - suffix.length());
        return new OriginalTitle(stem.isBlank() ? name : stem, TitleOrigin.FILE_NAME, null);
    }
}
