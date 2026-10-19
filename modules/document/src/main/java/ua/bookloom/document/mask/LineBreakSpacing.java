package ua.bookloom.document.mask;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Puts a target's whitespace around each line-break token back as the source has it. A model often writes a space where
 * the source has a line end after the token ({@code землю,⟦g0⟧ він}), or moves the line end before it: restored as
 * written, a Markdown hard break becomes {@code землю,\ він} and the paragraph's structure no longer matches, and a TXT
 * line runs into the next. The token itself stays where the model put it; only the spacing on its two sides changes,
 * and only for a token the source has next to a line end.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LineBreakSpacing {

    /**
     * Normalises the spacing around each line-break token of {@code target}.
     *
     * @param sourceMasked the segment's masked form; never null
     * @param target the translated masked text; never null
     * @param lineBreakTokens the segment's line-break tokens; never null
     * @return the target with the whitespace on either side of each such token replaced by the source's; the target
     *     unchanged when the segment has none
     */
    public static String normalise(String sourceMasked, String target, List<String> lineBreakTokens) {
        Objects.requireNonNull(sourceMasked, "sourceMasked");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(lineBreakTokens, "lineBreakTokens");
        String result = target;
        for (final String token : lineBreakTokens) {
            result = respaced(sourceMasked, result, token);
        }
        if (!result.equals(target)) {
            log.debug("Line-break spacing normalised tokens={}", lineBreakTokens.size());
        }
        return result;
    }

    private static String respaced(String sourceMasked, String target, String token) {
        final int inSource = sourceMasked.indexOf(token);
        final int inTarget = target.indexOf(token);
        if (inSource < 0 || inTarget < 0) {
            return target;
        }
        final String before = whitespaceBefore(sourceMasked, inSource);
        final String after = whitespaceAfter(sourceMasked, inSource + token.length());
        if (before.indexOf('\n') < 0 && after.indexOf('\n') < 0) {
            return target;
        }
        final int start = inTarget - whitespaceBefore(target, inTarget).length();
        final int end = inTarget
                + token.length()
                + whitespaceAfter(target, inTarget + token.length()).length();
        return target.substring(0, start) + before + token + after + target.substring(end);
    }

    private static String whitespaceBefore(String text, int index) {
        int start = index;
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, index);
    }

    private static String whitespaceAfter(String text, int index) {
        int end = index;
        while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(index, end);
    }
}
