package ua.bookloom.pipeline.glossary;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts a Cyrillic letter back where a small model wrote its Latin look-alike inside a Cyrillic word: gemma4:e4b wrote
 * "Вeнс" with a Latin {@code e} for "Vance" at every temperature tried (2026-10-02). The word reads right and matches
 * nothing, so it is repaired rather than refused — but only inside a word that already holds a Cyrillic letter, so a
 * Latin word stays Latin.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LookAlikes {

    // Only the letters every Cyrillic alphabet shares a shape with; Latin "i" has a twin only in some of them.
    private static final Map<Character, Character> CYRILLIC = Map.ofEntries(
            Map.entry('a', 'а'),
            Map.entry('c', 'с'),
            Map.entry('e', 'е'),
            Map.entry('o', 'о'),
            Map.entry('p', 'р'),
            Map.entry('x', 'х'),
            Map.entry('y', 'у'),
            Map.entry('A', 'А'),
            Map.entry('B', 'В'),
            Map.entry('C', 'С'),
            Map.entry('E', 'Е'),
            Map.entry('H', 'Н'),
            Map.entry('K', 'К'),
            Map.entry('M', 'М'),
            Map.entry('O', 'О'),
            Map.entry('P', 'Р'),
            Map.entry('T', 'Т'),
            Map.entry('X', 'Х'));

    /**
     * The text with each Latin look-alike inside a word that also holds a Cyrillic letter replaced by its Cyrillic twin.
     *
     * @param text the non-null text
     * @return the repaired text, or {@code text} itself when no word mixes the two alphabets
     */
    static String repaired(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && Character.isLetter(text.charAt(end))) {
                end++;
            }
            out.append(word(text.substring(start, end)));
            if (end < text.length()) {
                out.append(text.charAt(end));
            }
            start = end + 1;
        }
        final String repaired = out.toString();
        if (!repaired.equals(text)) {
            log.debug("Repaired Latin look-alike letters inside Cyrillic words of a suggested target");
        }
        return repaired;
    }

    private static String word(final String word) {
        final boolean cyrillic =
                word.chars().anyMatch(letter -> Character.UnicodeScript.of(letter) == Character.UnicodeScript.CYRILLIC);
        if (!cyrillic) {
            return word;
        }
        final StringBuilder out = new StringBuilder(word.length());
        word.chars().forEach(letter -> out.append(CYRILLIC.getOrDefault((char) letter, (char) letter)));
        return out.toString();
    }
}
