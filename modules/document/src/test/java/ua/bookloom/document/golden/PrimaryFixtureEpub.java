package ua.bookloom.document.golden;

import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * The primary golden-round-trip fixture (task 5.1, design.md D6) — one hand-authored EPUB built, deliberately, to
 * trip every trap a naive re-serializer would get wrong: {@code mimetype} first and stored; two {@code
 * dc:language} entries; a spine whose declared order differs from physical zip order; a duplicate element id; a
 * {@code <pre><code>} listing; an out-of-spine stylesheet; an embedded "font"; an XML comment between two block
 * elements; and both a named entity and a numeric character reference, both in ordinary prose text. Authored in
 * this repository rather than downloaded — a real book cannot be committed (design.md D6).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrimaryFixtureEpub {

    /** The declared inner text of the {@code <pre><code>} listing, decoded — what a golden trap test expects to
     * find surviving verbatim in the output ({@link #CHAPTER_TWO} encodes the greater-than sign as a named
     * entity). */
    public static final String CODE_LISTING_TEXT = "int x = 1;\nif (x > 0) { return x; }";

    /** The out-of-spine stylesheet's exact bytes, exposed so a trap test can assert byte-for-byte survival. */
    public static final String STYLESHEET_CONTENT = "body { font-family: serif; }\n";

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Primary Fixture</dc:title>
                <dc:creator>Fixture Author</dc:creator>
                <dc:language>en</dc:language>
                <dc:language>la</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
                <item id="css" href="styles.css" media-type="text/css"/>
                <item id="font" href="fonts/serif.otf" media-type="application/vnd.ms-opentype"/>
              </manifest>
              <spine>
                <itemref idref="c01"/>
                <itemref idref="c02"/>
              </spine>
            </package>
            """;

    /** Two elements share {@code id="note1"} (EC-EPUB-4); {@code id="ch01-p07" class="first"} together prove
     * element identity — not just a duplicate id — survives reassembly (FR-DOC-03's "Element identity survives
     * reassembly" scenario); a comment sits between "Before." and "After." (FR-DOC-03's comment-survival
     * scenario); both a named and a numeric entity appear in prose text. */
    private static final String CHAPTER_ONE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter One</title></head>
            <body>
            <h1>Chapter One</h1>
            <p id="note1">Fish &amp; Chips.</p>
            <p id="ch01-p07" class="first">Before.</p>
            <!-- a comment between two block elements -->
            <p>After.</p>
            <p id="note1">An em dash &#8212; here.</p>
            </body>
            </html>
            """;

    /** Contains the {@code <pre><code>} listing (DD-49) — excluded from segmentation entirely. */
    private static final String CHAPTER_TWO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter Two</title></head>
            <body>
            <h1>Chapter Two</h1>
            <p>Second chapter prose.</p>
            <pre><code>int x = 1;
            if (x &gt; 0) { return x; }</code></pre>
            <p>After the code listing.</p>
            </body>
            </html>
            """;

    /**
     * Builds the fixture at {@code destination}. The zip's physical entry order deliberately puts {@code
     * c02.xhtml} before {@code c01.xhtml}, diverging from the OPF's declared spine order ({@code c01} then {@code
     * c02}) — the trap that proves a reader follows spine order, not zip order (FR-DOC-EPUB-1).
     *
     * @param destination the file to write the fixture to
     * @return {@code destination}
     */
    public static Path build(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c02.xhtml", CHAPTER_TWO)
                .entry("OEBPS/c01.xhtml", CHAPTER_ONE)
                .entry("OEBPS/styles.css", STYLESHEET_CONTENT)
                .binaryEntry("OEBPS/fonts/serif.otf", fontBytes())
                .writeTo(destination);
    }

    /**
     * The embedded "font" bytes this fixture carries at {@code OEBPS/fonts/serif.otf} — arbitrary non-text binary
     * content, not a real font; only byte-for-byte passthrough is under test (FR-DOC-EPUB-4).
     *
     * @return a fresh copy of the fixture's embedded binary payload
     */
    public static byte[] fontBytes() {
        final byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        return payload;
    }
}
