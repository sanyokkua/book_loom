package ua.bookloom.pipeline.setup;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The book's translated title and author as the metadata segments hold them (the person's edit, else the model's
 * answer), which a suggested file name must spell exactly: a model that is shown the translated title still writes its
 * second word in lower case, and the file would then name the book differently than the book does.
 *
 * @param title the translated title, or null while it is not translated
 * @param author the translated first author, or null while it is not translated
 */
@Slf4j
record FileNameTargets(@Nullable String title, @Nullable String author) {

    private static final String TITLE_ID = "aux:title";
    private static final String AUTHOR_ID = "aux:creator:0";

    /**
     * Reads the translated title and author of a project.
     *
     * @param segments the project's stored segments
     * @param projectId the project's id
     * @return the targets found; either may be null
     */
    static FileNameTargets of(final SegmentRepository segments, final String projectId) {
        return new FileNameTargets(
                translated(segments, projectId, TITLE_ID), translated(segments, projectId, AUTHOR_ID));
    }

    /**
     * Rewrites every occurrence of the title or the author in a name, whatever its letter case, to the text as the book
     * spells it.
     *
     * @param name the cleaned file name proposal
     * @return the name with the title and author spelled as the book does
     */
    String spell(final String name) {
        final String withTitle = spelled(name, title);
        final String result = spelled(withTitle, author);
        if (!result.equals(name)) {
            log.debug("file name respelled from the book's title and author");
        }
        return result;
    }

    private static String spelled(final String name, @Nullable final String exact) {
        if (exact == null || exact.isBlank()) {
            return name;
        }
        final Matcher found = Pattern.compile(Pattern.quote(exact), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(name);
        return found.replaceAll(Matcher.quoteReplacement(exact));
    }

    private static @Nullable String translated(
            final SegmentRepository segments, final String projectId, final String segmentId) {
        final Optional<SegmentRecord> found =
                segments.find(projectId, segmentId).data();
        return found == null ? null : found.flatMap(FileNameTargets::targetOf).orElse(null);
    }

    private static Optional<String> targetOf(final SegmentRecord record) {
        return record.status() == SegmentStatus.PENDING
                ? Optional.empty()
                : record.effectiveTarget().map(String::strip).filter(text -> !text.isEmpty());
    }
}
