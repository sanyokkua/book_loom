package ua.bookloom.document.fixture;

import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * The EPUB hazard-paragraph fixture task 9.3 asks for, split out of {@link EpubFixtures} rather than added to it —
 * that class already sits at Checkstyle's 400-line file-length gate, and this module has been repaired twice for
 * exactly that limit; a second, cohesive class is the fix testing.md itself prescribes over raising the limit.
 *
 * <p>Registered in {@code FixtureCatalog} as {@code epub/hazard-paragraph} and also exercised directly by
 * {@code MaskThenRestoreHazardFixturesTest}. This class needs none of {@link EpubFixtures}'s other shapes
 * (self-closed elements, font obfuscation, DRM) and builds its own minimal single-chapter book directly through
 * {@link EpubZipBuilder}.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EpubHazardFixtures {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String OPF_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Hazard Fixture</dc:title>
                <dc:creator>Fixture Author</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c01"/></spine>
            </package>
            """;

    /**
     * One paragraph carrying every hazard a mask-then-restore identity cycle must survive together: an escaped
     * ampersand, a numeric character reference, an emphasis nested inside a bold span, an XML comment, and an
     * inline code span whose own text contains the placeholder bracket {@code ⟦} — the requirement <em>Restore a
     * masked segment to its source content when nothing is translated</em>.
     *
     * @param destination the {@code .epub} file to write
     * @return {@code destination}
     */
    public static Path hazardParagraph(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF_XML)
                .entry("OEBPS/c01.xhtml", chapter())
                .writeTo(destination);
    }

    private static String chapter() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Hazard Fixture</title></head>
                <body>
                <p>Smith &amp; Sons opened in 1880&#8212;1893 with <b>bold <i>and italic</i></b> phrasing.\
                <!-- editor note --> See <code>a⟦b</code> for detail.</p>
                </body>
                </html>
                """;
    }
}
