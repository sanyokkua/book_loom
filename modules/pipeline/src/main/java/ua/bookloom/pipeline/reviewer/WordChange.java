package ua.bookloom.pipeline.reviewer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What an edit does to the words of its quote, read word by word: whether only the endings changed, whether a function
 * word came or went, and whether the words it brings are ones the text already holds. A small model under the label
 * "agreement" drops {@code би} or swaps a verb for another; the label is then a false alarm.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WordChange {

    /** The share of the shorter word a changed word must keep as its shared stem. */
    private static final double MIN_STEM_SHARE = 0.6;

    /** A word this short (йшов, йшла) cannot keep 60% of its stem and still change its ending: two letters do. */
    private static final int SHORT_WORD = 4;

    private static final int SHORT_WORD_STEM = 2;

    private static final int MIN_CONTENT_STEM = 3;

    /** A pronoun (він → вона) shares no stem with its other gender; the function-word rule watches particles. */
    private static final int PRONOUN = 3;

    /**
     * The reflexive particle Ukrainian and Russian verbs end in (дивився, дивилась). A gender fix changes the ending
     * in front of it (дивився → дивилася), so the particle is set aside before the stems are compared; otherwise the
     * longer word's 60% reaches into the changed ending.
     */
    private static final Pattern REFLEXIVE = Pattern.compile("(?<=\\p{L}{3})(?:ся|сь)$");

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]+");

    static List<String> words(final String text) {
        return Arrays.stream(NON_LETTERS.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    /** Whether the replacement has the quote's words in the same places, each changed one sharing most of its stem. */
    static boolean changesOnlyEndings(final String quote, final String replacement) {
        final List<String> before = words(quote);
        final List<String> after = words(replacement);
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (!sharesStem(before.get(i), after.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the function words of the quote and the replacement differ; never when the language lists none. */
    static boolean changesFunctionWords(final String quote, final String replacement, final Set<String> functionWords) {
        if (functionWords.isEmpty()) {
            return false;
        }
        return !function(quote, functionWords).equals(function(replacement, functionWords));
    }

    /** Whether the replacement brings in at most one new word, or only words the known text already holds. */
    static boolean bringsInKnownWords(final String quote, final String replacement, final String known) {
        final List<String> quoted = words(quote);
        final List<String> knownWords = words(known);
        final List<String> brought = words(replacement).stream()
                .filter(word -> !quoted.contains(word))
                .toList();
        return brought.size() <= 1 || knownWords.containsAll(brought);
    }

    /** Whether two words begin with the same stem: most of the shorter word, at least three letters. */
    static boolean sharesContentStem(final String first, final String second) {
        int common = 0;
        final int shorter = Math.min(first.length(), second.length());
        while (common < shorter && first.charAt(common) == second.charAt(common)) {
            common++;
        }
        return common >= Math.min(shorter, Math.max(MIN_CONTENT_STEM, Math.ceil(MIN_STEM_SHARE * shorter)));
    }

    private static List<String> function(final String text, final Set<String> functionWords) {
        return words(text).stream().filter(functionWords::contains).sorted().toList();
    }

    private static boolean sharesStem(final String quoted, final String replaced) {
        if (quoted.equals(replaced)) {
            return true;
        }
        final String before = withoutReflexive(quoted);
        final String after = withoutReflexive(replaced);
        int common = 0;
        final int shorter = Math.min(before.length(), after.length());
        while (common < shorter && before.charAt(common) == after.charAt(common)) {
            common++;
        }
        return shorter <= PRONOUN || common >= (shorter <= SHORT_WORD ? SHORT_WORD_STEM : MIN_STEM_SHARE * shorter);
    }

    private static String withoutReflexive(final String word) {
        return REFLEXIVE.matcher(word).replaceFirst("");
    }
}
