package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.WordCounts;
import ua.bookloom.util.text.GlossaryKeys;
import ua.bookloom.util.text.JunkRules;

/**
 * The deterministic name scan: every capitalised word, and every run of two or three, that occurs often enough away
 * from a sentence start in the book's running text ({@link BookIndex#scanReads}) — never in a heading or a title line. It takes whichever segments the caller passes, so one scan serves the whole book before a run
 * and "the segments decided so far" at the end of each chapter.
 *
 * <p>A word the book also writes in lower case often enough ({@link #LOWER_SHARE_LIMIT}) is a common word, not a
 * name, in any language with capitals — unless a names reading follows the scan ({@link #proposals}) and the book also
 * writes it with a capital mid-sentence at least {@link #READING_MIN_MID_SENTENCE} times: then it is kept, marked
 * {@link NameCandidate#readingOnly()}, for the reading to decide (15i.N5: the bartender Mark beside "a mark on the
 * wall"). A word on the language's bundled stop-word list never starts or joins a name.
 * A capitalised word at a sentence start counts when the book writes it with a capital mid-sentence at least
 * {@link #CREDIT_MIN_MID_SENTENCE} times, so a name that often opens a sentence is not undercounted, and a single word
 * that mostly occurs inside a longer name ({@code Garrick} of {@code Garrick Ashdown}) is not proposed on its own,
 * unless the book writes it alone {@link #ALIAS_MIN_ALONE} times or more, or truly alone (mid-sentence, no capital beside
 * it) {@link #ALONE_MIN_STANDALONE} times or more. Two runs joined by a joining word of the language ({@code Tower of
 * London}) are a name too. Spellings that differ only in letter case are folded first ({@link CaseVariants}), a plural
 * written as a common plural is folded or left out ({@link PluralNouns}), and a word written as a common noun or an
 * adjective is not a name ({@link NounUses}); the list ends with the {@link CandidateTriage}, which drops heading-only
 * names and credits and joins a written pair.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FrequencyScan {

    /** How often a name must occur before {@link #newTerms} proposes it. */
    static final int PROPOSAL_MIN_COUNT = 3;

    /** A word written in lower case at least this share of the time is a common word; every real name measured is below. */
    public static final double LOWER_SHARE_LIMIT = 0.2;

    /**
     * A word over {@link #LOWER_SHARE_LIMIT} stays a candidate for the names reading once the book writes it with a
     * capital mid-sentence this often (counted over the whole book, {@link BookIndex#stats}).
     */
    static final int READING_MIN_MID_SENTENCE = 3;

    /** A sentence-initial capitalised word counts once the book writes it with a capital mid-sentence this often. */
    static final int CREDIT_MIN_MID_SENTENCE = 2;

    /** A single word standing alone less than this share of its occurrences is part of a longer name. */
    static final double ALIAS_STANDALONE_LIMIT = 0.4;

    /**
     * A single word the book writes alone at least this often is a name of its own whatever its share, since a
     * segment that writes it alone needs it in the glossary (15i.N gate: a first name written alone 34 times beside
     * 76 uses of the full name).
     */
    static final int ALIAS_MIN_ALONE = 10;

    /**
     * A single word the book writes truly alone (mid-sentence, with no capitalised word beside it) at least this often
     * is a name of its own beside the longer name (15i.N8: surnames written alone two to five times were lost beside
     * their full names, while a part written alone only after a sentence-initial capital stays a part).
     */
    static final int ALONE_MIN_STANDALONE = 2;

    /**
     * Counts the capitalised words and runs of some of the book's segments, read from the book's index.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null, may be empty
     * @param minCount the fewest occurrences a candidate needs; {@code 1} lists every one
     * @param sourceLanguage the BCP 47 tag of the book's language, choosing the stop-word list; null reads every bundled list together
     * @return the candidates by count descending, then by first occurrence; never null, empty when none qualifies
     */
    public static List<NameCandidate> candidates(
            final BookIndex index,
            final List<Integer> ordinals,
            final int minCount,
            @Nullable final String sourceLanguage) {
        return candidates(index, ordinals, minCount, sourceLanguage, false);
    }

    /**
     * Counts the capitalised words and runs of some of the book's segments, keeping for a names reading the common
     * words a reading must decide.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null, may be empty
     * @param minCount the fewest occurrences a candidate needs; {@code 1} lists every one
     * @param sourceLanguage the BCP 47 tag of the book's language, choosing the stop-word list; null reads every bundled list together
     * @param forReading whether a names reading follows, so a common word with {@link #READING_MIN_MID_SENTENCE}
     *     capitals mid-sentence is kept, marked {@link NameCandidate#readingOnly()}, instead of dropped
     * @return the candidates by count descending, then by first occurrence; never null, empty when none qualifies
     */
    static List<NameCandidate> candidates(
            final BookIndex index,
            final List<Integer> ordinals,
            final int minCount,
            @Nullable final String sourceLanguage,
            final boolean forReading) {
        Objects.requireNonNull(index, "index");
        log.debug(
                "Name scan over {} segments, minimum count {}, language {}, for reading {}",
                ordinals.size(),
                minCount,
                sourceLanguage,
                forReading);
        final List<Occurrences.Read> read = index.scanReads(ordinals, sourceLanguage);
        final WordCounts counts = WordCounts.of(read);
        final Map<String, NameCandidate> tallies = cleanTallies(read, counts, sourceLanguage);
        final List<NameCandidate> kept = keep(tallies, counts, forReading ? index : null, minCount, sourceLanguage);
        final List<NameCandidate> candidates = CandidateTriage.apply(index, kept, minCount);
        log.debug(
                "Name scan counted {} distinct names, {} kept at {}, {} after triage",
                tallies.size(),
                kept.size(),
                minCount,
                candidates.size());
        candidates.forEach(candidate ->
                log.trace("Name candidate {} x{}: {}", candidate.term(), candidate.count(), candidate.firstSentence()));
        return candidates;
    }

    private static Map<String, NameCandidate> cleanTallies(
            final List<Occurrences.Read> read, final WordCounts counts, @Nullable final String sourceLanguage) {
        final Set<String> stopWords = StopWords.of(sourceLanguage);
        final List<String> suffixes = NameAliases.suffixesOf(sourceLanguage);
        final NounUses nouns = NounUses.of(read, sourceLanguage);
        final Map<String, NameCandidate> tallies = PluralNouns.fold(
                NameAliases.fold(
                        CaseVariants.fold(StopWordHeads.add(
                                NameTally.of(read, word -> isNameLike(word, counts, stopWords), sourceLanguage),
                                read,
                                counts,
                                stopWords,
                                sourceLanguage)),
                        suffixes),
                nouns,
                suffixes);
        nouns.dropFrom(tallies);
        dropJunk(tallies);
        dropHygiene(tallies, sourceLanguage, read);
        return tallies;
    }

    private static List<NameCandidate> keep(
            final Map<String, NameCandidate> tallies,
            final WordCounts counts,
            @Nullable final BookIndex reading,
            final int minCount,
            @Nullable final String sourceLanguage) {
        final Set<String> neverAlone = StopWords.neverAlone(sourceLanguage);
        return tallies.values().stream()
                .filter(tally -> tally.count() >= minCount)
                .flatMap(tally -> keptByCase(tally, counts, reading, neverAlone).stream())
                .filter(tally -> isNotPartOfLongerName(tally, tallies.values(), minCount, counts))
                .sorted((left, right) -> Integer.compare(right.count(), left.count()))
                .toList();
    }

    // The book's own counts let hygiene keep a plural-shaped name the fold kept as a name of its own. A name two runs
    // make joined by "of" makes neither run a fragment: "Grey Gate" stays beside "Keeper of the Grey Gate", "Arlen"
    // beside "Tower of Arlen" (15i.N8).
    private static void dropHygiene(
            final Map<String, NameCandidate> tallies,
            @Nullable final String language,
            final List<Occurrences.Read> read) {
        final List<String> all = tallies.keySet().stream()
                .filter(term -> !NameTally.isJoined(term))
                .toList();
        final ToIntFunction<String> occurrences = NameHygiene.occurrencesIn(
                read.stream().map(Occurrences.Read::text).toList());
        tallies.keySet()
                .removeIf(term ->
                        NameHygiene.rejection(term, all, language, occurrences).isPresent());
    }

    private static void dropJunk(final Map<String, NameCandidate> tallies) {
        tallies.keySet().removeIf(term -> {
            final boolean junk = JunkRules.isJunk(term);
            if (junk) {
                log.debug("Name candidate dropped as junk, score {}", JunkRules.likelihood(term));
                log.trace("Name candidate {} dropped as junk", term);
            }
            return junk;
        });
    }

    private static boolean isNameLike(
            final Occurrences.Word word, final WordCounts counts, final Set<String> stopWords) {
        final String key = word.key();
        return word.capitalised()
                && !stopWords.contains(key)
                && (!word.initial() || counts.midSentence(key) >= CREDIT_MIN_MID_SENTENCE);
    }

    // A common word by its lower-case share is dropped, unless a reading follows (its book index given) and its
    // mid-sentence capitals make it a name too: then the reading decides, and the candidate says so.
    private static Optional<NameCandidate> keptByCase(
            final NameCandidate tally,
            final WordCounts counts,
            @Nullable final BookIndex reading,
            final Set<String> neverAlone) {
        if (tally.term().indexOf(' ') >= 0) {
            return Optional.of(tally);
        }
        if (neverAlone.contains(Occurrences.keyOf(tally.term()))) {
            log.debug("Name candidate {} dropped: a number or a language name is never a name alone", tally.term());
            return Optional.empty();
        }
        final double share = counts.lowerShare(Occurrences.keyOf(tally.term()));
        if (share < LOWER_SHARE_LIMIT) {
            return Optional.of(tally);
        }
        final int midSentence =
                reading == null ? 0 : reading.stats(tally.term()).capitalisedMidSentence();
        if (midSentence >= READING_MIN_MID_SENTENCE) {
            log.debug(
                    "Name candidate {} kept for the reading: lower case {} of the time, {} capitals mid-sentence",
                    tally.term(),
                    share,
                    midSentence);
            return Optional.of(tally.forReadingOnly());
        }
        log.debug("Name candidate {} dropped: written in lower case {} of the time", tally.term(), share);
        return Optional.empty();
    }

    private static boolean isNotPartOfLongerName(
            final NameCandidate tally,
            final Collection<NameCandidate> all,
            final int minCount,
            final WordCounts counts) {
        if (tally.term().indexOf(' ') >= 0) {
            return true;
        }
        final String key = Occurrences.keyOf(tally.term());
        final List<NameCandidate> longer = all.stream()
                .filter(other -> other.count() >= minCount
                        && !NameTally.isJoined(other.term())
                        && NameTally.containsWord(other.term(), key))
                .toList();
        final int inside = longer.stream().mapToInt(NameCandidate::count).sum();
        final double standalone = (double) tally.count() / (tally.count() + inside);
        if (inside == 0 || standalone >= ALIAS_STANDALONE_LIMIT) {
            return true;
        }
        final int trulyAlone = counts.standaloneMidSentence(key);
        final boolean kept = tally.count() >= ALIAS_MIN_ALONE || trulyAlone >= ALONE_MIN_STANDALONE;
        logPart(tally, longer.getFirst(), standalone, trulyAlone, kept);
        return kept;
    }

    private static void logPart(
            final NameCandidate tally,
            final NameCandidate longer,
            final double standalone,
            final int trulyAlone,
            final boolean kept) {
        log.debug(
                "Name candidate {} part of {}: alone {} of the time, {} times, {} away from any other capital, kept {}",
                tally.term(),
                longer.term(),
                standalone,
                tally.count(),
                trulyAlone,
                kept);
    }

    /**
     * The glossary entries a scan no names reading follows proposes (the run's preparation and its rescans), leaving
     * out a term the glossary holds or the person removed. Case variants of one name ({@code Hale}, {@code HALE})
     * share an entry id, so only the highest-ranked one is proposed.
     *
     * @param projectId the project whose glossary is read; never null
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to scan, in reading order; never null
     * @param sourceLanguage the BCP 47 tag of the book's language, or null when it is not known
     * @param glossary where the entries and the removal memory are read; nothing is written
     * @return unlocked entries with no target, type other and gender unknown, in candidate order; or the read's error
     */
    public static Result<List<GlossaryEntry>> newTerms(
            final String projectId,
            final BookIndex index,
            final List<Integer> ordinals,
            @Nullable final String sourceLanguage,
            final GlossaryRepository glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(ordinals, "ordinals");
        Objects.requireNonNull(glossary, "glossary");
        return glossary.all(projectId)
                .flatMap(held -> unheldEntries(
                        projectId, candidates(index, ordinals, PROPOSAL_MIN_COUNT, sourceLanguage), glossary, held));
    }

    /**
     * The glossary entries a scan the names reading may follow proposes (15i.N5): as {@link #newTerms}, and also the
     * common words a reading must decide, listed apart for the caller to keep where its dial's plan reads them.
     *
     * @param projectId the project whose glossary is read; never null
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to scan, in reading order; never null
     * @param sourceLanguage the BCP 47 tag of the book's language, or null when it is not known
     * @param glossary where the entries and the removal memory are read; nothing is written
     * @return the proposals; or the read's error
     */
    public static Result<ScanProposals> proposals(
            final String projectId,
            final BookIndex index,
            final List<Integer> ordinals,
            @Nullable final String sourceLanguage,
            final GlossaryRepository glossary) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(ordinals, "ordinals");
        Objects.requireNonNull(glossary, "glossary");
        final List<NameCandidate> found = candidates(index, ordinals, PROPOSAL_MIN_COUNT, sourceLanguage, true);
        final Set<String> readingOnly = found.stream()
                .filter(NameCandidate::readingOnly)
                .map(NameCandidate::term)
                .collect(Collectors.toSet());
        return glossary.all(projectId)
                .flatMap(held -> unheldEntries(projectId, found, glossary, held))
                .map(entries -> new ScanProposals(entries, readingOnly));
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
                        false)
                .withAliases(candidate.aliases());
    }
}
