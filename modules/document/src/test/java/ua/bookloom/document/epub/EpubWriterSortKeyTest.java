package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * A translated title or author must not keep the sort key of its source-language text: the writer drops
 * {@code calibre:title_sort}, {@code opf:file-as} and a {@code file-as} refinement when the text they sort changed,
 * and leaves them untouched when it did not.
 */
class EpubWriterSortKeyTest {

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

    // IF the old key stayed, THEN a library would shelve «Амулет Самарканда» under "Amulet of Samarkand, The".
    @Test
    void write_titleTranslated_dropsCalibreTitleSortAndTheTitlesFileAs() {
        final Path output = writeWithAuxiliaryTarget("aux:title", "Амулет Самарканда");

        final String opf = rawOpfTextOf(output);
        assertThat(opf).contains("<dc:title").contains("Амулет Самарканда");
        assertThat(opf).doesNotContain("calibre:title_sort").doesNotContain("Samarkand, The");
        assertThat(opf).doesNotContain("title-sort-key");
    }

    // IF only the title were handled, THEN the translated author would still sort under the source-language spelling.
    @Test
    void write_authorTranslated_dropsTheAuthorsFileAsAttributeAndRefinement() {
        final Path output = writeWithAuxiliaryTarget("aux:creator:0", "Джонатан Страуд");

        final String opf = rawOpfTextOf(output);
        assertThat(opf).contains("Джонатан Страуд");
        assertThat(opf).doesNotContain("Stroud, Jonathan").doesNotContain("author-sort-key");
        assertThat(opf).contains("calibre:title_sort");
    }

    // IF a book written with no translated title lost its keys, THEN a no-op export would no longer equal its source.
    @Test
    void write_nothingTranslated_keepsEverySortKey() {
        final Path output = writeEdited(document -> document);

        final String opf = rawOpfTextOf(output);
        assertThat(opf)
                .contains("calibre:title_sort")
                .contains("Samarkand, The")
                .contains("Stroud, Jonathan")
                .contains("title-sort-key")
                .contains("author-sort-key");
    }

    // IF the first export's dropped keys were gone from the open book for good, THEN reverting the title would still
    // ship a title with no sort key.
    @Test
    void write_titleTranslatedThenRevertedOnTheSameOpenBook_keepsTheSortKeysTheSecondTime() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf())
                .entry("OEBPS/c01.xhtml", chapter())
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        final EpubWriter writer = new EpubWriter(registry);
        writer.write(
                withAuxiliaryTarget(document, "aux:title", "Амулет Самарканда"), tempDir.resolve("one.epub"), "uk");

        final Path second = writer.write(
                withAuxiliaryTarget(document, "aux:title", "The Amulet of Samarkand"),
                tempDir.resolve("two.epub"),
                "uk");

        assertThat(rawOpfTextOf(second))
                .contains("The Amulet of Samarkand")
                .contains("calibre:title_sort")
                .contains("Samarkand, The")
                .contains("title-sort-key");
    }

    private Path writeWithAuxiliaryTarget(String segmentId, String target) {
        return writeEdited(document -> withAuxiliaryTarget(document, segmentId, target));
    }

    private Path writeEdited(UnaryOperator<Document> edit) {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf())
                .entry("OEBPS/c01.xhtml", chapter())
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        return new EpubWriter(registry).write(edit.apply(document), tempDir.resolve("out.epub"), "uk");
    }

    private static Document withAuxiliaryTarget(Document document, String segmentId, String target) {
        final List<Unit> units = document.units().stream()
                .map(unit -> unit.isAuxiliary() ? unit.withSegments(retargeted(unit, segmentId, target)) : unit)
                .toList();
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                units);
    }

    private static List<Segment> retargeted(Unit unit, String segmentId, String target) {
        return unit.segments().stream()
                .map(segment -> segment.id().equals(segmentId) ? withTarget(segment, target) : segment)
                .toList();
    }

    private static Segment withTarget(Segment segment, String targetInner) {
        return segment.withDecision(ua.bookloom.api.document.SegmentStatus.ACCEPTED, targetInner);
    }

    private static String rawOpfTextOf(Path zip) {
        return new String(contentOf(zip, "OEBPS/content.opf"), StandardCharsets.UTF_8);
    }

    private static byte[] contentOf(Path zip, String entryName) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            final ZipEntry entry = file.getEntry(entryName);
            return file.getInputStream(entry).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String opf() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title id="t1" opf:file-as="Amulet of Samarkand, The">The Amulet of Samarkand</dc:title>
                    <dc:creator id="a1" opf:file-as="Stroud, Jonathan">Jonathan Stroud</dc:creator>
                    <dc:language>en</dc:language>
                    <meta name="calibre:title_sort" content="Amulet of Samarkand, The"/>
                    <meta name="calibre:series" content="Bartimaeus"/>
                    <meta refines="#t1" property="file-as" id="title-sort-key">Amulet of Samarkand, The</meta>
                    <meta refines="#a1" property="file-as" id="author-sort-key">Stroud, Jonathan</meta>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """;
    }

    private static String chapter() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body><p>Paragraph 0.</p></body>
                </html>
                """;
    }
}
