package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;

/** The one naming rule a side file follows beside the written book. */
class SideFileTest {

    // Keeping the format suffix, or removing only part of a composite one, would name the side file wrongly.
    @ParameterizedTest
    @CsvSource({
        "/books/Frankenstein.uk.epub, EPUB, GLOSSARY_CSV, /books/Frankenstein.uk.glossary.csv",
        "/books/Frankenstein.uk.epub, EPUB, BILINGUAL_HTML, /books/Frankenstein.uk.bilingual.html",
        "/books/Frankenstein.uk.epub, EPUB, QUALITY_REPORT, /books/Frankenstein.uk.report.md",
        "/books/Kobzar.uk.fb2.zip, FB2, GLOSSARY_CSV, /books/Kobzar.uk.glossary.csv",
        "/books/Kobzar.uk.fb2, FB2, GLOSSARY_CSV, /books/Kobzar.uk.glossary.csv",
        "/books/Book.uk.MD, MARKDOWN, QUALITY_REPORT, /books/Book.uk.report.md",
        "/books/Book.uk.markdown, MARKDOWN, BILINGUAL_HTML, /books/Book.uk.bilingual.html",
        "/books/Notes.uk.txt, TXT, QUALITY_REPORT, /books/Notes.uk.report.md"
    })
    void pathBeside_destinationOfItsFormat_removesTheFormatSuffix(
            final String destination, final BookFormat format, final SideFile sideFile, final String expected) {
        assertThat(sideFile.pathBeside(Path.of(destination), format)).isEqualTo(Path.of(expected));
    }

    // A name the format does not match keeps its whole name instead of failing the export.
    @ParameterizedTest
    @CsvSource({
        "/books/Notes, EPUB, GLOSSARY_CSV, /books/Notes.glossary.csv",
        "/books/Kobzar.uk.fb2.zip, EPUB, QUALITY_REPORT, /books/Kobzar.uk.fb2.zip.report.md"
    })
    void pathBeside_nameTheFormatDoesNotMatch_keepsTheWholeName(
            final String destination, final BookFormat format, final SideFile sideFile, final String expected) {
        assertThat(sideFile.pathBeside(Path.of(destination), format)).isEqualTo(Path.of(expected));
    }

    // Each side file carries its own suffix, the part the Export screen and the writer share.
    @ParameterizedTest
    @CsvSource({"GLOSSARY_CSV, .glossary.csv", "BILINGUAL_HTML, .bilingual.html", "QUALITY_REPORT, .report.md"})
    void suffix_eachSideFile_isItsFileEnding(final SideFile sideFile, final String expected) {
        assertThat(sideFile.suffix()).isEqualTo(expected);
    }
}
