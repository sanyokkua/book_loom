package ua.bookloom.pipeline.typography;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * One sentence of speech the model cut into two quoted runs — a closer, white space only, an opener — is one run again
 * when the source holds a single quoted run. Narration between two runs is words, so the pattern never matches it.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SplitSpeech {

    static Edit apply(final String source, final String text, final QuotePair primary) {
        if (QuoteMarks.runsIn(source) != 1) {
            return new Edit(text, 0);
        }
        final Pattern gap = Pattern.compile(Pattern.quote(String.valueOf(primary.close())) + "[ \\t\\u00A0]+"
                + Pattern.quote(String.valueOf(primary.open())));
        final java.util.regex.Matcher matcher = gap.matcher(text);
        final StringBuilder out = new StringBuilder();
        int count = 0;
        int cursor = 0;
        while (matcher.find()) {
            out.append(text, cursor, matcher.start()).append(' ');
            cursor = matcher.end();
            count++;
        }
        out.append(text, cursor, text.length());
        return new Edit(out.toString(), count);
    }
}
