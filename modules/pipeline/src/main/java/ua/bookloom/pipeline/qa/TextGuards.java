package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The counts a text edit may not lose, read off two display texts alone: quote marks, dialogue dashes, clause marks,
 * Latin runs and the share of words a rewrite replaces. The never-worse gate and the export pass's revision guards
 * share them, so both count a quote mark or a replaced word the same way.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextGuards {

    /** The quote marks counted, of every convention a target may use. */
    public static final String QUOTE_MARKS = "«»„“”\"";

    /** The dialogue dashes counted. */
    public static final String DASHES = "—–";

    /** The marks that separate clauses. */
    public static final String CLAUSE_MARKS = ",;:";

    /**
     * The largest share of a paragraph's words a rewrite may replace or remove; past it the rewrite is refused. An
     * unmeasured named constant (design D14).
     */
    public static final double REWRITE_CAP = 0.35;

    private static final Pattern LATIN_RUN = Pattern.compile("\\b[a-z]{2,}(?:\\s+[a-z]{2,}){1,200}\\b");
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    /**
     * How many of the marks a text holds.
     *
     * @param text the non-null display text
     * @param marks the non-null marks to count, each a single character
     * @return the number of characters of {@code text} that are one of {@code marks}
     */
    public static long count(final String text, final String marks) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(marks, "marks");
        return text.chars().filter(c -> marks.indexOf(c) >= 0).count();
    }

    /**
     * Whether a new text brings a run of two or more lower-case Latin words the old one did not have; only a text in
     * Cyrillic is read, since a Latin run is normal in a Latin-script target.
     *
     * @param old the non-null old display text
     * @param fresh the non-null new display text
     * @return {@code true} if the new text holds a Latin run the old one lacks, {@code false} otherwise
     */
    public static boolean bringsLatinRun(final String old, final String fresh) {
        if (!CYRILLIC.matcher(fresh).find()) {
            return false;
        }
        final List<String> known = runs(old);
        return runs(fresh).stream().anyMatch(run -> !known.contains(run));
    }

    /**
     * The share of the old text's words a new text no longer holds in order; a pure addition replaces nothing.
     *
     * @param old the non-null old display text
     * @param fresh the non-null new display text
     * @return a share in {@code [0,1]}; zero when the old text has no word
     */
    public static double replacedShare(final String old, final String fresh) {
        final List<String> before = words(old);
        final List<String> after = words(fresh);
        return before.isEmpty() ? 0 : (double) (before.size() - commonWords(before, after)) / before.size();
    }

    /**
     * How many words a text holds.
     *
     * @param text the non-null display text
     * @return the number of words, letters joined by apostrophes and hyphens
     */
    public static int wordCount(final String text) {
        return (int) WORD.matcher(text).results().count();
    }

    private static List<String> runs(final String text) {
        return LATIN_RUN.matcher(text).results().map(MatchResult::group).toList();
    }

    private static List<String> words(final String text) {
        return WORD.matcher(text)
                .results()
                .map(match -> match.group().toLowerCase(Locale.ROOT))
                .toList();
    }

    // The longest run of old words that survive in order: what the new text kept.
    private static int commonWords(final List<String> old, final List<String> fresh) {
        final int[] row = new int[fresh.size() + 1];
        for (final String word : old) {
            int diagonal = 0;
            for (int at = 1; at <= fresh.size(); at++) {
                final int above = row[at];
                row[at] = word.equals(fresh.get(at - 1)) ? diagonal + 1 : Math.max(row[at], row[at - 1]);
                diagonal = above;
            }
        }
        return row[fresh.size()];
    }
}
