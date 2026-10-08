package ua.bookloom.pipeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;

/** The one {@code ⟦gN⟧} placeholder-token pattern, so no class keeps its own copy of it. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Tokens {

    private static final Pattern TOKEN = Pattern.compile("⟦g(\\d+)⟧");

    /**
     * The token with a given number.
     *
     * @param index the token's number; not negative
     * @return {@code ⟦gN⟧} for {@code N = index}
     */
    public static String of(final int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0, but was " + index);
        }
        return "⟦g" + index + "⟧";
    }

    /**
     * Every placeholder token of a text.
     *
     * @param text any text, masked or not; never null
     * @return the tokens in text order, repeats included; never null, empty when there are none
     */
    public static List<String> inOrder(final String text) {
        Objects.requireNonNull(text, "text");
        final Matcher matcher = TOKEN.matcher(text);
        final List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * The tokens of a text that {@code known} does not hold and that stand where a word would — no letter or digit
     * touches them, other tokens skipped. A small model writes such a token in place of a name; dropping it would drop
     * the name, whereas a token glued to a word ({@code ⟦g9⟧Vance}) can go and leave the word.
     *
     * @param text the reply; never null
     * @param known the tokens the text may hold; never null
     * @return those tokens in text order, repeats included; never null, empty when there are none
     */
    public static List<String> inventedStandingAlone(final String text, final Collection<String> known) {
        Objects.requireNonNull(known, "known");
        final Matcher matcher = matcher(text);
        final List<String> invented = new ArrayList<>();
        while (matcher.find()) {
            if (!known.contains(matcher.group()) && !isGluedToWord(text, matcher.start(), matcher.end())) {
                invented.add(matcher.group());
            }
        }
        return invented;
    }

    private static boolean isGluedToWord(final String text, final int start, final int end) {
        final String before = replace(text.substring(0, start), "");
        final String after = replace(text.substring(end), "");
        return (!before.isEmpty() && Character.isLetterOrDigit(before.codePointBefore(before.length())))
                || (!after.isEmpty() && Character.isLetterOrDigit(after.codePointAt(0)));
    }

    /**
     * A matcher over the placeholder tokens of a text, for a caller that needs where each one sits.
     *
     * @param text any text, masked or not; never null
     * @return a fresh matcher; each match is one token, its group 1 the token's number
     */
    public static Matcher matcher(final String text) {
        Objects.requireNonNull(text, "text");
        return TOKEN.matcher(text);
    }

    /**
     * The highest token number a text uses, so a new token can be numbered above every existing one.
     *
     * @param text any text, masked or not; never null
     * @return the largest {@code N} of any {@code ⟦gN⟧} in the text, or {@code -1} when it holds none
     */
    public static int highestIndex(final String text) {
        final Matcher matcher = matcher(text);
        int highest = -1;
        while (matcher.find()) {
            highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
        }
        return highest;
    }

    /**
     * Replaces every placeholder token of a text.
     *
     * @param text any text, masked or not; never null
     * @param replacement the text each token becomes; taken literally, never null
     * @return the text with each token replaced
     */
    public static String replace(final String text, final String replacement) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(replacement, "replacement");
        return TOKEN.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /**
     * The text a model reads of some segments, to look names up in: each masked text with its tokens turned to spaces.
     *
     * @param segments the non-null segments
     * @return one text per segment, in order
     */
    public static List<String> visibleTexts(final List<Segment> segments) {
        return segments.stream().map(segment -> replace(segment.masked(), " ")).toList();
    }
}
