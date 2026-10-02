package ua.bookloom.document.mask;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Masks a plain-text (TXT) segment. Plain text has no inline markup to protect, so its text masks to itself unless it
 * contains a literal U+27E6 {@code ⟦} or U+27E7 {@code ⟧}, in which case each occurrence becomes its own token — and,
 * in a paragraph laid out line by line ({@link LineStructure}), each line end inside it gets a line-break token that
 * stands for nothing, placed before the line end, so a translation that merges lines fails the placeholder gate
 * instead of being accepted.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlainTextMasker {

    private static final Pattern LINE_END = Pattern.compile("\\r?\\n");

    /**
     * Masks a TXT segment's source text.
     *
     * @param text the segment's plain source text; never null
     * @return the masked content; never null
     */
    public static MaskedContent mask(String text) {
        Objects.requireNonNull(text, "text");
        final MaskWriter writer = new MaskWriter();
        if (!LineStructure.isLineBased(text)) {
            writer.appendCharacterData(text);
            return writer.build();
        }
        final Matcher lineEnd = LINE_END.matcher(text);
        int cursor = 0;
        while (lineEnd.find()) {
            writer.appendCharacterData(text.substring(cursor, lineEnd.start()));
            if (lineEnd.end() < text.length()) {
                writer.appendLineBreak("");
            }
            writer.appendCharacterData(lineEnd.group());
            cursor = lineEnd.end();
        }
        writer.appendCharacterData(text.substring(cursor));
        return writer.build();
    }
}
