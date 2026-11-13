package ua.bookloom.pipeline.batch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether a batch target carries the reply's own structure — a model that loses track of the JSON string it is writing
 * (typographic quotes inside it) leaves the {@code terms} object or the next entry in the text. Only structural
 * shapes count (a key followed by a colon, a brace holding an id key, a code fence), so a paragraph that merely uses
 * the word "terms" or a brace in dialogue is left alone. The text is never stripped: the id falls back to a single
 * draft instead.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtocolLeak {

    private static final String QUOTE_OPEN = "[\"«“]";
    private static final String QUOTE_CLOSE = "[\"»”]";
    private static final String FENCE = "```";
    private static final Pattern KEY = Pattern.compile(QUOTE_OPEN + "(?:terms|items)" + QUOTE_CLOSE + "\\s*:");
    private static final Pattern ID_KEY = Pattern.compile(QUOTE_OPEN + "id" + QUOTE_CLOSE + "\\s*:");
    private static final Pattern FIRST_KEY =
            Pattern.compile("\\{\\s*" + QUOTE_OPEN + "[^\"»”«“\\s]{1,40}" + QUOTE_CLOSE + "\\s*:\\s*[\"«“{]");

    /**
     * Whether a target holds protocol text.
     *
     * @param target the non-null target text
     * @return {@code true} if it holds a structural JSON-looking tail or a code fence, {@code false} otherwise
     */
    public static boolean leaks(final String target) {
        return target.contains(FENCE)
                || KEY.matcher(target).find()
                || FIRST_KEY.matcher(target).find()
                || hasIdKeyInsideBraces(target);
    }

    // An id key with an opening brace before it and no closing brace between, found in one pass over the text.
    private static boolean hasIdKeyInsideBraces(final String target) {
        final Matcher keys = ID_KEY.matcher(target);
        int scanned = 0;
        boolean open = false;
        while (keys.find()) {
            for (; scanned < keys.start(); scanned++) {
                final char c = target.charAt(scanned);
                open = c == '{' || (open && c != '}');
            }
            if (open) {
                return true;
            }
        }
        return false;
    }
}
