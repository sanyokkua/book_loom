package ua.bookloom.pipeline.glossary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

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

    private static final int MIN_WORD_LETTERS = 3;
    private static final String TITLES_DIRECTORY = "titles/";
    private static final String FALLBACK_LANGUAGE = "en";

    /**
     * The recurring terms of the segments.
     *
     * @param segments the non-null segments to read, by their masked text
     * @param sourceLanguage the book's BCP 47 tag, choosing the title and stop-word lists; null reads English
     * @return the terms in lower case with their counts, most frequent first, at most {@link #MAX_PROPOSALS}; never
     *     null, empty when none qualifies
     */
    public static List<NameCandidate> candidates(final List<Segment> segments, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(segments, "segments");
        final List<Occurrences.Read> read = ScanText.of(segments, sourceLanguage).stream()
                .map(Occurrences::read)
                .toList();
        final WordCounts counts = WordCounts.of(read);
        final Set<String> stopWords = StopWords.of(sourceLanguage);
        final Set<String> titles = titles(sourceLanguage);
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
     * The lexicon entries a scan proposes, leaving out a term the glossary or the lexicon already holds.
     *
     * @param projectId the non-null project whose stores are read; nothing is written
     * @param segments the non-null segments to scan
     * @param sourceLanguage the book's BCP 47 tag, or null when unknown
     * @param glossary where the held glossary terms are read
     * @param lexicon where the held lexicon terms are read
     * @return an entry with no rendering for each new term, in candidate order; or a read's error
     */
    public static Result<List<LexiconEntry>> newTerms(
            final String projectId,
            final List<Segment> segments,
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
                    final List<LexiconEntry> proposals = candidates(segments, sourceLanguage).stream()
                            .filter(candidate -> !held.contains(LexiconEntry.keyOf(candidate.term())))
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
     * Whether a term is a title of the source language's bundled list, which a book may write with a capital only.
     *
     * @param term the non-null term
     * @param sourceLanguage the book's BCP 47 tag, or null to read English
     * @return {@code true} if the list holds the term, {@code false} otherwise
     */
    public static boolean isTitle(final String term, @Nullable final String sourceLanguage) {
        Objects.requireNonNull(term, "term");
        return titles(sourceLanguage).contains(Occurrences.keyOf(term));
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

    private static Set<String> titles(@Nullable final String sourceLanguage) {
        final String language = sourceLanguage == null || sourceLanguage.isBlank()
                ? FALLBACK_LANGUAGE
                : Locale.forLanguageTag(sourceLanguage.strip()).getLanguage();
        final InputStream stream = KeyTermScan.class.getResourceAsStream(TITLES_DIRECTORY + language + ".txt");
        if (stream == null) {
            log.debug("No bundled title list for language {}", language);
            return Set.of();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(Occurrences::keyOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException cause) {
            throw new UncheckedIOException("The bundled title list for " + language + " could not be read", cause);
        }
    }
}
