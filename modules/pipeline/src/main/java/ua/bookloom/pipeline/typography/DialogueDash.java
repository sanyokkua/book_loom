package ua.bookloom.pipeline.typography;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes dialogue dashes in one form: {@code — } at the start of a line, {@code  — } where a dash follows {@code , ? !
 * …} or {@code »} and precedes a letter, and a spaced short dash or hyphen between words as {@code  — }. A hyphen
 * inside a word and a dash between digits are never touched, and {@code — …} (a silent reply) is already right.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DialogueDash {

    // Over the whole text: a line's start is a real line start, never the stretch after a token.
    private static final Pattern LINE_START =
            Pattern.compile("(?m)^(?:[–―‒—](?=[ \\u00A0])|[–―‒—-](?=[\\p{L}«„“\"])|[–―‒](?=[ \\u00A0]))");
    private static final Pattern SPACED_DASH =
            Pattern.compile("(?<=[^\\s\\p{N}])[ \\u00A0][–―‒-][ \\u00A0](?=[^\\s\\p{N}])");
    private static final Pattern GLUED_DASH = Pattern.compile("(?<=[,?!…»])[ \\u00A0]?[-–―‒—](?=\\p{L})");

    static Edit apply(final String text) {
        final Edit start = lineStart(text);
        final Edit spaced = OutsideTokens.replaceAll(start.text(), SPACED_DASH, " — ");
        final Edit glued = OutsideTokens.replaceAll(spaced.text(), GLUED_DASH, " — ");
        return new Edit(glued.text(), start.count() + spaced.count() + glued.count());
    }

    private static Edit lineStart(final String text) {
        final var matcher = LINE_START.matcher(text);
        final StringBuilder out = new StringBuilder(text.length() + 2);
        int count = 0;
        int cursor = 0;
        while (matcher.find()) {
            final boolean isRight = matcher.group().equals("—") && followedBySpace(text, matcher.end());
            out.append(text, cursor, matcher.start());
            if (isRight) {
                out.append('—');
            } else {
                out.append(followedBySpace(text, matcher.end()) ? "—" : "— ");
                count++;
            }
            cursor = matcher.end();
        }
        return new Edit(out.append(text, cursor, text.length()).toString(), count);
    }

    private static boolean followedBySpace(final String text, final int index) {
        return index < text.length() && (text.charAt(index) == ' ' || text.charAt(index) == ' ');
    }
}
