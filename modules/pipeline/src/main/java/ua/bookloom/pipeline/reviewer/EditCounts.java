package ua.bookloom.pipeline.reviewer;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.Sentences;
import ua.bookloom.pipeline.qa.TextGuards;

/**
 * What an edit in place may not lose, read off the old and the new text alone: a gender or name fix changes words and
 * endings, never the quote marks, the dialogue dashes, the sentences, a fifth of the words, or the script of a word.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EditCounts {

    private static final String QUOTE_MARKS = TextGuards.QUOTE_MARKS;
    private static final String DASHES = TextGuards.DASHES;
    private static final int WORD_LOSS_DIVISOR = 5;
    private static final String QUOTES = "quotes";
    private static final String DASH_RULE = "dashes";
    private static final String SENTENCES = "sentences";
    private static final String WORDS = "words";
    private static final String LATIN = "latin-run";

    /**
     * The first rule a text edited in place breaks.
     *
     * @param before the text before the edit, masked; never null
     * @param after the text after the edit, masked; never null
     * @param mayGrow whether the edit may add quote marks, dashes or sentences (an omission fix), never remove them
     * @return the broken rule's name, or empty when the edit keeps every count
     */
    static Optional<String> violationOfEdit(final String before, final String after, final boolean mayGrow) {
        final String old = DisplayText.of(before);
        final String fresh = DisplayText.of(after);
        final Optional<String> broken = Stream.of(
                        broken(QUOTES, count(old, QUOTE_MARKS), count(fresh, QUOTE_MARKS), mayGrow),
                        broken(DASH_RULE, count(old, DASHES), count(fresh, DASHES), mayGrow),
                        broken(SENTENCES, Sentences.countLenient(old), Sentences.countLenient(fresh), mayGrow),
                        losesWords(old, fresh) ? WORDS : null,
                        TextGuards.bringsLatinRun(old, fresh) ? LATIN : null)
                .filter(Objects::nonNull)
                .findFirst();
        log.debug(
                "Edit counts mayGrow={} quotes={}->{} dashes={}->{} sentences={}->{} words={}->{} broken={}",
                mayGrow,
                count(old, QUOTE_MARKS),
                count(fresh, QUOTE_MARKS),
                count(old, DASHES),
                count(fresh, DASHES),
                Sentences.countLenient(old),
                Sentences.countLenient(fresh),
                TextGuards.wordCount(old),
                TextGuards.wordCount(fresh),
                broken.orElse("none"));
        return broken;
    }

    private static @Nullable String broken(final String rule, final long old, final long fresh, final boolean mayGrow) {
        final boolean kept = mayGrow ? fresh >= old : old == fresh;
        return kept ? null : rule;
    }

    private static long count(final String text, final String marks) {
        return TextGuards.count(text, marks);
    }

    private static boolean losesWords(final String old, final String fresh) {
        final int lost = TextGuards.wordCount(old) - TextGuards.wordCount(fresh);
        return lost > Math.max(1, TextGuards.wordCount(old) / WORD_LOSS_DIVISOR);
    }
}
