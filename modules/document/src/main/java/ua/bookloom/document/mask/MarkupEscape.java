package ua.bookloom.document.mask;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Escapes character data for a markup-shaped format's restored fragment (EPUB, FB2) — the requirement
 * <em>Compose a markup-shaped restored fragment so it is well-formed by construction</em>.
 *
 * <p>Only {@code &}, {@code <} and {@code >} are escaped, in that order so a {@code &} introduced by escaping
 * {@code <} or {@code >} is never itself re-escaped. That is sufficient, and matching either serializer's full
 * escaper would be both unnecessary and wrong: the composed fragment this feeds is re-parsed by
 * {@code TreeNode#replaceChildren} before either writer ever serializes it, and — measured against this repo's
 * pinned jsoup and JDOM2 versions — the two disagree beyond this set. jsoup renders U+00A0 as a character reference
 * or the raw character depending on its configured escape mode, while JDOM2 always writes the character; JDOM2
 * escapes the literal sequence {@code ]]>}, while jsoup does not. Escaping to either serializer's rule would be
 * pointless work undone by the re-parse, and picking one over the other would be an arbitrary choice this class has
 * no way to justify.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkupEscape {

    /**
     * Escapes {@code text} as XML/HTML character data.
     *
     * @param text the text to escape; never null
     * @return {@code text} with {@code &}, {@code <} and {@code >} escaped, in that order; never null
     */
    public static String escapeCharacterData(String text) {
        Objects.requireNonNull(text, "text");
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
