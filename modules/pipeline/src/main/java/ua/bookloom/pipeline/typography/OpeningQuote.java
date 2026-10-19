package ua.bookloom.pipeline.typography;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * Speech that goes on from the paragraph before opens in the source with a quote mark but has no opener to close, so
 * a model drops it. When the source opens with a quote, the target neither opens with the language's opener nor a dash
 * and holds exactly one closer more than openers, the opener is put back at the start — and in no other case.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpeningQuote {

    static Edit apply(final String source, final String text, final QuotePair primary) {
        final String body = text.stripLeading();
        if (body.isEmpty() || !QuoteMarks.opensWithQuote(source) || isExcludedStart(body.charAt(0), primary)) {
            return new Edit(text, 0);
        }
        final long closers = text.chars().filter(c -> c == primary.close()).count();
        final long openers = text.chars().filter(c -> c == primary.open()).count();
        if (closers - openers != 1) {
            return new Edit(text, 0);
        }
        final int at = text.length() - body.length();
        return new Edit(text.substring(0, at) + primary.open() + body, 1);
    }

    private static boolean isExcludedStart(final char first, final QuotePair primary) {
        return first == primary.open() || Character.getType(first) == Character.DASH_PUNCTUATION || first == '⟦';
    }
}
