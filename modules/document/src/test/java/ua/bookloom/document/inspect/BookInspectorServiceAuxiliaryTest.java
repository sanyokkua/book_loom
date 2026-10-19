package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;

/** The auxiliary unit is neither a structure node nor part of any count the inspection reports. */
class BookInspectorServiceAuxiliaryTest {

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
                <dc:title>Two Chapters</dc:title>
                <dc:creator>A. Author</dc:creator>
                <dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c01"/><itemref idref="c02"/></spine>
            </package>
            """;

    @TempDir
    private Path tempDir;

    @Test
    void structure_epubWithAuxiliaryUnit_givesItNoNodeAndCountsNoneOfItsSegments() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Document document = pair.reader().read(twoChapterBook());

        final List<StructureNode> nodes = pair.inspection().structure(document);

        final Unit last = document.units().get(document.units().size() - 1);
        assertThat(last.isAuxiliary()).isTrue();
        assertThat(last.segments()).isNotEmpty();
        assertThat(nodes).extracting(StructureNode::segmentCount).containsExactly(3, 2);
    }

    @Test
    void stats_epubWithAuxiliaryUnit_countsBodySegmentsOnly() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Document document = pair.reader().read(twoChapterBook());

        assertThat(pair.inspection().stats(document).segments()).isEqualTo(5);
    }

    private Path twoChapterBook() {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", chapter("<p>One.</p><p>Two.</p><p>Three.</p>"))
                .entry("OEBPS/c02.xhtml", chapter("<p>Four.</p><p>Five.</p>"))
                .writeTo(tempDir.resolve("two.epub"));
    }

    private static String chapter(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<head><title>Chapter</title></head><body>" + body + "</body></html>";
    }
}
