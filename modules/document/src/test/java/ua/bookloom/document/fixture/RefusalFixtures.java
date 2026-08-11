package ua.bookloom.document.fixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * The four single-purpose refusal fixtures (task 5.2, design.md D6): each exists to make one failure path
 * provable rather than argued — content encryption, font-obfuscation-only (which must succeed), a missing OPF,
 * and a file that is not a zip archive at all.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RefusalFixtures {

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
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Refusal Fixture</dc:title>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="font" href="fonts/serif.otf" media-type="application/x-font-otf"/>
              </manifest>
              <spine>
                <itemref idref="c01"/>
              </spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter One</title></head>
            <body><p>Prose.</p></body>
            </html>
            """;

    /**
     * Encrypts a <em>content document</em>. Under ADR-0026 the decision is taken from what the cipher reference
     * names, not from the algorithm — so this fixture is refused even though the algorithm it declares is the
     * IDPF font-obfuscation URI, which is precisely the hole an algorithm-only check left open.
     */
    private static final String ENCRYPTION_XML_CONTENT_ENCRYPTED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/c01.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
            """;

    /**
     * Encrypts only a manifest-declared font, under the Adobe algorithm spelled as real books spell it — without
     * the trailing {@code 4}. An exact-match algorithm allowlist refused 16 of 194 surveyed books over exactly
     * this character; ADR-0026 allows it because every encrypted resource is a font.
     */
    private static final String ENCRYPTION_XML_FONT_OBFUSCATION_ONLY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="http://ns.adobe.com/pdf/enc#RC"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/fonts/serif.otf"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
            """;

    private static final String NOT_A_ZIP_CONTENT = "this is not a zip archive at all";

    /**
     * A book whose {@code META-INF/encryption.xml} declares content encryption — EC-EPUB-1, refused with no
     * partial import.
     */
    public static Path contentEncrypted(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("META-INF/encryption.xml", ENCRYPTION_XML_CONTENT_ENCRYPTED)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(destination);
    }

    /**
     * A book whose {@code META-INF/encryption.xml} declares only known IDPF font obfuscation — FR-DOC-EPUB-7,
     * processed normally rather than refused.
     */
    public static Path fontObfuscationOnly(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("META-INF/encryption.xml", ENCRYPTION_XML_FONT_OBFUSCATION_ONLY)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .binaryEntry("OEBPS/fonts/serif.otf", new byte[] {1, 2, 3, 4})
                .writeTo(destination);
    }

    /**
     * An archive whose {@code container.xml} names {@code OEBPS/content.opf}, which the archive never actually
     * contains — EC-EPUB-2.
     */
    public static Path missingOpf(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(destination);
    }

    /** A file named like an EPUB whose bytes are not a zip archive at all — EC-EPUB-2. */
    public static Path notAZip(Path destination) {
        try {
            Files.write(destination, NOT_A_ZIP_CONTENT.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
