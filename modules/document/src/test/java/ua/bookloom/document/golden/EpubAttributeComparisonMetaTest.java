package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.document.epub.EpubZipBuilder;

/** The EPUB comparison must see a line feed in an attribute value that an XML reader would turn into a space. */
class EpubAttributeComparisonMetaTest {

    @TempDir
    private Path tempDir;

    private Path epub(String name, String separator) {
        return new EpubZipBuilder()
                .entry("mimetype", "application/epub+zip", ZipEntry.STORED)
                .entry("OEBPS/chapter.xhtml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
            <body><p data-pdf-bookmark="Communication:%sSo Many Choices">Hello.</p></body></html>
            """.formatted(separator))
                .writeTo(tempDir.resolve(name));
    }

    // WHEN the output writes an attribute's line feed raw where the source held a reference, THEN the comparison
    // reports the difference (an XML reader sees a space in the output).
    @Test
    void epubComparison_attributeLineFeedWrittenRaw_isCaught() {
        final Path source = epub("source.epub", "&#10;");
        final Path output = epub("output.epub", "\n");

        assertThatThrownBy(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .isInstanceOf(AssertionError.class);
    }

    // WHEN source and output both hold the reference, THEN the comparison passes.
    @Test
    void epubComparison_attributeLineFeedReferenceKept_passes() {
        final Path source = epub("source.epub", "&#10;");
        final Path output = epub("output.epub", "&#10;");

        assertThatCode(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .doesNotThrowAnyException();
    }

    private Path epubWithXmlEntry(String name, String entryName, String content) {
        return new EpubZipBuilder()
                .entry("mimetype", "application/epub+zip", ZipEntry.STORED)
                .entry(entryName, content)
                .writeTo(tempDir.resolve(name));
    }

    // WHEN a spine document named .xml is written with a self-closed element expanded, THEN it compares as XHTML.
    @Test
    void epubComparison_xmlNamedXhtmlDocumentReserialized_passes() {
        final String head =
                "<?xml version=\"1.0\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>a<br%s></p></body></html>";
        final Path source = epubWithXmlEntry("source.epub", "OPS/cover.xml", head.formatted("/"));
        final Path output = epubWithXmlEntry("output.epub", "OPS/cover.xml", head.formatted(" /"));

        assertThatCode(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .doesNotThrowAnyException();
    }

    // WHEN an .xml entry that is not an html document differs, THEN it is still compared byte-exact.
    @Test
    void epubComparison_xmlEntryThatIsNotHtmlChanged_isCaught() {
        final Path source = epubWithXmlEntry("source.epub", "META-INF/other.xml", "<root><a/></root>");
        final Path output = epubWithXmlEntry("output.epub", "META-INF/other.xml", "<root><a></a></root>");

        assertThatThrownBy(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .isInstanceOf(AssertionError.class);
    }
}
