package ua.bookloom.document.model;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The XML 1.0 {@code Char} production, applied to restored segment content before it is ever handed to a tree
 * format's writer.
 *
 * <p><strong>Why this is checked at the segment boundary and not left to the writer.</strong> The two tree formats
 * disagree about a character XML cannot represent, and both answers are wrong. Measured on a target carrying a
 * U+0008 backspace: FB2's fragment parse throws, and it throws from {@code DocumentService#write}, which aborts
 * before a single byte of the book is written — so one bad segment makes the whole export impossible, and the
 * error names no segment. EPUB accepts the same content and drops the character silently at serialization, so the
 * book exports with data quietly missing. Checking here turns both into one {@code validation} failure of the one
 * segment that caused it, which is the posture the Markdown structure check already takes, and leaves every other
 * segment of the book exportable.
 *
 * <p>The rejected set is the complement of XML 1.0's {@code Char}: C0 controls other than tab, line feed and
 * carriage return; unpaired surrogates; and U+FFFE/U+FFFF. C1 controls are <em>not</em> rejected — XML 1.0 permits
 * them, and only XML 1.1 (which no format here emits) restricts them.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class XmlCharacters {

    private static final int TAB = 0x09;
    private static final int LINE_FEED = 0x0A;
    private static final int CARRIAGE_RETURN = 0x0D;

    /** The first character above the C0 control block, where the contiguous legal range starts. */
    private static final int FIRST_PRINTABLE = 0x20;

    /** The last code point before the surrogate block, which XML text may never contain unpaired. */
    private static final int LAST_BEFORE_SURROGATES = 0xD7FF;

    /** The first code point above the surrogate block. */
    private static final int FIRST_AFTER_SURROGATES = 0xE000;

    /** The last legal BMP code point — U+FFFE and U+FFFF are excluded by the {@code Char} production. */
    private static final int LAST_LEGAL_BMP = 0xFFFD;

    /**
     * Whether {@code text} contains a character no XML 1.0 document can carry.
     *
     * @param text the restored segment content about to be written back into a tree
     * @return {@code true} if at least one character cannot survive an XML round trip, {@code false} if every
     *     character can
     */
    public static boolean hasUnwritableCharacter(String text) {
        Objects.requireNonNull(text, "text");
        int index = 0;
        while (index < text.length()) {
            // codePointAt yields the bare surrogate value for an unpaired surrogate and the combined supplementary
            // code point for a well-formed pair, so the surrogate block test below distinguishes the two for free.
            final int codePoint = text.codePointAt(index);
            if (!isWritable(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isWritable(int codePoint) {
        return codePoint == TAB
                || codePoint == LINE_FEED
                || codePoint == CARRIAGE_RETURN
                || (codePoint >= FIRST_PRINTABLE && codePoint <= LAST_BEFORE_SURROGATES)
                || (codePoint >= FIRST_AFTER_SURROGATES && codePoint <= LAST_LEGAL_BMP)
                || (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT && codePoint <= Character.MAX_CODE_POINT);
    }
}
