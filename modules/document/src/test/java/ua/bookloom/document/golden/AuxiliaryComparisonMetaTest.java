package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * The comparison must not go blind to an attribute merely because a sibling attribute (an image's {@code alt}) is
 * one the writer legitimately changes: a changed neighbouring {@code title} is still reported.
 */
class AuxiliaryComparisonMetaTest {

    @TempDir
    private Path tempDir;

    private Path epub(String name, String alt, String title) {
        return new EpubZipBuilder()
                .entry("mimetype", "application/epub+zip", ZipEntry.STORED)
                .entry("OEBPS/chapter.xhtml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title></head>
            <body><p>Look <img src="f.png" alt="%s" title="%s"/></p></body></html>
            """.formatted(alt, title))
                .writeTo(tempDir.resolve(name));
    }

    @Test
    void epubComparison_neighbouringTitleChangedBesideAnAltChange_isCaught() {
        final Path source = epub("source.epub", "Figure 1", "A drawing");
        final Path output = epub("output.epub", "Рисунок 1", "Малюнок");

        assertThatThrownBy(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void epubComparison_onlyTheNeighbouringTitleChanged_isCaught() {
        final Path source = epub("source.epub", "Figure 1", "A drawing");
        final Path output = epub("output.epub", "Figure 1", "Малюнок");

        assertThatThrownBy(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void epubComparison_altAndTitleBothUnchanged_passes() {
        final Path source = epub("source.epub", "Figure 1", "A drawing");
        final Path output = epub("output.epub", "Figure 1", "A drawing");

        assertThatCode(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .doesNotThrowAnyException();
    }
}
