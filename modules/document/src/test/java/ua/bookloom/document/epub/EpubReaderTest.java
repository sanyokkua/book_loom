package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * {@code EpubReader}'s read path, against tiny ad-hoc EPUB fixtures built in {@link EpubZipBuilder}.
 */
class EpubReaderTest {

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

    private static final String NCX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="np1"><navLabel><text>Chapter One</text></navLabel><content src="c01.xhtml"/></navPoint>
              </navMap>
            </ncx>
            """;

    // the system SHALL process content documents in the order the spine declares, not
    // the order the zip entries happen to appear in.
    @Test
    void read_spineOrderDiffersFromZipOrder_followsSpineOrder() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(false))
                .entry("OEBPS/c02.xhtml", chapter("Chapter Two"))
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .writeTo(epub);

        final Document document = newReader().read(epub);

        assertThat(document.units()).extracting(Unit::href).containsExactly("OEBPS/c01.xhtml", "OEBPS/c02.xhtml");
        assertThat(document.units()).extracting(Unit::order).containsExactly(0, 1);
    }

    // WHEN an EPUB 2 book carrying an NCX and no nav document is parsed, THEN parsing
    // succeeds and its spine documents are read in declared order.
    @Test
    void read_epub2WithNcxAndNoNav_parsesSuccessfully_inSpineOrder() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(true))
                .entry("OEBPS/toc.ncx", NCX)
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .entry("OEBPS/c02.xhtml", chapter("Chapter Two"))
                .writeTo(epub);

        final Document document = newReader().read(epub);

        assertThat(document.units()).extracting(Unit::href).containsExactly("OEBPS/c01.xhtml", "OEBPS/c02.xhtml");
        assertThat(document.format()).isEqualTo(ua.bookloom.api.document.BookFormat.EPUB);
    }

    // the system SHALL compute a SHA-256 hash over the imported source file and carry it
    // on the parsed document; the same file parsed twice SHALL yield the same hash.
    @Test
    void read_sameFileParsedTwice_reportsIdenticalContentHash() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(false))
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .entry("OEBPS/c02.xhtml", chapter("Chapter Two"))
                .writeTo(epub);
        final EpubReader reader = newReader();

        final Document first = reader.read(epub);
        final Document second = reader.read(epub);

        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.contentHash()).hasSize(64);
    }

    @Test
    void read_missingFile_isACorruptContainerFailure() {
        final EpubReader reader = newReader();

        assertThatThrownBy(() -> reader.read(tempDir.resolve("missing.epub")))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void read_bookMetadata_carriesTitleAndAuthor() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(false))
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .entry("OEBPS/c02.xhtml", chapter("Chapter Two"))
                .writeTo(epub);

        final Document document = newReader().read(epub);

        assertThat(document.metadata()).containsEntry("title", "Test Book").containsEntry("author", "A. Author");
        assertThat(document.declaredLang()).isEqualTo("en");
    }

    // WHERE an EPUB package nests its Dublin Core metadata elements inside a legacy
    // wrapper element rather than placing them directly under the metadata element, the system SHALL read them
    // from that nested location.
    @Test
    void read_dublinCoreNestedInLegacyDcMetadataWrapper_carriesTitleAuthorAndLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithNestedDcMetadata())
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .writeTo(epub);

        final Document document = newReader().read(epub);

        assertThat(document.metadata())
                .containsEntry("title", "Alice's Adventures in Wonderland")
                .containsEntry("author", "Lewis Carroll");
        assertThat(document.declaredLang()).isEqualTo("en-GB");
    }

    // Reproduces aliceDynamic.epub's content.opf verbatim: <package>'s default namespace is OPF and
    // <dc-metadata> is written unprefixed, so the wrapper itself inherits OPF while only its dc:-prefixed
    // children carry the Dublin Core namespace.
    private static String opfWithNestedDcMetadata() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
                  <metadata>
                    <dc-metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>Alice's Adventures in Wonderland</dc:title>
                      <dc:creator>Lewis Carroll</dc:creator>
                      <dc:language>en-GB</dc:language>
                    </dc-metadata>
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

    /**
     * Sigil and friends leave itemrefs whose files were deleted; Apple Books shows what exists. One real book in
     * the owner's collection ships only the odd-numbered pages its spine lists, and every reader opens it.
     */
    // WHEN a spine item names a file the archive does not contain, THEN that item is skipped and the remaining
    // spine documents are read in spine order with dense unit orders.
    @Test
    void read_spineItemMissingFromArchive_skipsItAndReadsTheRest() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithSpine("c01", "c02", "c03"))
                .entry("OEBPS/c01.xhtml", chapter("Chapter One"))
                .entry("OEBPS/c03.xhtml", chapter("Chapter Three"))
                .writeTo(epub);

        final Document document = newReader().read(epub);

        assertThat(document.units()).extracting(Unit::href).containsExactly("OEBPS/c01.xhtml", "OEBPS/c03.xhtml");
        assertThat(document.units()).extracting(Unit::order).containsExactly(0, 1);
    }

    // IF no spine item's file exists in the archive at all, THEN the book is refused as corrupt — there is nothing
    // to translate.
    @Test
    void read_everySpineItemMissingFromArchive_isACorruptContainerFailure() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithSpine("c01", "c02"))
                .writeTo(epub);

        assertThatThrownBy(() -> newReader().read(epub)).isInstanceOf(CorruptContainerException.class);
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

    private static String opf(boolean withNcxToc) {
        final String tocAttr = withNcxToc ? " toc=\"ncx\"" : "";
        final String ncxItem =
                withNcxToc ? "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>" : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>A. Author</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
                    %s
                  </manifest>
                  <spine%s>
                    <itemref idref="c01"/>
                    <itemref idref="c02"/>
                  </spine>
                </package>
                """.formatted(ncxItem, tocAttr);
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
