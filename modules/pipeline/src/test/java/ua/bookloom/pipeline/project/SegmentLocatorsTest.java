package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;

/** The locator each segment of a real opened book is shown by: its chapter and paragraph, or its auxiliary kind. */
class SegmentLocatorsTest {

    @TempDir
    private Path tempDir;

    @Test
    void of_twelfthSegmentOfTheFifthBodyUnit_readsItsChapterAndParagraph() {
        final Map<String, SegmentLocator> locators =
                SegmentLocators.of(open(TestBooks.epub(tempDir.resolve("Book.epub"), fiveChapters(), "en")));

        assertThat(locators.get("OEBPS/ch4.xhtml:11")).isEqualTo(new SegmentLocator("ch5 · p12"));
    }

    @Test
    void of_thirdSegmentOfTheFirstBodyUnit_padsTheParagraphToTwoDigits() {
        final Map<String, SegmentLocator> locators =
                SegmentLocators.of(open(TestBooks.epub(tempDir.resolve("Book.epub"), fiveChapters(), "en")));

        assertThat(locators.get("OEBPS/ch0.xhtml:2")).isEqualTo(new SegmentLocator("ch1 · p03"));
    }

    @Test
    void of_navigationLabelsOfTheAuxiliaryUnit_readNavAndTheirPositionAmongLabels() {
        final Document document = open(TestBooks.epubWithNavigation(
                tempDir.resolve("Book.epub"), fiveChapters(), List.of("Chapter One", "Chapter Two")));

        final Map<String, SegmentLocator> locators = SegmentLocators.of(document);

        assertThat(navigationLabels(document))
                .extracting(segment -> locators.get(segment.id()))
                .containsExactly(new SegmentLocator("nav · 01"), new SegmentLocator("nav · 02"));
    }

    @Test
    void of_idTheDocumentDoesNotHold_hasNoEntry() {
        final Map<String, SegmentLocator> locators =
                SegmentLocators.of(open(TestBooks.epub(tempDir.resolve("Book.epub"), fiveChapters(), "en")));

        assertThat(locators).doesNotContainKey("OEBPS/ch9.xhtml:0").containsKey("OEBPS/ch4.xhtml:11");
    }

    /** Five chapters of twelve paragraphs each. */
    private static List<List<String>> fiveChapters() {
        return IntStream.rangeClosed(1, 5)
                .mapToObj(chapter -> IntStream.rangeClosed(1, 12)
                        .mapToObj(paragraph -> "Chapter " + chapter + " paragraph " + paragraph + ".")
                        .toList())
                .toList();
    }

    private static List<Segment> navigationLabels(final Document document) {
        return document.units().stream()
                .filter(Unit::isAuxiliary)
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .toList();
    }

    private static Document open(final Path book) {
        return Objects.requireNonNull(TestDocuments.documents().open(book).data(), "opened " + book);
    }
}
