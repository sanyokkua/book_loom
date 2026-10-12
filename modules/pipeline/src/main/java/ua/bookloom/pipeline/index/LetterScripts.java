package ua.bookloom.pipeline.index;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;

/**
 * Counts a text's letters by Unicode script, ignoring placeholder tokens, so the code can tell which writing system a
 * book or a marked run is mostly written in without knowing its language. Hiragana, Katakana and Han count as one
 * script, because Japanese text mixes them.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LetterScripts {

    /**
     * Adds a text's letters to a running count.
     *
     * @param text any text, masked or not; never null
     * @param counts the running count by script; never null
     */
    public static void addTo(final String text, final Map<Character.UnicodeScript, Integer> counts) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(counts, "counts");
        final String visible = Tokens.replace(text, " ");
        visible.codePoints()
                .filter(Character::isLetter)
                .forEach(point -> counts.merge(scriptOf(point), 1, Integer::sum));
    }

    /**
     * The script most of a text's letters are written in.
     *
     * @param text any text, masked or not; never null
     * @return the majority script, or empty when the text holds no letter
     */
    public static Optional<Character.UnicodeScript> majorityOf(final String text) {
        final Map<Character.UnicodeScript, Integer> counts = new EnumMap<>(Character.UnicodeScript.class);
        addTo(text, counts);
        return majorityOf(counts);
    }

    /**
     * The script with the most letters in a count.
     *
     * @param counts letters per script; never null
     * @return the script with the most letters, the first declared one on a tie, or empty when there are none
     */
    public static Optional<Character.UnicodeScript> majorityOf(final Map<Character.UnicodeScript, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        return counts.entrySet().stream()
                .max(Map.Entry.<Character.UnicodeScript, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey);
    }

    private static Character.UnicodeScript scriptOf(final int point) {
        final Character.UnicodeScript script = Character.UnicodeScript.of(point);
        return script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
                ? Character.UnicodeScript.HAN
                : script;
    }
}
