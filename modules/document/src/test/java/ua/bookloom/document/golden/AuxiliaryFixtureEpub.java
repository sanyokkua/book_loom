package ua.bookloom.document.golden;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * An EPUB carrying every kind of auxiliary text at once (task 6.6): a navigation document outside the spine, an
 * NCX reading the same labels, a package title and two authors, a head title and an image with alt text. Built in
 * this repository rather than downloaded, like {@link PrimaryFixtureEpub}.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AuxiliaryFixtureEpub {

    static final String TITLE = "Frankenstein";
    static final String ALT = "Figure 1";
    static final List<String> LABELS = List.of("Prologue", "The Storm", "Epilogue");

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
                <dc:title>Frankenstein</dc:title>
                <dc:creator>Mary Shelley</dc:creator>
                <dc:creator>Percy Shelley</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="nav" href="toc01.html" media-type="application/xhtml+xml" properties="nav"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="c01"/>
              </spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head><title>Chapter One</title></head>
            <body>
            <h1>Chapter One</h1>
            <p>It was a dark night.</p>
            <p>Figure <img src="fig1.png" alt="Figure 1" title="A drawing"/></p>
            </body>
            </html>
            """;

    /** The book with a table of contents listing {@code labels}, in both the navigation document and the NCX. */
    static Path build(Path destination, List<String> labels) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .entry("OEBPS/toc01.html", navigation(labels))
                .entry("OEBPS/toc.ncx", ncx(labels))
                .binaryEntry("OEBPS/fig1.png", new byte[] {1, 2, 3})
                .writeTo(destination);
    }

    private static String navigation(List<String> labels) {
        final String items = labels.stream()
                .map(label -> "<li><a href=\"c01.xhtml\">%s</a></li>".formatted(label))
                .collect(Collectors.joining());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>Contents</title></head>
                <body><nav epub:type="toc"><ol>%s</ol></nav></body>
                </html>
                """.formatted(items);
    }

    private static String ncx(List<String> labels) {
        final String points = IntStream.range(0, labels.size())
                .mapToObj(i -> ("<navPoint id=\"np%d\" playOrder=\"%d\"><navLabel><text>%s</text></navLabel>"
                                + "<content src=\"c01.xhtml\"/></navPoint>")
                        .formatted(i, i + 1, labels.get(i)))
                .collect(Collectors.joining("\n"));
        return "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n"
                + "<head/><docTitle><text>Book</text></docTitle>\n<navMap>\n" + points + "\n</navMap>\n</ncx>\n";
    }
}
