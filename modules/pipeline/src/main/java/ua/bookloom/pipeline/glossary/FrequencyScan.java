package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * The deterministic name scan: every capitalised word, and every run of two or three, that occurs often enough away
 * from a sentence start in the book's running text ({@link ScanText}) — never in a heading or a title line. It takes whichever segments the caller passes, so one scan serves the whole book before a run
 * and "the segments decided so far" at the end of each chapter.
 *
 * <p>A word the book also writes in lower case often enough ({@link #LOWER_SHARE_LIMIT}) is a common word, not a
 * name, in any language with capitals; a word on the language's bundled stop-word list never starts or joins a name.
 * A capitalised word at a sentence start counts when the book writes it with a capital mid-sentence at least
 * {@link #CREDIT_MIN_MID_SENTENCE} times, so a name that often opens a sentence is not undercounted, and a single word
 * that mostly occurs inside a longer name ({@code Simon} of {@code Simon Lovelace}) is not proposed on its own.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FrequencyScan {

    /** How often a name must occur before {@link #newTerms} proposes it. */
    static final int PROPOSAL_MIN_COUNT = 3;

    /** A word written in lower case at least this share of the time is a common word; every real name measured is below. */
    static final double LOWER_SHARE_LIMIT = 0.2;

    /** A sentence-initial capitalised word counts once the book writes it with a capital mid-sentence this often. */
    static final int CREDIT_MIN_MID_SENTENCE = 2;

    /** A single word standing alone less than this share of its occurrences is part of a longer name. */
    static final double ALIAS_STANDALONE_LIMIT = 0.4;

    /**
     * Counts the capitalised words and runs of the segments.
     *
     * @param segments the segments to read, by their masked text; never null, may be empty
     * @param minCount the fewest occurrences a candidate needs; {@code 1} lists every one
     * @param sourceLanguage the BCP 47 tag of the book's language, choosing the stop-word list; null reads English
     * @return the candidates by count descending, then by first occurrence; never null, empty when none qualifies
     */
    public static List<NameCandidate> candidates(
            final List<Segment> segments, final int minCount, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(segments, "segments");
        log.debug(
                "Name scan over {} segments, minimum count {}, language {}", segments.size(), minCount, sourceLanguage);
        final List<Occurrences.Read> read = ScanText.of(segments, sourceLanguage).stream()
                .map(Occurrences::read)
                .toList();
        final WordCounts counts = WordCounts.of(read);
        final Set<String> stopWords = StopWords.of(sourceLanguage);
        final Set<String> neverAlone = StopWords.neverAlone(sourceLanguage);
        final Map<String, NameCandidate> tallies = tally(read, word -> isNameLike(word, counts, stopWords));
        final List<NameCandidate> candidates = tallies.values().stream()
                .filter(tally -> tally.count() >= minCount)
                .filter(tally -> isNotCommonWord(tally, counts, neverAlone))
                .filter(tally -> isNotPartOfLongerName(tally, tallies.values(), minCount))
                .sorted((left, right) -> Integer.compare(right.count(), left.count()))
                .toList();
        log.debug("Name scan counted {} distinct names, {} kept at {}", tallies.size(), candidates.size(), minCount);
        candidates.forEach(candidate ->
                log.trace("Name candidate {} x{}: {}", candidate.term(), candidate.count(), candidate.firstSentence()));
        return candidates;
    }

    private static boolean isNameLike(
            final Occurrences.Word word, final WordCounts counts, final Set<String> stopWords) {
        final String key = word.key();
        return word.capitalised()
                && !stopWords.contains(key)
                && (!word.initial() || counts.midSentence(key) >= CREDIT_MIN_MID_SENTENCE);
    }

    private static Map<String, NameCandidate> tally(
            final List<Occurrences.Read> read, final Predicate<Occurrences.Word> nameLike) {
        final Map<String, NameCandidate> tallies = new LinkedHashMap<>();
        for (final Occurrences.Read segment : read) {
            for (final Occurrences.Occurrence found : Occurrences.runs(segment, nameLike)) {
                tallies.merge(
                        found.term(),
                        new NameCandidate(found.term(), 1, found.sentence()),
                        (first, another) -> new NameCandidate(first.term(), first.count() + 1, first.firstSentence()));
            }
        }
        return tallies;
    }

    private static boolean isNotCommonWord(
            final NameCandidate tally, final WordCounts counts, final Set<String> neverAlone) {
        if (tally.term().indexOf(' ') >= 0) {
            return true;
        }
        if (neverAlone.contains(Occurrences.keyOf(tally.term()))) {
            log.debug("Name candidate {} dropped: a number or a language name is never a name alone", tally.term());
            return false;
        }
        final double share = counts.lowerShare(Occurrences.keyOf(tally.term()));
        if (share >= LOWER_SHARE_LIMIT) {
            log.debug("Name candidate {} dropped: written in lower case {} of the time", tally.term(), share);
            return false;
        }
        return true;
    }

    private static boolean isNotPartOfLongerName(
            final NameCandidate tally, final Collection<NameCandidate> all, final int minCount) {
        if (tally.term().indexOf(' ') >= 0) {
            return true;
        }
        final String key = Occurrences.keyOf(tally.term());
        final List<NameCandidate> longer = all.stream()
                .filter(other -> other.count() >= minCount && containsWord(other.term(), key))
                .toList();
        final int inside = longer.stream().mapToInt(NameCandidate::count).sum();
        final double standalone = (double) tally.count() / (tally.count() + inside);
        if (inside > 0 && standalone < ALIAS_STANDALONE_LIMIT) {
            log.debug(
                    "Name candidate {} dropped: part of {}, alone {} of the time",
                    tally.term(),
                    longer.getFirst().term(),
                    standalone);
            return false;
        }
        return true;
    }

    private static boolean containsWord(final String run, final String key) {
        return run.indexOf(' ') >= 0
                && Arrays.stream(run.split(" ")).map(Occurrences::keyOf).anyMatch(key::equals);
    }

    /**
     * The glossary entries a scan proposes, leaving out a term the glossary holds or the person removed. Case variants
     * of one name ({@code Hale}, {@code HALE}) share an entry id, so only the highest-ranked one is proposed.
     *
     * @param projectId the project whose glossary is read; never null
     * @param segments the segments to scan; never null
     * @param sourceLanguage the BCP 47 tag of the book's language, or null when it is not known
     * @param glossary where the entries and the removal memory are read; nothing is written
     * @return unlocked entries with no target, type other and gender unknown, in candidate order; or the read's error
     */
    public static Result<List<GlossaryEntry>> newTerms(
            final String projectId,
            final List<Segment> segments,
            @Nullable final String sourceLanguage,
            final GlossaryRepository glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(glossary, "glossary");
        return glossary.all(projectId)
                .flatMap(held -> unheldEntries(
                        projectId, candidates(segments, PROPOSAL_MIN_COUNT, sourceLanguage), glossary, held));
    }

    private static Result<List<GlossaryEntry>> unheldEntries(
            final String projectId,
            final List<NameCandidate> candidates,
            final GlossaryRepository glossary,
            final List<GlossaryEntry> held) {
        final Set<String> heldTerms = keysOf(held);
        final List<GlossaryEntry> proposals = new ArrayList<>();
        int skippedHeld = 0;
        int skippedRemoved = 0;
        for (final NameCandidate candidate : candidates) {
            final String lowerCased = GlossaryKeys.of(candidate.term());
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

    private static Set<String> keysOf(final List<GlossaryEntry> entries) {
        final Set<String> terms = new HashSet<>();
        entries.forEach(entry -> terms.add(GlossaryKeys.of(entry.term())));
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
