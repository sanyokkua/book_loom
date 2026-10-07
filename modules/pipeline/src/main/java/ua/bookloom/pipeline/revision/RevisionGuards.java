package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
final class RevisionGuards {

    private static final String QUOTE_MARKS = "«»„“”\"";
    private static final String DASHES = "—–";
    private static final int WORD_LOSS_DIVISOR = 5;
    private static final Pattern LATIN_RUN = Pattern.compile("\\b[a-z]{2,}(?:\\s+[a-z]{2,})+\\b");
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    /**
     * Whether the new text keeps what the old one had.
     *
     * @param before the old text, masked
     * @param after the new text, masked
     * @return {@code true} when quote marks, dialogue dashes, sentences and words are kept and no Latin run is new
     */
    static boolean preserves(final String before, final String after) {
        final String old = DisplayText.of(before);
        final String fresh = DisplayText.of(after);
        final boolean kept = count(old, QUOTE_MARKS) == count(fresh, QUOTE_MARKS)
                && count(old, DASHES) == count(fresh, DASHES)
                && Sentences.countLenient(old) == Sentences.countLenient(fresh)
                && !losesWords(old, fresh)
                && !bringsLatinRun(old, fresh);
        log.debug(
                "Revision guards quotes={}->{} dashes={}->{} sentences={}->{} words={}->{} kept={}",
                count(old, QUOTE_MARKS),
                count(fresh, QUOTE_MARKS),
                count(old, DASHES),
                count(fresh, DASHES),
                Sentences.countLenient(old),
                Sentences.countLenient(fresh),
                wordCount(old),
                wordCount(fresh),
                kept);
        return kept;
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
