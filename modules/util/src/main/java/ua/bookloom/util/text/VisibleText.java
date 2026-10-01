package ua.bookloom.util.text;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The one answer to "would a reader see anything here", shared by every format's segment walk and by the pipeline.
 *
 * <p>{@link String#isBlank()} answers a narrower question — Java whitespace — so a paragraph of a lone non-breaking
 * space, a zero-width space, a byte-order mark or a soft hyphen passed it as text and became a segment the model was
 * asked to translate. Invisible here is every Unicode separator ({@code \p{Z}}), control ({@code \p{Cc}}) and format
 * character ({@code \p{Cf}}, which holds U+200B, U+FEFF, U+2060 and the soft hyphen U+00AD).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VisibleText {

    /**
     * Reports whether {@code text} holds nothing a reader would see.
     *
     * @param text the text to test; never null
     * @return {@code true} if every code point is a separator, control or format character (or there is none),
     *     {@code false} otherwise
     */
    public static boolean isBlank(final CharSequence text) {
        Objects.requireNonNull(text, "text");
        return text.codePoints().allMatch(VisibleText::isInvisible);
    }

    /**
     * Drops every invisible code point from {@code text}.
     *
     * @param text the text to reduce; never null
     * @return the visible code points in their order; empty when {@link #isBlank} holds
     */
    public static String visible(final CharSequence text) {
        Objects.requireNonNull(text, "text");
        final StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().filter(codePoint -> !isInvisible(codePoint)).forEach(kept::appendCodePoint);
        return kept.toString();
    }

    private static boolean isInvisible(final int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.SPACE_SEPARATOR,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.CONTROL,
                    Character.FORMAT -> true;
            default -> false;
        };
    }
}
