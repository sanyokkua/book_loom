package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.WordCounts;
import ua.bookloom.pipeline.prompt.NounPhrases;
import ua.bookloom.util.lang.Languages;

/**
 * Names that open with a stop word written as part of the name ({@code Other Shore}, 15i.N8). A stop word never
 * starts a run, because a book writes function words with a capital at every sentence start; but one the book also
 * writes with a capital away from a sentence start, mostly inside one run, is a word of that name. The run needs
 * {@link #MIN_COUNT} uses, and the run must be at least {@link #MIN_SHARE_PERCENT} percent of the stop word's capitals
 * away from a sentence start, so a pronoun or an article a book capitalises in headings and titles opens no name. The
 * book must also write the stop word in lower case ({@link FrequencyScan#LOWER_SHARE_LIMIT}): a word it writes only
 * with a capital is an honorific ({@code Herr}, {@code Don}), which never starts a name.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StopWordHeads {

    /** The fewest times a run that opens with a stop word is written before it is a name. */
    static final int MIN_COUNT = 3;

    /** The share of the stop word's capitals away from a sentence start that its run must hold, in percent. */
    static final int MIN_SHARE_PERCENT = 50;

    private static final int PERCENT = 100;

    /**
     * Adds the runs that open with a stop word to the tallies.
     *
     * @param tallies the runs counted so far; never null; the result is this map
     * @param read the segments' scan reads; never null
     * @param counts the book's word counts; never null
     * @param stopWords the language's stop words, lower case; never null
     * @param language the BCP 47 tag of the book's language, whose articles never open a name; null reads every bundled language's
     * @return {@code tallies}, with every qualifying run added
     */
    static Map<String, NameCandidate> add(
            final Map<String, NameCandidate> tallies,
            final List<Occurrences.Read> read,
            final WordCounts counts,
            final Set<String> stopWords,
            @Nullable final String language) {
        final Set<String> articles = articlesOf(language);
        final Map<String, NameCandidate> heads = new LinkedHashMap<>();
        for (final Occurrences.Read segment : read) {
            Occurrences.runs(segment, StopWordHeads::isHeadLike).stream()
                    .filter(found ->
                            found.term().indexOf(' ') > 0 && startsWithStopWord(found.term(), stopWords, articles))
                    .forEach(found -> heads.merge(
                            found.term(),
                            new NameCandidate(found.term(), 1, found.sentence()),
                            (first, another) ->
                                    new NameCandidate(first.term(), first.count() + 1, first.firstSentence())));
        }
        heads.values().stream()
                .filter(head -> isMostOfItsWordsCapitals(head, counts))
                .forEach(head -> {
                    log.debug("Name candidate {} kept: a stop word written as part of a name", head.term());
                    tallies.putIfAbsent(head.term(), head);
                });
        return tallies;
    }

    // The stop words come from every bundled list when the language is unknown or has none, so do the articles.
    private static Set<String> articlesOf(@Nullable final String language) {
        if (language != null && StopWords.bundled(language).isPresent()) {
            return NounPhrases.articles(language);
        }
        return Languages.all().stream()
                .flatMap(known -> NounPhrases.articles(known.tag()).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isHeadLike(final Occurrences.Word word) {
        return word.capitalised() && !word.initial();
    }

    private static boolean startsWithStopWord(
            final String term, final Set<String> stopWords, final Set<String> articles) {
        final String head = Occurrences.keyOf(term.substring(0, term.indexOf(' ')));
        return stopWords.contains(head) && !articles.contains(head);
    }

    private static boolean isMostOfItsWordsCapitals(final NameCandidate head, final WordCounts counts) {
        final String key =
                Occurrences.keyOf(head.term().substring(0, head.term().indexOf(' ')));
        final int capitals = counts.midSentence(key);
        final boolean kept = head.count() >= MIN_COUNT
                && head.count() * PERCENT >= capitals * MIN_SHARE_PERCENT
                && counts.lowerShare(key) >= FrequencyScan.LOWER_SHARE_LIMIT;
        log.debug(
                "Stop-word head {}: {} uses, {} capitals of '{}' away from a sentence start, kept {}",
                head.term(),
                head.count(),
                capitals,
                key,
                kept);
        return kept;
    }
}
