package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.Sentences;

/**
 * What a revision may not lose or bring in, read off the old and the new text alone: a gender, name or neighbour fix
 * changes words and endings, never the quote marks, the dialogue dashes, the sentences, a fifth of the words, or the
 * script of a word. Each rule is a regression a real consistency pass made (quotes stripped, "senile amnesia", a
 * phrase cut to a preposition).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RevisionGuards {

    private static final String QUOTE_MARKS = "«»„“”\"";
    private static final String DASHES = "—–";
    private static final int WORD_LOSS_DIVISOR = 5;
    private static final Pattern LATIN_RUN = Pattern.compile("\\b[a-z]{2,}(?:\\s+[a-z]{2,})+\\b");
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    static final String QUOTES = "quotes";
    static final String CLAUSES = "clauses";
    static final String REWRITE_CAP_RULE = "rewrite-cap";
    /** The largest share of a paragraph's words a neighbour check may replace or remove; a rewrite past it is refused. */
    static final double REWRITE_CAP = 0.35;

    private static final String CLAUSE_MARKS = ",;:";
    private static final String DASH_RULE = "dashes";
    private static final String SENTENCES = "sentences";
    private static final String WORDS = "words";
    private static final String LATIN = "latin-run";

    /** How a count the new text holds is compared with the old one. */
    enum Mode {
        /** A fix of words and endings: every count stays as it was. */
        SAME_COUNTS,
        /**
         * A new draft of a doubted text: a count may grow, since the old text may have lost a sentence or its quote
         * marks, but never shrink.
         */
        NO_LOSS
    }

    /**
     * Whether the new text keeps what the old one had.
     *
     * @param before the old text, masked
     * @param after the new text, masked
     * @return {@code true} when quote marks, dialogue dashes, sentences and words are kept and no Latin run is new
     */
    static boolean preserves(final String before, final String after) {
        return violation(before, after, Mode.SAME_COUNTS).isEmpty();
    }

    /**
     * The first rule the new text breaks.
     *
     * @param before the old text, masked
     * @param after the new text, masked
     * @param mode how the counts are compared
     * @return the broken rule's name ({@code quotes}, {@code dashes}, {@code sentences}, {@code words} or
     *     {@code latin-run}), or empty when the new text keeps every one
     */
    static Optional<String> violation(final String before, final String after, final Mode mode) {
        return violationOf(before, after, mode)
                .or(() -> clausesLost(DisplayText.of(before), DisplayText.of(after))
                        ? Optional.of(CLAUSES)
                        : Optional.empty());
    }

    /**
     * Whether a new text replaces more of the old one's words than the cap allows. A pure addition (a restored
     * sentence) replaces nothing.
     *
     * @param before the old text, masked
     * @param after the new text, masked
     * @return {@code true} if more than {@link #REWRITE_CAP} of the old words are missing from the new text, in order
     */
    static boolean exceedsRewriteCap(final String before, final String after) {
        final List<String> old = words(DisplayText.of(before));
        final List<String> fresh = words(DisplayText.of(after));
        final double share = old.isEmpty() ? 0 : (double) (old.size() - commonWords(old, fresh)) / old.size();
        log.debug(
                "Rewrite cap oldWords={} newWords={} replacedShare={} cap={}",
                old.size(),
                fresh.size(),
                share,
                REWRITE_CAP);
        return share > REWRITE_CAP;
    }

    private static boolean clausesLost(final String old, final String fresh) {
        final long before = count(old, CLAUSE_MARKS);
        final long after = count(fresh, CLAUSE_MARKS);
        log.debug("Revision guards clause marks={}->{}", before, after);
        return after < before;
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

    /**
     * The first rule a text edited in place breaks.
     *
     * @param before the text before the edit, masked; never null
     * @param after the text after the edit, masked; never null
     * @param mayGrow whether the edit may add quote marks, dashes or sentences (an omission fix), never remove them
     * @return the broken rule's name, or empty when the edit keeps every count
     */
    public static Optional<String> violationOfEdit(final String before, final String after, final boolean mayGrow) {
        return violationOf(before, after, mayGrow ? Mode.NO_LOSS : Mode.SAME_COUNTS);
    }

    private static Optional<String> violationOf(final String before, final String after, final Mode mode) {
        final String old = DisplayText.of(before);
        final String fresh = DisplayText.of(after);
        final Optional<String> broken = Stream.of(
                        broken(QUOTES, count(old, QUOTE_MARKS), count(fresh, QUOTE_MARKS), mode),
                        broken(DASH_RULE, count(old, DASHES), count(fresh, DASHES), mode),
                        broken(SENTENCES, Sentences.countLenient(old), Sentences.countLenient(fresh), mode),
                        losesWords(old, fresh) ? WORDS : null,
                        bringsLatinRun(old, fresh) ? LATIN : null)
                .filter(Objects::nonNull)
                .findFirst();
        log.debug(
                "Revision guards mode={} quotes={}->{} dashes={}->{} sentences={}->{} words={}->{} broken={}",
                mode,
                count(old, QUOTE_MARKS),
                count(fresh, QUOTE_MARKS),
                count(old, DASHES),
                count(fresh, DASHES),
                Sentences.countLenient(old),
                Sentences.countLenient(fresh),
                wordCount(old),
                wordCount(fresh),
                broken.orElse("none"));
        return broken;
    }

    /**
     * The first rule a new draft breaks against the source, for an old text that was never translated: the source's
     * quote marks, dialogue dashes and sentences may grow but never shrink. Words and Latin runs are not compared — a
     * translation has fewer words than the English it replaces, and the source is Latin throughout.
     *
     * @param source the source text, masked
     * @param after the new text, masked
     * @return the broken rule's name ({@code quotes}, {@code dashes} or {@code sentences}), or empty when the new text
     *     keeps every one
     */
    static Optional<String> violationOfSource(final String source, final String after) {
        final String origin = DisplayText.of(source);
        final String fresh = DisplayText.of(after);
        final Optional<String> broken = Stream.of(
                        broken(QUOTES, count(origin, QUOTE_MARKS), count(fresh, QUOTE_MARKS), Mode.NO_LOSS),
                        broken(DASH_RULE, count(origin, DASHES), count(fresh, DASHES), Mode.NO_LOSS),
                        broken(SENTENCES, Sentences.countLenient(origin), Sentences.countLenient(fresh), Mode.NO_LOSS))
                .filter(Objects::nonNull)
                .findFirst();
        log.debug(
                "Revision guards against the source quotes={}->{} dashes={}->{} sentences={}->{} broken={}",
                count(origin, QUOTE_MARKS),
                count(fresh, QUOTE_MARKS),
                count(origin, DASHES),
                count(fresh, DASHES),
                Sentences.countLenient(origin),
                Sentences.countLenient(fresh),
                broken.orElse("none"));
        return broken;
    }

    private static @Nullable String broken(final String rule, final long old, final long fresh, final Mode mode) {
        final boolean kept = mode == Mode.SAME_COUNTS ? old == fresh : fresh >= old;
        return kept ? null : rule;
    }

    private static int wordCount(final String text) {
        return (int) WORD.matcher(text).results().count();
    }

    private static long count(final String text, final String marks) {
        return text.chars().filter(c -> marks.indexOf(c) >= 0).count();
    }

    private static boolean losesWords(final String old, final String fresh) {
        final int lost = wordCount(old) - wordCount(fresh);
        return lost > Math.max(1, wordCount(old) / WORD_LOSS_DIVISOR);
    }

    private static boolean bringsLatinRun(final String old, final String fresh) {
        if (!CYRILLIC.matcher(fresh).find()) {
            return false;
        }
        final List<String> known = runs(old);
        return runs(fresh).stream().anyMatch(run -> !known.contains(run));
    }

    private static List<String> runs(final String text) {
        return LATIN_RUN.matcher(text).results().map(match -> match.group()).toList();
    }
}
