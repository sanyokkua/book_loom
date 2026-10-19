package ua.bookloom.pipeline.typography;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * Speech split by narration is one quoted run: «Ходімо, — сказав Гейл, — ми запізнюємось». A model sometimes closes the
 * quote before the narration as well, leaving one opener and two closers. When the text has exactly that count and the
 * first closer is followed by a comma and a spaced dash, narration free of quote marks, and a second spaced dash, that
 * first closer is the extra one and goes. Any other shape is left alone.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtraCloser {

    private static final String DASH = "[ \\u00A0][—–][ \\u00A0]";

    static Edit apply(final String text, final QuotePair primary) {
        if (primary.open() == primary.close()
                || count(text, primary.open()) != 1
                || count(text, primary.close()) != 2) {
            return new Edit(text, 0);
        }
        final String open = Pattern.quote(String.valueOf(primary.open()));
        final String close = Pattern.quote(String.valueOf(primary.close()));
        final Matcher shape = Pattern.compile(open + "[^" + open + close + "]*?(" + close + ")(?=," + DASH + "[^\""
                        + open + close + "]+?" + DASH + "[^" + open + close + "]*" + close + ")")
                .matcher(text);
        if (!shape.find()) {
            log.trace("Extra closer: shape does not match");
            return new Edit(text, 0);
        }
        return new Edit(text.substring(0, shape.start(1)) + text.substring(shape.end(1)), 1);
    }

    private static long count(final String text, final char mark) {
        return text.chars().filter(c -> c == mark).count();
    }
}
