package ua.bookloom.document.mask;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Masks a plain-text (TXT) segment. Plain text has no inline markup to protect, so masking is exactly
 * {@link MaskWriter#appendCharacterData(String)}: the text masks to itself unless it contains a literal U+27E6
 * {@code ⟦} or U+27E7 {@code ⟧}, in which case each occurrence becomes its own token.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlainTextMasker {

    /**
     * Masks a TXT segment's source text.
     *
     * @param text the segment's plain source text; never null
     * @return the masked content; never null
     */
    public static MaskedContent mask(String text) {
        Objects.requireNonNull(text, "text");
        final MaskWriter writer = new MaskWriter();
        writer.appendCharacterData(text);
        return writer.build();
    }
}
