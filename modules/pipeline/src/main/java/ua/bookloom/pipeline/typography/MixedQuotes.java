package ua.bookloom.pipeline.typography;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * A secondary pair ({@code „ “}) outside any primary pair ({@code « »}) is a primary pair written wrongly, so it becomes
 * one; inside a primary pair it is the nested pair and stays. A secondary opener with no closer before the next
 * primary mark is left alone.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MixedQuotes {

    static Edit apply(final String text, final List<QuotePair> pairs) {
        if (pairs.size() < 2) {
            return new Edit(text, 0);
        }
        final QuotePair primary = pairs.get(0);
        final QuotePair secondary = pairs.get(1);
        final StringBuilder out = new StringBuilder(text);
        int depth = 0;
        int count = 0;
        for (int i = 0; i < out.length(); i++) {
            final char c = out.charAt(i);
            if (c == primary.open()) {
                depth++;
            } else if (c == primary.close() && depth > 0) {
                depth--;
            } else if (c == secondary.open() && depth == 0) {
                final int close = closerOf(out, i, primary, secondary);
                if (close > 0) {
                    out.setCharAt(i, primary.open());
                    out.setCharAt(close, primary.close());
                    count++;
                }
            }
        }
        return new Edit(out.toString(), count);
    }

    private static int closerOf(
            final CharSequence text, final int from, final QuotePair primary, final QuotePair secondary) {
        for (int i = from + 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == secondary.close()) {
                return i;
            }
            if (c == primary.open() || c == primary.close() || c == secondary.open()) {
                return -1;
            }
        }
        return -1;
    }
}
