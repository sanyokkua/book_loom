package ua.bookloom.pipeline.glossary;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.WordCounts;

/**
 * The deterministic scan for recurring common terms and titles — {@code master}, {@code imp}, {@code Mr} — that a model
 * renders differently from one page to the next. It reads the same running text as the name scan and looks at what the
 * name scan throws away: a title from the language's bundled list that the book uses often enough, and a word the book
 * writes both with a capital away from a sentence start (an address, a title) and in lower case, so it is no name and
 * no function word either. Nothing is proposed that the glossary or the lexicon holds, and only the most frequent few
 * are, since every term costs the model a place in each batch reply.
 *
 * <p>A word that is only ever lower case, such as {@code pentacle}, has nothing to tell it from any other lower-case
 * word without a frequency reference the app does not carry, so it is added by hand on Names &amp; style.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KeyTermScan {

    /** How often a bundled title must occur before it is proposed. */
    static final int TITLE_MIN_COUNT = 3;

    /** How often a dual-use word must occur before it is proposed. */
    static final int DUAL_USE_MIN_COUNT = 5;

    /** How often a dual-use word must be written with a capital mid-sentence before it is proposed. */
    static final int MID_SENTENCE_MIN_COUNT = 3;

    /** The most terms one scan proposes. */
    static final int MAX_PROPOSALS = 30;

    /** The fewest uses a word needs before it is shown to the model as a candidate. */
    static final int FREQUENT_MIN_COUNT = 6;

    private static final int FREQUENT_MIN_LETTERS = 4;
    private static final int MIN_WORD_LETTERS = 3;

    /**
     * The recurring terms of some of the book's segments, read from the book's index.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param sourceLanguage the book's BCP 47 tag, choosing the title and stop-word lists; null reads every bundled list together
     * @return the terms in lower case with their counts, most frequent first, at most {@link #MAX_PROPOSALS}; never
     *     null, empty when none qualifies
     */
    public static List<NameCandidate> candidates(
            final BookIndex index, final List<Integer> ordinals, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(index, "index");
        final List<Occurrences.Read> read = index.scanReads(ordinals, sourceLanguage);
        final WordCounts counts = WordCounts.of(read);
        final Set<String> stopWords = StopWords.of(sourceLanguage);
        final Set<String> titles = StopWords.titles(sourceLanguage);
        final Map<String, NameCandidate> seen = tally(read);
        final List<NameCandidate> kept = seen.values().stream()
                .filter(tally -> qualifies(tally, counts, stopWords, titles))
                .sorted(Comparator.comparingInt(NameCandidate::count).reversed())
                .limit(MAX_PROPOSALS)
                .toList();
        log.debug("Key-term scan counted {} words, {} proposed", seen.size(), kept.size());
        kept.forEach(candidate -> log.trace("Key term {} x{}", candidate.term(), candidate.count()));
        return kept;
    }

    /**
     * The words of the segments the model may choose recurring terms among: every word the book writes mostly in lower
     * case and often enough, that is no function word. The model, not a frequency list, decides which are terms.
     *
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to read, in reading order; never null
     * @param sourceLanguage the book's BCP 47 tag, choosing the stop-word list; null reads every bundled list together
     * @param limit the most words returned; positive
     * @return the words in lower case with their counts, most frequent first; never null
     */
    public static List<NameCandidate> frequentWords(
            final BookIndex index,
            final List<Integer> ordinals,
            @Nullable final String sourceLanguage,
            final int limit) {
        Objects.requireNonNull(index, "index");
        final List<Occurrences.Read> read = index.scanReads(ordinals, sourceLanguage);
        final WordCounts counts = WordCounts.of(read);
        final Set<String> stopWords = StopWords.of(sourceLanguage);
        final List<NameCandidate> frequent = tally(read).values().stream()
                .filter(tally -> tally.count() >= FREQUENT_MIN_COUNT)
                .filter(tally -> tally.term().codePointCount(0, tally.term().length()) >= FREQUENT_MIN_LETTERS)
                .filter(tally -> !stopWords.contains(tally.term()))
                .filter(tally -> counts.lowerShare(tally.term()) >= FrequencyScan.LOWER_SHARE_LIMIT)
                .sorted(Comparator.comparingInt(NameCandidate::count).reversed())
                .limit(limit)
                .toList();
        log.debug("Frequent words {} of {} kept", frequent.size(), read.size());
        return frequent;
    }

    /**
     * The lexicon entries a scan proposes, leaving out a term the glossary or the lexicon already holds.
     *
     * @param projectId the non-null project whose stores are read; nothing is written
     * @param index the project's book index; never null
     * @param ordinals the ordinals of the segments to scan, in reading order; never null
     * @param sourceLanguage the book's BCP 47 tag, or null when unknown
     * @param glossary where the held glossary terms are read
     * @param lexicon where the held lexicon terms are read
     * @return an entry with no rendering for each new term, in candidate order; or a read's error
     */
    public static Result<List<LexiconEntry>> newTerms(
            final String projectId,
            final BookIndex index,
            final List<Integer> ordinals,
            @Nullable final String sourceLanguage,
            final GlossaryRepository glossary,
            final LexiconRepository lexicon) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(lexicon, "lexicon");
        return glossary.all(projectId)
                .flatMap(heldNames -> lexicon.all(projectId).map(heldTerms -> {
                    final Set<String> held = new HashSet<>();
                    heldNames.forEach(entry -> held.add(LexiconEntry.keyOf(entry.term())));
                    heldTerms.forEach(entry -> held.add(LexiconEntry.keyOf(entry.term())));
                    final List<LexiconEntry> proposals = candidates(index, ordinals, sourceLanguage).stream()
                            .filter(candidate -> !held.contains(LexiconEntry.keyOf(candidate.term())))
                            .filter(candidate -> !wasRemoved(lexicon, projectId, candidate.term()))
                            .map(candidate -> LexiconEntry.of(projectId, candidate.term()))
                            .toList();
                    log.debug(
                            "Key-term proposals project={} proposed={} held={}",
                            projectId,
                            proposals.size(),
                            held.size());
                    return proposals;
                }));
    }

    /**
     * Whether the lexicon remembers a term as removed this session, by the person or by a model's drop, so a scan
     * does not add it back.
     *
     * @param lexicon where the removals are remembered; never null
     * @param projectId the project id; never null
     * @param term the term; never null
     * @return {@code true} when the term was removed and not stored again since; {@code false} otherwise, including
     *     when the lexicon cannot answer
     */
    public static boolean wasRemoved(final LexiconRepository lexicon, final String projectId, final String term) {
        final boolean removed =
                Boolean.TRUE.equals(lexicon.wasRemoved(projectId, term).data());
        if (removed) {
            log.debug("Key-term scan skips a term removed this session project={}", projectId);
            log.trace("Key-term scan skips removed term {}", term);
        }
        return removed;
    }

    /**
     * Whether a term is a title of the source language's bundled list, which a book may write with a capital only.
     *
     * @param term the non-null term
     * @param sourceLanguage the book's BCP 47 tag, or null when the language is not known
     * @return {@code true} if the list holds the term, {@code false} otherwise
     */
    public static boolean isTitle(final String term, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(term, "term");
        return StopWords.titles(sourceLanguage).contains(Occurrences.keyOf(term));
    }

    private static boolean qualifies(
            final NameCandidate tally, final WordCounts counts, final Set<String> stopWords, final Set<String> titles) {
        final String key = tally.term();
        if (titles.contains(key)) {
            return tally.count() >= TITLE_MIN_COUNT;
        }
        return tally.count() >= DUAL_USE_MIN_COUNT
                && key.codePointCount(0, key.length()) >= MIN_WORD_LETTERS
                && !stopWords.contains(key)
                && counts.standaloneMidSentence(key) >= MID_SENTENCE_MIN_COUNT
                && counts.lowerShare(key) >= FrequencyScan.LOWER_SHARE_LIMIT;
    }

    private static Map<String, NameCandidate> tally(final List<Occurrences.Read> read) {
        final Map<String, NameCandidate> tallies = new LinkedHashMap<>();
        for (final Occurrences.Read segment : read) {
            for (final Occurrences.Word word : segment.words()) {
                final String key = word.key();
                tallies.merge(
                        key,
                        new NameCandidate(key, 1, segment.sentences().get(word.sentence())),
                        (first, another) -> new NameCandidate(key, first.count() + 1, first.firstSentence()));
            }
        }
        return tallies;
    }
}
