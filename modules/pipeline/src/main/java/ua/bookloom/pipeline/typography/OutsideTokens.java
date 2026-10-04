package ua.bookloom.pipeline.typography;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;

/**
 * Applies a pattern to the stretches of a masked text between its {@code ⟦gN⟧} tokens, each on its own. A stretch
 * ends where a token begins, so a lookbehind or lookahead can never reach across one: a character next to a token
 * may be a tag's whitespace, and a code span or protected name behind a token is never read at all.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OutsideTokens {

    static Edit replaceAll(final String text, final Pattern pattern, final String replacement) {
        final Matcher tokens = Tokens.matcher(text);
        final StringBuilder out = new StringBuilder(text.length());
        int cursor = 0;
        int count = 0;
        while (tokens.find()) {
            count += appendReplaced(text.substring(cursor, tokens.start()), pattern, replacement, out);
            out.append(tokens.group());
            cursor = tokens.end();
        }
        count += appendReplaced(text.substring(cursor), pattern, replacement, out);
        return new Edit(out.toString(), count);
    }

    private static int appendReplaced(
            final String stretch, final Pattern pattern, final String replacement, final StringBuilder out) {
        final Matcher matcher = pattern.matcher(stretch);
        int count = 0;
        int cursor = 0;
        while (matcher.find()) {
            out.append(stretch, cursor, matcher.start()).append(replacement);
            cursor = matcher.end();
            count++;
        }
        out.append(stretch, cursor, stretch.length());
        return count;
    }
}
