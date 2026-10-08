package ua.bookloom.pipeline.checks;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The JSON closers a small model writes inside the last string of its reply ({@code …двері."}]}}): a quote and braces
 * or brackets at the very end of a target. A book that prints a brace or bracket itself is left alone, since then a
 * closer cannot be told from its text. A closing guillemet or curly quote is cut with them only when the text has no
 * opening mark for it, so a real quotation is not unbalanced.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReplyClosers {

    private static final String STRUCTURAL = "{}[]";
    private static final Pattern TAIL = Pattern.compile("([\"”»])?\\s*[}\\]]+\\s*$");

    /**
     * Cuts the closers from the end of a target.
     *
     * @param source the non-null source text; closers are cut only when it holds no brace or bracket
     * @param target the non-null target text
     * @return the target without its trailing closers, which may be empty; empty when nothing was cut
     */
    public static Optional<String> strip(final String source, final String target) {
        if (source.chars().anyMatch(c -> STRUCTURAL.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        final Matcher tail = TAIL.matcher(target);
        if (!tail.find()) {
            return Optional.empty();
        }
        final int cut = keepsQuote(target, tail) ? tail.start() + 1 : tail.start();
        final String kept = target.substring(0, cut).stripTrailing();
        log.debug("Reply closers cut from a target of {} characters, {} kept", target.length(), kept.length());
        return Optional.of(kept);
    }

    // A curly or guillemet closer belongs to the text when an opening mark of its kind stands before it.
    private static boolean keepsQuote(final String target, final Matcher tail) {
        final String quote = tail.group(1);
        if (quote == null || quote.equals("\"")) {
            return false;
        }
        final String before = target.substring(0, tail.start());
        final char open = quote.equals("»") ? '«' : '“';
        return before.chars().filter(c -> c == open).count()
                > before.chars().filter(c -> c == quote.charAt(0)).count();
    }
}
