package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The one owner of how a word count is worded, so the verdict line and the "About this book" summary never give two
 * figures for one book: exact below a thousand, where rounding would invent a count, and rounded to the hundred from
 * there up, where the count is only an estimate anyway.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class WordCount {

    private static final int ROUNDING_FROM = 1000;
    private static final double HUNDRED = 100.0;

    /**
     * Words a count in the interface language.
     *
     * @param messages the catalogue the wording comes from
     * @param words the number of words; never negative
     * @return the exact count below 1000, otherwise "about" and the count rounded to the nearest hundred
     */
    public static String text(final Messages messages, final int words) {
        Objects.requireNonNull(messages, "messages");
        if (words < ROUNDING_FROM) {
            return messages.get(MessageKey.IMPORT_WORDS_EXACT, words);
        }
        final long rounded = Math.round(words / HUNDRED) * (long) HUNDRED;
        log.debug("word count {} is worded as about {}", words, rounded);
        return messages.get(MessageKey.IMPORT_WORDS_ABOUT, rounded);
    }
}
