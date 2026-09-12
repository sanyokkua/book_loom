package ua.bookloom.document.fixture;

import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * A minimal single-chapter EPUB built around whatever body content a test hands it.
 *
 * <p>{@link EpubFixtures} keeps one named builder per surveyed pathology, which is right for a fixture the
 * catalogue sweep drives by name. A regression test for a defect measured on one exact markup shape needs the
 * opposite: the shape written where the assertion can be read beside it, and no catalogue entry, because the
 * fixture proves one thing and is exercised once. This class is that seam — and it exists rather than a fourth
 * private copy of the container/OPF/chapter boilerplate, which is what the two fixture classes and the golden
 * package's own builder already amount to.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BodyContentEpub {

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
                <dc:title>Body Fixture</dc:title>
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
     * Writes a one-chapter EPUB whose {@code <body>} holds exactly {@code bodyContent}.
     *
     * @param destination the {@code .epub} file to write
     * @param bodyContent the chapter's body markup, spliced in verbatim so a fixture can control every byte —
     *     including the leading line feeds a preformatted block's regression depends on
     * @return {@code destination}
     */
    public static Path withBody(Path destination, String bodyContent) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF_XML)
                .entry("OEBPS/c01.xhtml", chapter(bodyContent))
                .writeTo(destination);
    }

    // Deliberately not a text block: Java's incidental-whitespace stripping would rewrite a fixture whose whole
    // point can be a run of blank lines inside a <pre>. A single literal keeps every byte of bodyContent exact.
    @SuppressWarnings("StringConcatToTextBlock")
    private static String chapter(String bodyContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><title>Body Fixture</title></head>\n"
                + "<body>\n"
                + bodyContent
                + "</body>\n"
                + "</html>\n";
    }
}
