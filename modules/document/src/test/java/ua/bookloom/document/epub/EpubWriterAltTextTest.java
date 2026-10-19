package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapter;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapterItem;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.entryText;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.opf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/** {@code EpubWriter} writing an image's translated {@code alt}, in or out of a translated paragraph. */
class EpubWriterAltTextTest {

    private static final String FIGURE = "<img src=\"fig1.png\" alt=\"Figure 1\" />";
    private static final String FIRST_ALT = "aux:alt:OEBPS/c01.xhtml:0.0";
    private static final String SECOND_ALT = "aux:alt:OEBPS/c01.xhtml:0.1";
    private static final String BODY = "OEBPS/c01.xhtml:0";

    @TempDir
    private Path tempDir;

    private AuxiliaryEpubSupport support;

    @BeforeEach
    void createSupport() {
        support = new AuxiliaryEpubSupport(tempDir);
    }

    private Document openParagraph(String paragraphContent) {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>" + paragraphContent + "</p>"));
        return support.open(support.epub("book.epub", entries));
    }

    private String chapterAfter(Document document, Map<String, String> targets) {
        return entryText(support.write(document, targets, "en"), "OEBPS/c01.xhtml");
    }

    @Test
    void write_imageInTranslatedParagraph_keepsTranslatedAlt() {
        final Document document = openParagraph("Before " + FIGURE + " after");

        final String chapter = chapterAfter(document, Map.of(BODY, "До " + FIGURE + " після", FIRST_ALT, "Рисунок 1"));

        assertThat(chapter).contains("<p>До <img src=\"fig1.png\" alt=\"Рисунок 1\" /> після</p>");
    }

    @Test
    void write_imageMovedWithinTranslatedParagraph_imageStillReadsTranslatedAlt() {
        final Document document = openParagraph("Before " + FIGURE + " after");

        final String chapter = chapterAfter(document, Map.of(BODY, FIGURE + " До після", FIRST_ALT, "Рисунок 1"));

        assertThat(chapter).contains("<p><img src=\"fig1.png\" alt=\"Рисунок 1\" /> До після</p>");
    }

    @Test
    void write_imageInUntranslatedParagraph_stillTakesTranslatedAlt() {
        final Document document = openParagraph("Before " + FIGURE + " after");

        final String chapter = chapterAfter(document, Map.of(FIRST_ALT, "Рисунок 1"));

        assertThat(chapter).contains("<p>Before <img src=\"fig1.png\" alt=\"Рисунок 1\" /> after</p>");
    }

    @Test
    void write_identicalImagesInTranslatedParagraph_takeTheirDescriptionsInOrder() {
        final String dot = "<img src=\"dot.png\" alt=\"Dot\" />";
        final Document document = openParagraph(dot + " and " + dot);

        final String chapter = chapterAfter(
                document, Map.of(BODY, dot + " і " + dot, FIRST_ALT, "Перша точка", SECOND_ALT, "Друга точка"));

        assertThat(chapter)
                .contains("<p><img src=\"dot.png\" alt=\"Перша точка\" /> і "
                        + "<img src=\"dot.png\" alt=\"Друга точка\" /></p>");
    }

    @Test
    void write_ampersandAltInTranslatedParagraph_isEscapedOnce() {
        final Document document = openParagraph("Before " + FIGURE + " after");

        final String chapter =
                chapterAfter(document, Map.of(BODY, "До " + FIGURE + " після", FIRST_ALT, "Том & Джеррі"));

        assertThat(chapter).contains("alt=\"Том &amp; Джеррі\"").doesNotContain("&amp;amp;");
    }

    @Test
    void write_ampersandAltInUntranslatedParagraph_isEscapedOnce() {
        final Document document = openParagraph("Before " + FIGURE + " after");

        final String chapter = chapterAfter(document, Map.of(FIRST_ALT, "Том & Джеррі"));

        assertThat(chapter).contains("alt=\"Том &amp; Джеррі\"").doesNotContain("&amp;amp;");
    }

    @Test
    void write_imageOutsideAnyRun_takesTranslatedAlt() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<div>" + FIGURE + "</div><p>Prose.</p>"));
        final Document document = support.open(support.epub("book.epub", entries));

        final String chapter = chapterAfter(document, Map.of("aux:alt:OEBPS/c01.xhtml:0.0", "Рисунок 1"));

        assertThat(chapter).contains("<div><img src=\"fig1.png\" alt=\"Рисунок 1\" /></div>");
    }
}
