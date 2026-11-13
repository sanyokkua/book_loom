package ua.bookloom.pipeline.glossary;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;

/**
 * The one INFO line a names pass ends with (glossary "Log the names pass as counts and never as names"): the entries,
 * their forms, the genders by origin, the entries with no gender and the suggested renderings the target's rules
 * refused. It goes to the always-on run log, a file the person may share, so it holds counts only: no name, rendering
 * or book text.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NamesPassCounts {

    /**
     * Writes the line.
     *
     * @param entries the glossary as the pass leaves it; never null
     * @param rejected how many suggested renderings the target's rules refused
     */
    static void write(final List<GlossaryEntry> entries, final int rejected) {
        log.info(
                "names pass entries={} forms={} book={} rule={} likely={} noGender={} rejected={}",
                entries.size(),
                entries.stream().mapToInt(entry -> entry.forms().size()).sum(),
                withOrigin(entries, Origin.BOOK),
                withOrigin(entries, Origin.RULE),
                withOrigin(entries, Origin.LIKELY),
                entries.stream()
                        .filter(entry -> entry.gender() == Gender.UNKNOWN)
                        .count(),
                rejected);
    }

    // An entry still without a target after the pass is the person's to give: the report lists it for the results
    // dialog, and it is counted at WARN and listed at TRACE (15j.J10), never kept silently.
    static List<String> untranslated(final String projectId, final List<GlossaryEntry> entries) {
        final List<String> open = entries.stream()
                .filter(entry -> entry.target() == null || entry.target().isBlank())
                .map(GlossaryEntry::term)
                .toList();
        if (!open.isEmpty()) {
            log.warn("Names pass project={} left {} name(s) without a target for the person", projectId, open.size());
            log.trace("Names pass project={} names without a target: {}", projectId, open);
        }
        return open;
    }

    private static long withOrigin(final List<GlossaryEntry> entries, final Origin origin) {
        return entries.stream()
                .filter(entry -> entry.gender() != Gender.UNKNOWN && entry.genderOrigin() == origin)
                .count();
    }
}
