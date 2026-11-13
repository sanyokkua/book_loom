package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The segments whose source names a glossary entry, read from the project's book index, so a late gender can be
 * deferred on exactly the decided segments it may have made wrong. A heading, title or front matter is not one: no
 * gender is agreed there.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentMentions {

    /**
     * Finds the segments naming an entry by its term or any of its forms.
     *
     * @param openProjects where the project's index is served from; never null
     * @param entry the entry; never null
     * @return the segment ids in reading order; never null, empty when no book is open for the project
     */
    static Set<String> naming(final OpenProjects openProjects, final GlossaryEntry entry) {
        Objects.requireNonNull(openProjects, "openProjects");
        Objects.requireNonNull(entry, "entry");
        final BookIndex index = openProjects.index(entry.projectId());
        if (index == null) {
            log.debug(
                    "No segments found naming entry={}: no book is open for project={}", entry.id(), entry.projectId());
            return Set.of();
        }
        final List<String> names = new ArrayList<>(List.of(entry.term()));
        names.addAll(entry.forms());
        final Set<String> ids = index.mentions(names, Case.SENSITIVE).stream()
                .filter(index::isStoryText)
                .map(mention -> index.segment(mention.ordinal()).segmentId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        log.debug("Segments naming entry={} found={}", entry.id(), ids.size());
        return ids;
    }
}
