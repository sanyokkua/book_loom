package ua.bookloom.pipeline.typography;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.checks.QuoteConventions;

/**
 * The one straight {@code "} a model leaves at the very end or start of a paragraph is a stray closer or opener when
 * the language's own quotes already pair up and there are some; it is removed. An inch sign, or a text with no other
 * quotes, is never touched.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StrayQuote {

    private static final char STRAIGHT = '"';

    static Edit apply(final String text, final String languageTag) {
        final long straight = text.chars().filter(c -> c == STRAIGHT).count();
        final String stripped = text.strip();
        if (straight != 1 || stripped.length() < 2 || !hasPairedQuotes(text, languageTag)) {
            return new Edit(text, 0);
        }
        if (stripped.charAt(stripped.length() - 1) == STRAIGHT) {
            return new Edit(stripped.substring(0, stripped.length() - 1).stripTrailing(), 1);
        }
        if (stripped.charAt(0) == STRAIGHT) {
            return new Edit(stripped.substring(1).stripLeading(), 1);
        }
        return new Edit(text, 0);
    }

    private static boolean hasPairedQuotes(final String text, final String languageTag) {
        final boolean hasOthers = text.chars().anyMatch(c -> "«»„“”".indexOf(c) >= 0);
        return hasOthers && QuoteConventions.isBalanced(text, languageTag);
    }
}
