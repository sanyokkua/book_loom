package ua.bookloom.pipeline.typography;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Puts back the dash that opens a speech line in the source and is missing in the target: a target that starts with a
 * letter gets {@code — }. A target that starts with a quote mark is only reported, as the quote may be the speech's own
 * mark and a dash before it would be a guess.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LeadingDash {

    private static final String DASH = "— ";
    private static final Pattern SOURCE_DASH = Pattern.compile("^[-–―‒—][ \\u00A0]");
    private static final Pattern STARTS_WITH_LETTER = Pattern.compile("^\\p{L}");
    private static final Pattern STARTS_WITH_QUOTE = Pattern.compile("^[«»„“”\"‘’']");

    static Edit apply(final String source, final String text) {
        return startsWithDash(source) && STARTS_WITH_LETTER.matcher(text).find()
                ? new Edit(DASH + text, 1)
                : new Edit(text, 0);
    }

    static boolean isLostBeforeQuote(final String source, final String text) {
        return startsWithDash(source) && STARTS_WITH_QUOTE.matcher(text).find();
    }

    private static boolean startsWithDash(final String source) {
        return SOURCE_DASH.matcher(source).find();
    }
}
