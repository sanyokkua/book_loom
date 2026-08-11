package ua.bookloom.document.epub;

import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The media types that mark an encrypted EPUB resource as an obfuscated font rather than protected content
 * (ADR-0026).
 *
 * <p><strong>Why an explicit list and not a substring test.</strong> A survey of 194 real books found the
 * encrypted resources declared as {@code application/vnd.ms-opentype} (57), {@code application/x-font-otf} (40)
 * and {@code application/x-font-ttf} (2) — and a substring test for "font" matches none of the 57
 * {@code vnd.ms-opentype} cases. The file extension is no better: {@code .otf} appears under two media types and
 * {@code .ttf} under two. The remaining entries are the historical and RFC 8081 font types, included so the next
 * book does not need a code change to open.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FontMediaTypes {

    private static final Set<String> FONT_MEDIA_TYPES = Set.of(
            "application/vnd.ms-opentype",
            "application/x-font-otf",
            "application/x-font-ttf",
            "application/x-font-truetype",
            "application/font-sfnt",
            "application/font-woff",
            "font/otf",
            "font/ttf",
            "font/woff",
            "font/woff2",
            "font/sfnt",
            "font/collection");

    /**
     * Whether a manifest-declared media type names a font.
     *
     * @param mediaType the manifest item's declared media type, possibly carrying parameters or odd casing
     * @return {@code true} if it names a font, {@code false} for anything else including {@code null}
     */
    static boolean isFont(String mediaType) {
        final int parameterStart = mediaType.indexOf(';');
        final String bare = parameterStart < 0 ? mediaType : mediaType.substring(0, parameterStart);
        return FONT_MEDIA_TYPES.contains(bare.trim().toLowerCase(Locale.ROOT));
    }
}
