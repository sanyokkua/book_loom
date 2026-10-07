package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Splits a display text into sentences for the checks that compare a source with its translation. Language-agnostic by
 * construction: a sentence ends at a run of terminal marks before a space, a closing quote or the end, except after a
 * title or an initial ({@code Mr.}, {@code J.}) and before a lower-case continuation.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Sentences {

    /** A sentence with at least this many words counts as a sentence a translation may not drop. */
    public static final int SIGNIFICANT_WORDS = 3;

    private static final Pattern END = Pattern.compile("[.!?…。！？]+[»”\"’)]*(?=\\s|$)");
    private static final Pattern LAST_WORD = Pattern.compile("(\\p{L}+)$");
    private static final Set<String> TITLES = Set.of(
            "mr", "mrs", "ms", "dr", "st", "jr", "sr", "vs", "prof", "gen", "col", "capt", "lt", "sgt", "mt", "etc");

    /**
     * Cuts a text into sentences.
     *
     * @param text a non-null display text
     * @return the trimmed, non-blank sentences in order, each with its closing quote; never null, empty for a text with
     *     no letter
     */
    public static List<String> split(final String text) {
        Objects.requireNonNull(text, "text");
        final List<String> sentences = new ArrayList<>();
        final Matcher matcher = END.matcher(text);
        int from = 0;
        while (matcher.find()) {
            if (continues(text, matcher)) {
                continue;
            }
            add(sentences, text.substring(from, matcher.end()));
            from = matcher.end();
        }
        add(sentences, text.substring(from));
        return sentences;
    }

    /**
     * The sentences a translation is held to keep: those of at least {@value #SIGNIFICANT_WORDS} words.
     *
     * @param text a non-null display text
     * @return the significant sentences in order; never null
     */
    public static List<String> significant(final String text) {
        return split(text).stream()
                .filter(sentence -> Words.count(sentence) >= SIGNIFICANT_WORDS)
                .toList();
    }

    /**
     * How many sentences a translation holds, counted generously: every run of terminal marks ends one, whatever follows
     * it, so a target is never short-counted by a lower-case word after a full stop.
     *
     * @param text a non-null display text
     * @return the number of sentences; zero for a text with no letter
     */
    public static int countLenient(final String text) {
        Objects.requireNonNull(text, "text");
        final Matcher matcher = END.matcher(text);
        int count = 0;
        int from = 0;
        while (matcher.find()) {
            if (hasLetter(text.substring(from, matcher.start()))) {
                count++;
            }
            from = matcher.end();
        }
        return hasLetter(text.substring(from)) ? count + 1 : count;
    }

    private static boolean hasLetter(final String text) {
        return text.codePoints().anyMatch(Character::isLetter);
    }

    private static void add(final List<String> sentences, final String raw) {
        final String sentence = raw.strip();
        if (sentence.codePoints().anyMatch(Character::isLetter)) {
            sentences.add(sentence);
        }
    }

    // A full stop after a title or an initial, or one followed by a lower-case letter, does not end a sentence.
    private static boolean continues(final String text, final Matcher end) {
        final String marks = end.group();
        if (!marks.startsWith(".") || (marks.length() > 1 && marks.charAt(1) == '.')) {
            return false;
        }
        final Matcher word = LAST_WORD.matcher(text.substring(0, end.start()));
        if (word.find()) {
            final String last = word.group(1);
            final boolean initial = last.length() == 1 && Character.isUpperCase(last.charAt(0));
            if (initial || TITLES.contains(last.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        int next = end.end();
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        return next < text.length() && Character.isLowerCase(text.charAt(next));
    }
}
