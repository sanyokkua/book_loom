package ua.bookloom.pipeline.checks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds what a paragraph's characters say as a speaker and what they say as the narrator. A check about the narrator
 * (the person of a verb, the gender it agrees with) must not read a character's own words, so it reads the text with the
 * speech blanked out. The blanked text keeps its length, so an offset in it is an offset in the original.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuotedSpans {

    private static final Pattern LEADING_DASH = Pattern.compile("^\\s*[—–―]\\s");
    private static final Pattern REMARK_DASH = Pattern.compile("(?<=\\s)[—–―](?=\\s)");

    /**
     * The text with every quoted passage and every line of dash dialogue replaced by spaces.
     *
     * @param text a display text
     * @param languageTag the language whose quote marks apply, or null for the general table
     * @return a string of the same length as {@code text}; an opened quote that is never closed blanks the rest, so the
     *     text a caller reads is only what is surely the narrator's
     */
    public static String narration(final String text, final String languageTag) {
        Objects.requireNonNull(text, "text");
        final char[] chars = text.toCharArray();
        blankQuotes(chars, QuoteConventions.forLanguage(languageTag));
        blankDashDialogue(chars, text);
        return new String(chars);
    }

    private static void blankQuotes(final char[] chars, final List<QuotePair> pairs) {
        final Deque<Character> closers = new ArrayDeque<>();
        for (int index = 0; index < chars.length; index++) {
            final char mark = chars[index];
            final boolean closes = !closers.isEmpty() && closers.peek() == mark;
            final QuotePair opened = pairs.stream()
                    .filter(pair -> pair.open() == mark)
                    .findFirst()
                    .orElse(null);
            if (closes) {
                closers.pop();
                chars[index] = ' ';
            } else if (opened != null) {
                closers.push(opened.close());
                chars[index] = ' ';
            } else if (!closers.isEmpty()) {
                chars[index] = ' ';
            }
        }
    }

    // A paragraph that starts with a dash is speech up to the dash that opens the author's remark, narration up to the
    // next dash, and so on; a paragraph that does not start with one is never split on its dashes.
    private static void blankDashDialogue(final char[] chars, final String text) {
        if (!LEADING_DASH.matcher(text).find()) {
            return;
        }
        boolean speech = true;
        int from = 0;
        final Matcher dash = REMARK_DASH.matcher(text);
        while (dash.find()) {
            blank(chars, speech ? from : dash.start(), dash.end());
            speech = !speech;
            from = dash.end();
        }
        if (speech) {
            blank(chars, from, chars.length);
        }
    }

    private static void blank(final char[] chars, final int from, final int to) {
        for (int index = from; index < to; index++) {
            chars[index] = ' ';
        }
    }
}
