package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.epub.EpubZipBuilder;

/** An image's alternative text is restored as plain text, so the writer encodes it exactly once. */
class DocumentServiceAltUnmaskTest {

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
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Book</dc:title></metadata>
              <manifest><item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="c01"/></spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head></head>
            <body><p>Before <img src="fig1.png" alt="Figure 1"/> after</p></body></html>
            """;

    @TempDir
    private Path tempDir;

    @Test
    void unmask_altSegmentWithAmpersand_returnsThePlainTextUnescaped() {
        final DocumentService service = DocumentServices.newService();
        final Segment alt = segmentOfKind(service, SegmentKind.ALT);

        final Result<String> result = service.unmask(BookFormat.EPUB, alt, "Том & Джеррі");

        assertThat(result.data()).isEqualTo("Том & Джеррі");
    }

    @Test
    void unmask_paragraphSegmentWithAmpersand_stillEscapesItAsMarkup() {
        final DocumentService service = DocumentServices.newService();
        final Segment paragraph = segmentOfKind(service, SegmentKind.PARAGRAPH);

        final Result<String> result = service.unmask(BookFormat.EPUB, paragraph, "Том & Джеррі ⟦g0⟧");

        assertThat(result.data()).isEqualTo("Том &amp; Джеррі <img src=\"fig1.png\" alt=\"Figure 1\" />");
    }

    private Segment segmentOfKind(DocumentService service, SegmentKind kind) {
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(tempDir.resolve("book.epub"));
        final Document document = Objects.requireNonNull(service.open(epub).data(), "document");
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == kind)
                .findFirst()
                .orElseThrow();
    }
}
