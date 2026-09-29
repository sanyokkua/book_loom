package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * The deterministic name scan: every capitalised word, and every run of two or three, that occurs often enough away
 * from a sentence start. It takes whichever segments the caller passes, so one scan serves the whole book before a run
 * and "the segments decided so far" at the end of each chapter.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FrequencyScan {

    /** How often a name must occur before {@link #newTerms} proposes it. */
    static final int PROPOSAL_MIN_COUNT = 3;

    /**
     * Counts the capitalised words and runs of the segments.
     *
     * @param segments the segments to read, by their masked text; never null, may be empty
     * @param minCount the fewest occurrences a candidate needs; {@code 1} lists every one
     * @return the candidates by count descending, then by first occurrence; never null, empty when none qualifies
     */
    public static List<NameCandidate> candidates(final List<Segment> segments, final int minCount) {
        Objects.requireNonNull(segments, "segments");
        log.debug("Name scan over {} segments, minimum count {}", segments.size(), minCount);
        final Map<String, NameCandidate> tallies = new LinkedHashMap<>();
        for (final Segment segment : segments) {
            for (final Occurrences.Occurrence found : Occurrences.in(segment.masked())) {
                tallies.merge(
                        found.term(),
                        new NameCandidate(found.term(), 1, found.sentence()),
                        (first, another) -> new NameCandidate(first.term(), first.count() + 1, first.firstSentence()));
            }
        }
        final List<NameCandidate> candidates = tallies.values().stream()
                .filter(tally -> tally.count() >= minCount)
                .sorted((left, right) -> Integer.compare(right.count(), left.count()))
                .toList();
        log.debug("Name scan counted {} distinct names, {} reach {}", tallies.size(), candidates.size(), minCount);
        candidates.forEach(candidate ->
                log.trace("Name candidate {} x{}: {}", candidate.term(), candidate.count(), candidate.firstSentence()));
        return candidates;
    }

    /**
     * The glossary entries a scan proposes, leaving out a term the glossary holds or the person removed. Case variants
     * of one name ({@code Hale}, {@code HALE}) share an entry id, so only the highest-ranked one is proposed.
     *
     * @param projectId the project whose glossary is read; never null
     * @param segments the segments to scan; never null
     * @param glossary where the entries and the removal memory are read; nothing is written
     * @return unlocked entries with no target, type other and gender unknown, in candidate order; or the read's error
     */
    public static Result<List<GlossaryEntry>> newTerms(
            final String projectId, final List<Segment> segments, final GlossaryRepository glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(glossary, "glossary");
        return glossary.all(projectId).flatMap(held -> unheldEntries(projectId, segments, glossary, held));
    }

    private static Result<List<GlossaryEntry>> unheldEntries(
            final String projectId,
            final List<Segment> segments,
            final GlossaryRepository glossary,
            final List<GlossaryEntry> held) {
        final Set<String> heldTerms = lowerCasedTerms(held);
        final List<GlossaryEntry> proposals = new ArrayList<>();
        int skippedHeld = 0;
        int skippedRemoved = 0;
        for (final NameCandidate candidate : candidates(segments, PROPOSAL_MIN_COUNT)) {
            final String lowerCased = candidate.term().toLowerCase(Locale.ROOT);
            if (heldTerms.contains(lowerCased)) {
                skippedHeld++;
                continue;
            }
            final Result<Boolean> removed = glossary.wasRemoved(projectId, candidate.term());
            if (removed.isErr()) {
                return Result.err(Objects.requireNonNull(removed.error(), "error"));
            }
            if (Boolean.TRUE.equals(removed.data())) {
                skippedRemoved++;
                continue;
            }
            proposals.add(entryFor(projectId, candidate));
            heldTerms.add(lowerCased);
        }
        log.debug(
                "Name proposals for project {}: {} made, {} skipped as held or a case variant already proposed, {} as removed",
                projectId,
                proposals.size(),
                skippedHeld,
                skippedRemoved);
        return Result.ok(proposals);
    }

    private static Set<String> lowerCasedTerms(final List<GlossaryEntry> entries) {
        final Set<String> terms = new HashSet<>();
        entries.forEach(entry -> terms.add(entry.term().toLowerCase(Locale.ROOT)));
        return terms;
    }

    private static GlossaryEntry entryFor(final String projectId, final NameCandidate candidate) {
        return new GlossaryEntry(
                GlossaryIds.of(projectId, candidate.term()),
                projectId,
                candidate.term(),
                null,
                TermType.OTHER,
                Gender.UNKNOWN,
                false);
    }
}
