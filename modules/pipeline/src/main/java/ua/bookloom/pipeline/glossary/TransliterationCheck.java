package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a rendering carries every capitalised word of a name, and under Transliterate whether each starts the way the
 * source transliterates (15i.N6, 15j.J4): the opening letters of a source word map by the target language's letter
 * table to the opening letters of one of the rendering's words, in any order, so a translated common word that stands
 * lower-case ({@code місто} for {@code City}) takes the place of a word the table cannot match. A table, not a
 * dictionary: it refuses {@code Карло} for {@code Arlo} and cannot judge the rest of a word.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransliterationCheck {

    private static final Pattern WORD_BREAK = Pattern.compile("[\\s-]+");
    private static final int LONGEST_KEY = 2;

    /** What is wrong with a rendering. */
    public enum Problem {
        /** A capitalised word of the name has no word in the rendering. */
        LOST_WORD,
        /** A word of the rendering opens with a letter the source word cannot give, or is no word of the name. */
        OPENING
    }

    /**
     * Checks a rendering against its source name, judging the opening of every word.
     *
     * @param term the source name; never null
     * @param rendering the suggested rendering; never null
     * @param table the target language's letter table; never null, empty when the language has none
     * @return {@code true} if {@link #problem} finds none; {@code false} otherwise
     */
    public static boolean holds(final String term, final String rendering, final Map<String, List<String>> table) {
        return problem(term, rendering, table, true).isEmpty();
    }

    /**
     * What, if anything, is wrong with a rendering of a name.
     *
     * @param term the source name; never null
     * @param rendering the suggested rendering; never null
     * @param table the target language's letter table; never null, empty when the language has none
     * @param judgesOpening whether the opening letters are judged (Transliterate); otherwise only the words are counted
     * @return the problem, or empty when every capitalised word of the name has a word in the rendering and, when the
     *     opening is judged, each opens as the table allows and none is left over
     */
    public static Optional<Problem> problem(
            final String term,
            final String rendering,
            final Map<String, List<String>> table,
            final boolean judgesOpening) {
        return problem(term, rendering, table, judgesOpening, Set.of());
    }

    /**
     * What, if anything, is wrong with a rendering of a name whose leading article is no word of it.
     *
     * @param term the source name; never null
     * @param rendering the suggested rendering; never null
     * @param table the target language's letter table; never null, empty when the language has none
     * @param judgesOpening whether the opening letters are judged (Transliterate); otherwise only the words are counted
     * @param articles the source language's articles in lower case; a name of two words or more that opens with one
     *     (The Hague) is judged without it; never null
     * @return the problem, or empty when there is none
     */
    public static Optional<Problem> problem(
            final String term,
            final String rendering,
            final Map<String, List<String>> table,
            final boolean judgesOpening,
            final Set<String> articles) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(articles, "articles");
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(table, "table");
        final List<String> source = withoutArticle(wordsOf(term), articles).stream()
                .filter(TransliterationCheck::isCapitalised)
                .toList();
        final List<String> target = new ArrayList<>(wordsOf(rendering));
        final List<String> unmatched = new ArrayList<>();
        final List<String> unjudged = new ArrayList<>();
        for (final String word : source) {
            final Optional<List<String>> allowed = judgesOpening ? allowedOpenings(word, table) : Optional.empty();
            if (allowed.isEmpty()) {
                unjudged.add(word);
            } else if (!takeOpening(allowed.get(), target)) {
                unmatched.add(word);
            }
        }
        final Optional<Problem> problem = settle(unjudged, unmatched, target, judgesOpening);
        log.debug(
                "Word check: {} capitalised word(s), {} rendering word(s), problem {}",
                source.size(),
                wordsOf(rendering).size(),
                problem);
        log.trace("Word check: '{}' against '{}'", term, rendering);
        return problem;
    }

    private static List<String> withoutArticle(final List<String> words, final Set<String> articles) {
        final boolean leads = words.size() > 1 && articles.contains(words.get(0).toLowerCase(Locale.ROOT));
        return leads ? words.subList(1, words.size()) : words;
    }

    // What is left after the matching: a word with no word to stand for it is lost; a word that only a capitalised
    // word is left for opens wrongly; a lower-case word left over is a translated common word and stands for one.
    private static Optional<Problem> settle(
            final List<String> unjudged,
            final List<String> unmatched,
            final List<String> target,
            final boolean judgesOpening) {
        for (String _ : unjudged) {
            if (target.isEmpty()) {
                return Optional.of(Problem.LOST_WORD);
            }
            target.remove(0);
        }
        for (String _ : unmatched) {
            if (target.isEmpty()) {
                return Optional.of(Problem.LOST_WORD);
            }
            final Optional<String> common =
                    target.stream().filter(word -> !isCapitalised(word)).findFirst();
            if (common.isEmpty()) {
                return Optional.of(Problem.OPENING);
            }
            target.remove(common.get());
        }
        final boolean extraName = judgesOpening && target.stream().anyMatch(TransliterationCheck::isCapitalised);
        return extraName ? Optional.of(Problem.OPENING) : Optional.empty();
    }

    // The letters a source word may open a rendering's word with; empty when the table has no row for it.
    private static Optional<List<String>> allowedOpenings(
            final String sourceWord, final Map<String, List<String>> table) {
        final String source = sourceWord.toLowerCase(Locale.ROOT);
        for (int length = Math.min(LONGEST_KEY, source.length()); length >= 1; length--) {
            final List<String> allowed = table.get(source.substring(0, length));
            if (allowed != null) {
                return Optional.of(allowed);
            }
        }
        return Optional.empty();
    }

    private static boolean takeOpening(final List<String> allowed, final List<String> target) {
        for (int index = 0; index < target.size(); index++) {
            final String word = target.get(index);
            final String opening =
                    word.substring(0, word.offsetByCodePoints(0, 1)).toLowerCase(Locale.ROOT);
            if (allowed.contains(opening)) {
                target.remove(index);
                return true;
            }
        }
        return false;
    }

    // The words of a name, split at spaces and hyphens, each without the quotes or marks before its first letter.
    private static List<String> wordsOf(final String name) {
        return WORD_BREAK
                .splitAsStream(name.strip())
                .map(TransliterationCheck::fromFirstLetter)
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String fromFirstLetter(final String word) {
        int start = 0;
        while (start < word.length() && !Character.isLetterOrDigit(word.codePointAt(start))) {
            start += Character.charCount(word.codePointAt(start));
        }
        return word.substring(start);
    }

    private static boolean isCapitalised(final String word) {
        final int first = word.codePointAt(0);
        return Character.isUpperCase(first) || Character.isDigit(first);
    }
}
