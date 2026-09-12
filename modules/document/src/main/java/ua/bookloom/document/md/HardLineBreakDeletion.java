package ua.bookloom.document.md;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.jspecify.annotations.Nullable;

/**
 * Derives the character positions {@link MarkdownEscaper} deletes to neutralise a model-introduced hard line break
 * — split out of {@link MarkdownEscaper} to keep that class within its size budget; this logic has no other caller.
 *
 * <p>A hard line break has no backslash-escape spelling that removes it: a trailing backslash before a line feed
 * <em>is</em> the other hard-break spelling, so escaping it the way every other construct is escaped would create
 * the very construct it is meant to remove. It is neutralised by deletion instead, in both its spellings — a
 * single backslash character, which carries its own source span, or the two-trailing-spaces spelling, which
 * carries none and is derived from the preceding node's last source span by
 * {@link MarkdownSpans#trailingSpacesRange(Node, String)}, the same derivation {@code MarkdownMasker} uses for
 * masking.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HardLineBreakDeletion {

    /**
     * The positions to delete for {@code hardLineBreak}, or {@code null} if none can be derived.
     *
     * <p><strong>Why the guards, given that neither case has ever been observed.</strong> Measured over 300,000
     * parses across both paths, a space-spelled {@code HardLineBreak} always has a preceding sibling that carries a
     * source span — the CommonMark grammar cannot produce one without a text run before it on the same line. The
     * guards are not there because model output is less trustworthy than source text: both sides go through the
     * same {@link MarkdownReader#parser()}, and a book opened from disk is exactly as untrusted as a translation —
     * that is the path the {@code ArrayIndexOutOfBoundsException} this package already fixed once was on. They are
     * there because nothing pins the parser to that behaviour, and the cost of being wrong is an
     * {@code ErrorCode.internal} where {@code ErrorCode.validation} belongs.
     *
     * @param hardLineBreak the node to neutralise; never null
     * @param text the text {@code hardLineBreak} was parsed from; never null
     * @return the positions to delete, or {@code null} when the break has no preceding sibling, that sibling
     *     carries no source span, or its span ends in no space to delete
     */
    static int @Nullable [] positionsToDelete(HardLineBreak hardLineBreak, String text) {
        if (!hardLineBreak.getSourceSpans().isEmpty()) {
            return new int[] {MarkdownSpans.firstSpanStart(hardLineBreak)};
        }
        final Node precedingText = hardLineBreak.getPrevious();
        if (precedingText == null) {
            return null;
        }
        final int @Nullable [] range = MarkdownSpans.trailingSpacesRange(precedingText, text);
        if (range == null || range[0] == range[1]) {
            return null;
        }
        return rangePositions(range);
    }

    private static int[] rangePositions(int[] range) {
        final int[] positions = new int[range[1] - range[0]];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = range[0] + i;
        }
        return positions;
    }
}
