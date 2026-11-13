package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.pipeline.checks.NameSpelling;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * The two name vetoes of the never-worse gate, read against the book map: a pinned or locked name written in a form
 * the map does not list, and a map name the source holds that the current text has and the candidate dropped.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameVetoes {

    /** The fewest letters a word needs before it is read as a respelt name; shorter words collide by chance. */
    private static final int MIN_NAME_LETTERS = 4;

    /** The most letter edits that turn a listed form into a word still read as that name respelt. */
    private static final int MAX_EDITS = 2;

    /** The letters a respelt name keeps from its rendering's start, so two different names never read as one. */
    private static final int SHARED_PREFIX = 2;

    /**
     * How many words of a text are a pinned or locked name written in a form the map does not list.
     *
     * @param text the non-null display text
     * @param facts the window's names and target language
     * @return the number of such words; zero when the map holds no pinned or locked name
     */
    static long foreignForms(final String text, final WindowFacts facts) {
        return respelt(text, facts).count();
    }

    /**
     * Each word of a text that is a pinned or locked name written in a form the map does not list, with the rendering
     * it was read as, so a refusal can name both.
     *
     * @param text the non-null display text
     * @param facts the window's names and target language
     * @return lines such as {@code Зотової is not a form of Зотов}, in text order; never null
     */
    static List<String> foreignFormNotes(final String text, final WindowFacts facts) {
        return respelt(text, facts)
                .map(word -> facts.names().stream()
                        .filter(name -> isRespelt(word, name))
                        .findFirst()
                        .map(name -> word + " is not a form of " + name.target())
                        .orElse(word))
                .toList();
    }

    private static Stream<String> respelt(final String text, final WindowFacts facts) {
        final Set<String> lowerCase = NameSpelling.lowerCaseWords(List.of(text));
        return NameSpelling.CAPITALISED
                .matcher(text)
                .results()
                .map(MatchResult::group)
                .filter(word -> word.codePointCount(0, word.length()) >= MIN_NAME_LETTERS)
                .filter(word -> !NameSpelling.isCommonWord(word, facts.targetLanguage(), lowerCase))
                .filter(word -> facts.names().stream().anyMatch(name -> isRespelt(word, name)));
    }

    /**
     * Whether the candidate dropped a map name the source holds and the current text writes.
     *
     * @param old the non-null current display text
     * @param fresh the non-null candidate display text
     * @param source the non-null source display text
     * @param facts the window's names
     * @return {@code true} if such a name is gone, {@code false} otherwise
     */
    static boolean dropsName(final String old, final String fresh, final String source, final WindowFacts facts) {
        return facts.names().stream()
                .filter(name -> name.sourceNames().stream().anyMatch(term -> TermMatch.isNamedIn(term, source)))
                .anyMatch(name -> writes(old, name) && !writes(fresh, name));
    }

    private static boolean isRespelt(final String word, final NameFact name) {
        if (name.state() == TargetState.SUGGESTED || name.forms().contains(word)) {
            return false;
        }
        final String lower = word.toLowerCase(Locale.ROOT);
        return name.forms().stream()
                .map(form -> form.toLowerCase(Locale.ROOT))
                .filter(form -> !form.equals(lower))
                .anyMatch(form -> sharesPrefix(form, lower) && edits(form, lower) <= MAX_EDITS);
    }

    private static boolean writes(final String text, final NameFact name) {
        return name.forms().stream()
                .anyMatch(form -> wholeWord(form).matcher(text).find());
    }

    private static Pattern wholeWord(final String form) {
        return Pattern.compile("(?<![\\p{L}\\p{M}])" + Pattern.quote(form) + "(?![\\p{L}\\p{M}])");
    }

    private static boolean sharesPrefix(final String form, final String word) {
        return form.length() >= SHARED_PREFIX
                && word.length() >= SHARED_PREFIX
                && form.regionMatches(0, word, 0, SHARED_PREFIX);
    }

    // The Levenshtein distance, one row at a time.
    private static int edits(final String from, final String to) {
        int[] previous = new int[to.length() + 1];
        for (int at = 0; at <= to.length(); at++) {
            previous[at] = at;
        }
        for (int row = 1; row <= from.length(); row++) {
            final int[] current = new int[to.length() + 1];
            current[0] = row;
            for (int at = 1; at <= to.length(); at++) {
                final int substitution = previous[at - 1] + (from.charAt(row - 1) == to.charAt(at - 1) ? 0 : 1);
                current[at] = Math.min(substitution, Math.min(previous[at], current[at - 1]) + 1);
            }
            previous = current;
        }
        return previous[to.length()];
    }
}
