package ua.bookloom.pipeline.batch;

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
    private static final Pattern LEAK = Pattern.compile("```"
            + "|" + QUOTE_OPEN + "(?:terms|items)" + QUOTE_CLOSE + "\\s*:"
            + "|\\{[^}]*" + QUOTE_OPEN + "id" + QUOTE_CLOSE + "\\s*:"
            + "|\\{\\s*" + QUOTE_OPEN + "[^\"»”«“\\s]{1,40}" + QUOTE_CLOSE + "\\s*:\\s*[\"«“{]");

    /**
     * Whether a target holds protocol text.
     *
     * @param target the non-null target text
     * @return {@code true} if it holds a structural JSON-looking tail or a code fence, {@code false} otherwise
     */
    public static boolean leaks(final String target) {
        return LEAK.matcher(target).find();
    }
}
