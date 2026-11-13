package ua.bookloom.llm.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Splits a model key or row pattern into comparable tokens, so that {@code qwen2.5}, {@code qwen-2.5} and
 * {@code phi4}/{@code phi-4} meet however a name was spelled.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameKey {

    private static final Pattern SEPARATORS = Pattern.compile("[-:]");

    /**
     * Tokens of a key.
     *
     * @param key a lower-case name key; "-" and ":" separate, and a letter followed by a digit starts a new token
     * @return the non-empty tokens in order
     */
    static List<String> tokens(final String key) {
        final List<String> tokens = new ArrayList<>();
        SEPARATORS.splitAsStream(key.toLowerCase(Locale.ROOT)).forEach(part -> splitAtLetterToDigit(part, tokens));
        return tokens;
    }

    private static void splitAtLetterToDigit(final String part, final List<String> into) {
        int start = 0;
        for (int i = 1; i < part.length(); i++) {
            if (Character.isLetter(part.charAt(i - 1)) && Character.isDigit(part.charAt(i))) {
                into.add(part.substring(start, i));
                start = i;
            }
        }
        if (start < part.length()) {
            into.add(part.substring(start));
        }
    }
}
