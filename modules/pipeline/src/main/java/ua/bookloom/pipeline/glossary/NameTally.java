package ua.bookloom.pipeline.glossary;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.prompt.NounPhrases;

/**
 * Counts the name scan's runs: every capitalised run of the segments, and the names two runs make joined by a joining
 * word of the language with at most one determiner after it (15i.N8: {@code Tower of Arlen} and {@code Keeper of the
 * Grey Gate} would be lost as two runs), and the firms written as a run, a connecting word and a firm tail ({@code
 * Pell and Sons}). Each is counted where it is written; the two runs are still counted alone.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameTally {

    /**
     * The runs of some segments by term, in order of first occurrence.
     *
     * @param read the segments' scan reads; never null
     * @param nameLike whether a word may be (part of) a name
     * @param language the source language tag; null or a language with no joining words joins nothing
     * @return the candidates with their counts and first sentences; never null
     */
    static Map<String, NameCandidate> of(
            final List<Occurrences.Read> read,
            final Predicate<Occurrences.Word> nameLike,
            @Nullable final String language) {
        final Set<String> joiners = language == null ? Set.of() : NounPhrases.joiners(language);
        final Set<String> determiners = language == null ? Set.of() : NounPhrases.determiners(language);
        final Set<String> firmJoiners = language == null ? Set.of() : NounPhrases.firmJoiners(language);
        final Set<String> firmTails = language == null ? Set.of() : NounPhrases.firmTails(language);
        final Map<String, NameCandidate> tallies = new LinkedHashMap<>();
        int joined = 0;
        for (final Occurrences.Read segment : read) {
            final List<Occurrences.Occurrence> joins = Occurrences.joinedRuns(segment, nameLike, joiners, determiners);
            final List<Occurrences.Occurrence> firms = Occurrences.firmRuns(segment, nameLike, firmJoiners, firmTails);
            joined += joins.size() + firms.size();
            Occurrences.runs(segment, nameLike).forEach(found -> count(tallies, found));
            joins.forEach(found -> count(tallies, found));
            firms.forEach(found -> count(tallies, found));
        }
        log.debug("Name tally: {} distinct runs, {} uses of names joined by a joining word", tallies.size(), joined);
        return tallies;
    }

    private static void count(final Map<String, NameCandidate> tallies, final Occurrences.Occurrence found) {
        tallies.merge(
                found.term(),
                new NameCandidate(found.term(), 1, found.sentence()),
                (first, another) -> new NameCandidate(first.term(), first.count() + 1, first.firstSentence()));
    }

    /**
     * Whether a name is two runs joined by a word in lower case ({@code Tower of Arlen}); a run is capitalised word by
     * word.
     *
     * @param term the name; never null
     * @return {@code true} if it holds a word in lower case, {@code false} otherwise
     */
    static boolean isJoined(final String term) {
        return Arrays.stream(term.split(" "))
                .filter(word -> !word.isEmpty())
                .anyMatch(word -> Character.isLowerCase(word.codePointAt(0)));
    }

    /**
     * Whether a run of two words or more holds a word.
     *
     * @param run the run as written; never null
     * @param key the word's {@link Occurrences#keyOf} key
     * @return {@code true} if one of its words has that key, {@code false} otherwise or when the run is one word
     */
    static boolean containsWord(final String run, final String key) {
        return run.indexOf(' ') >= 0
                && Arrays.stream(run.split(" ")).map(Occurrences::keyOf).anyMatch(key::equals);
    }
}
