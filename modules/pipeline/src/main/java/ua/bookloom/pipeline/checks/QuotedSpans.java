package ua.bookloom.pipeline.checks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

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
    public static String narration(final String text, @Nullable final String languageTag) {
        Objects.requireNonNull(text, "text");
        final char[] chars = text.toCharArray();
        blankQuotes(chars, quotePairs(languageTag));
        blankDashDialogue(chars, text);
        return new String(chars);
    }

    /**
     * The quoted passages and the lines of dash dialogue of a text, one span for each speech: a quoted passage from its
     * opening mark to its closing one, a dash dialogue from the start up to the dash that opens the author's remark and
     * again after each remark.
     *
     * @param text a display text
     * @param languageTag the language whose quote marks apply, or null for the general table
     * @return the speeches in text order; never null, empty when the text holds none; an opened quote that is never
     *     closed runs to the end of the text
     */
    public static List<TextSpan> speeches(final String text, @Nullable final String languageTag) {
        Objects.requireNonNull(text, "text");
        final List<int[]> ranges = new ArrayList<>(dashSpeeches(text));
        final List<int[]> dash = List.copyOf(ranges);
        for (final int[] quoted : quoteRanges(text.toCharArray(), quotePairs(languageTag))) {
            if (dash.stream().noneMatch(range -> quoted[0] >= range[0] && quoted[0] < range[1])) {
                ranges.add(quoted);
            }
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        return ranges.stream()
                .map(range -> new TextSpan(range[0], range[1], text.substring(range[0], range[1])))
                .toList();
    }

    private static List<QuotePair> quotePairs(@Nullable final String languageTag) {
        return QuoteConventions.withEnglish(QuoteConventions.forLanguage(languageTag));
    }

    private static void blankQuotes(final char[] chars, final List<QuotePair> pairs) {
        for (final int[] range : quoteRanges(chars, pairs)) {
            blank(chars, range[0], range[1]);
        }
    }

    // Each top-level quoted passage as [from, to); an opened quote that is never closed runs to the end.
    private static List<int[]> quoteRanges(final char[] chars, final List<QuotePair> pairs) {
        final List<int[]> ranges = new ArrayList<>();
        final Deque<Character> closers = new ArrayDeque<>();
        int from = 0;
        for (int index = 0; index < chars.length; index++) {
            final char mark = chars[index];
            final QuotePair opened = pairs.stream()
                    .filter(pair -> pair.open() == mark)
                    .findFirst()
                    .orElse(null);
            if (!closers.isEmpty() && closers.peek() == mark) {
                closers.pop();
                if (closers.isEmpty()) {
                    ranges.add(new int[] {from, index + 1});
                }
            } else if (opened != null) {
                if (closers.isEmpty()) {
                    from = index;
                }
                closers.push(opened.close());
            }
        }
        if (!closers.isEmpty()) {
            ranges.add(new int[] {from, chars.length});
        }
        return ranges;
    }

    // The speech of a paragraph that starts with a dash, as [from, to) up to each remark dash; none for any other.
    private static List<int[]> dashSpeeches(final String text) {
        final List<int[]> ranges = new ArrayList<>();
        if (!LEADING_DASH.matcher(text).find()) {
            return ranges;
        }
        boolean speech = true;
        int from = 0;
        final Matcher dash = REMARK_DASH.matcher(text);
        while (dash.find()) {
            if (speech) {
                ranges.add(new int[] {from, dash.start()});
            }
            speech = !speech;
            from = dash.end();
        }
        if (speech) {
            ranges.add(new int[] {from, text.length()});
        }
        return ranges;
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
