package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;

/** The auxiliary unit {@code EpubReader} appends to every book: its identity, its slots and their stable ids. */
class EpubReaderAuxiliaryTest {

    @TempDir
    private Path tempDir;

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    // Every opened EPUB ends with one auxiliary unit: id aux, the package path as href, the auxiliary media type,
    // holding the package's title/creators/description and each content document's head title.
    @Test
    void read_bookWithMetadataAndTwelveHeadTitles_endsWithTheAuxiliaryUnit() {
        final Document document = newReader().read(frankenstein(12, "A gothic novel."));

        final Unit last = document.units().get(document.units().size() - 1);
        assertThat(document.units()).hasSize(13);
        assertThat(last.id()).isEqualTo("aux");
        assertThat(last.href()).isEqualTo("OEBPS/content.opf");
        assertThat(last.mediaType()).isEqualTo("application/x-bookloom-auxiliary");
        assertThat(last.isAuxiliary()).isTrue();
        assertThat(last.segments())
                .extracting(Segment::id, Segment::kind, Segment::masked)
                .startsWith(
                        tuple("aux:title", SegmentKind.METADATA_TITLE, "Frankenstein"),
                        tuple("aux:creator:0", SegmentKind.METADATA_AUTHOR, "Mary Shelley"),
                        tuple("aux:creator:1", SegmentKind.METADATA_AUTHOR, "Percy Shelley"),
                        tuple("aux:description:0", SegmentKind.METADATA_DESCRIPTION, "A gothic novel."));
        assertThat(last.segments()).hasSize(4 + 12);
        assertThat(last.segments().subList(4, 16)).extracting(Segment::kind).containsOnly(SegmentKind.TITLE);
        assertThat(last.segments().get(4).id()).isEqualTo("aux:head-title:OEBPS/c00.xhtml");
    }

    @Test
    void read_bookWithAuxiliaryUnit_leavesBodyUnitsIdsOrdersAndSegmentsAsTheyWere() {
        final Document document = newReader().read(frankenstein(12, "A gothic novel."));

        assertThat(document.units().subList(0, 12))
                .extracting(Unit::order)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(document.units().get(0).id()).isEqualTo("OEBPS/c00.xhtml");
        assertThat(document.units().get(0).segments())
                .extracting(Segment::id, Segment::kind)
                .containsExactly(
                        tuple("OEBPS/c00.xhtml:0", SegmentKind.HEADING),
                        tuple("OEBPS/c00.xhtml:1", SegmentKind.PARAGRAPH));
        assertThat(document.units().get(11).segments()).hasSize(2);
    }

    @Test
    void read_sameFileTwice_yieldsIdenticalAuxiliarySegmentIds() {
        final Path epub = frankenstein(3, "A gothic novel.");
        final EpubReader reader = newReader();

        final List<String> first = auxiliaryIds(reader.read(epub));
        final List<String> second = auxiliaryIds(reader.read(epub));

        assertThat(first).isEqualTo(second).contains("aux:title", "aux:head-title:OEBPS/c02.xhtml");
    }

    @Test
    void read_emptyDescription_yieldsNoDescriptionSegment() {
        final Document document = newReader().read(frankenstein(1, "  "));

        assertThat(auxiliaryIds(document)).doesNotContain("aux:description:0");
    }

    @Test
    void read_headTitleMissing_yieldsNoHeadTitleSegmentForThatDocument() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithSpine("c01"))
                .entry("OEBPS/c01.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml"><head></head><body><p>Prose.</p></body></html>
                        """)
                .writeTo(epub);

        assertThat(auxiliaryIds(newReader().read(epub))).containsExactly("aux:title");
    }

    private static List<String> auxiliaryIds(Document document) {
        return document.units().get(document.units().size() - 1).segments().stream()
                .map(Segment::id)
                .toList();
    }

    private Path frankenstein(int documents, String description) {
        final String[] ids = new String[documents];
        final EpubZipBuilder builder = new EpubZipBuilder().mimetype().entry("META-INF/container.xml", CONTAINER_XML);
        final StringBuilder manifest = new StringBuilder();
        final StringBuilder spine = new StringBuilder();
        for (int i = 0; i < documents; i++) {
            ids[i] = "c%02d".formatted(i);
            builder.entry("OEBPS/" + ids[i] + ".xhtml", chapter("Chapter " + i));
            manifest.append("<item id=\"%s\" href=\"%s.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
                    .formatted(ids[i], ids[i]));
            spine.append("<itemref idref=\"%s\"/>\n".formatted(ids[i]));
        }
        final String opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Frankenstein</dc:title>
                    <dc:creator>Mary Shelley</dc:creator>
                    <dc:creator>Percy Shelley</dc:creator>
                    <dc:description>%s</dc:description>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>%s</manifest>
                  <spine>%s</spine>
                </package>
                """.formatted(description, manifest, spine);
        return builder.entry("OEBPS/content.opf", opf).writeTo(tempDir.resolve("frankenstein-" + documents + ".epub"));
    }

    private static String opfWithSpine(String... ids) {
        final StringBuilder manifest = new StringBuilder();
        final StringBuilder spine = new StringBuilder();
        for (final String id : ids) {
            manifest.append(
                    "<item id=\"%s\" href=\"%s.xhtml\" media-type=\"application/xhtml+xml\"/>\n".formatted(id, id));
            spine.append("<itemref idref=\"%s\"/>\n".formatted(id));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    %s
                  </manifest>
                  <spine>
                    %s
                  </spine>
                </package>
                """.formatted(manifest, spine);
    }

    private static EpubReader newReader() {
        return new EpubReader(new OpenEpubRegistry());
    }

    private static String chapter(String title) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>%s</title></head>
                <body><h1>%s</h1><p>Prose.</p></body>
                </html>
                """.formatted(title, title);
    }
}
