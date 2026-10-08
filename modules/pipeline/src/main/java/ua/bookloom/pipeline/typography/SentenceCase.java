package ua.bookloom.pipeline.typography;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Capitalises a sentence start a model left lower-case: the first letter of the paragraph when the source's first
 * letter is upper-case and nothing but white space and quote marks precedes it in both (a dash opening is a
 * continuation and a token start is unreadable, so neither is touched), and the first letter after a closing quote with
 * its full stop.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SentenceCase {

    private static final Pattern AFTER_QUOTE = Pattern.compile("(\\.»|»\\.)([ \\t\\u00A0]+)(\\p{Ll})");

    static Edit apply(final String source, final String text, final Locale locale) {
        final Edit start = paragraphStart(source, text, locale);
        final Matcher after = AFTER_QUOTE.matcher(start.text());
        final StringBuilder out = new StringBuilder();
        int count = start.count();
        while (after.find()) {
            after.appendReplacement(
                    out,
                    Matcher.quoteReplacement(
                            after.group(1) + after.group(2) + after.group(3).toUpperCase(locale)));
            count++;
        }
        after.appendTail(out);
        return new Edit(out.toString(), count);
    }

    private static Edit paragraphStart(final String source, final String text, final Locale locale) {
        final int own = QuoteMarks.firstContent(text);
        final int theirs = QuoteMarks.firstContent(source);
        if (own < 0
                || theirs < 0
                || !Character.isLowerCase(text.charAt(own))
                || !Character.isUpperCase(source.charAt(theirs))) {
            return new Edit(text, 0);
        }
        final String upper = String.valueOf(text.charAt(own)).toUpperCase(locale);
        return new Edit(text.substring(0, own) + upper + text.substring(own + 1), 1);
    }
}
