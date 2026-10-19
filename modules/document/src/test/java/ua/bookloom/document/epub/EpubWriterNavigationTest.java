package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.NAV_ITEM;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.NCX_ITEM;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapter;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapterItem;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.entryBytes;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.entryText;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.navDocument;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.ncx;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.opf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/** {@code EpubWriter} writing navigation labels and NCX labels back, and copying both untouched otherwise. */
class EpubWriterNavigationTest {

    private static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    @TempDir
    private Path tempDir;

    private AuxiliaryEpubSupport support;

    @BeforeEach
    void createSupport() {
        support = new AuxiliaryEpubSupport(tempDir);
    }

    private Map<String, String> bookWith(String manifest, Map<String, String> resources) {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(manifest + chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));
        entries.putAll(resources);
        return entries;
    }

    @Test
    void write_ncxLabelTargetedWithNoNavigationDocument_keepsPlayOrderAndLinkAndReadsTheTarget() {
        final String source = """
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <navMap>
                <navPoint id="np3" playOrder="4"><navLabel><text>Chapter Three</text></navLabel>\
                <content src="c03.xhtml#start"/></navPoint>
                </navMap>
                </ncx>
                """;
        final Document document =
                support.open(support.epub("book.epub", bookWith(NCX_ITEM, Map.of("OEBPS/toc.ncx", source))));

        final Path output = support.write(document, Map.of("aux:ncx:np3", "Розділ третій"), "en");

        assertThat(entryText(output, "OEBPS/toc.ncx"))
                .contains("playOrder=\"4\"")
                .contains("src=\"c03.xhtml#start\"")
                .contains("<text>Розділ третій</text>")
                .doesNotContain("Chapter Three");
    }

    @Test
    void write_navigationLabelTargetedAndNcxReadsTheSame_bothReadTheTarget() {
        final Document document = openStormBook();

        final Path output = support.write(document, Map.of("aux:nav:1", "Буря"), "en");

        assertThat(entryText(output, "OEBPS/toc01.html")).contains(">Буря</a>").doesNotContain("The Storm");
        assertThat(entryText(output, "OEBPS/toc.ncx"))
                .contains("<text>Буря</text>")
                .doesNotContain("The Storm");
    }

    @Test
    void write_navigationLabelHoldsSavedEdit_bothReadTheEdit() {
        final Document document = openStormBook();

        final Path output = support.write(document, Map.of("aux:nav:1", "Шторм"), "en");

        assertThat(entryText(output, "OEBPS/toc01.html")).contains(">Шторм</a>");
        assertThat(entryText(output, "OEBPS/toc.ncx")).contains("<text>Шторм</text>");
    }

    @Test
    void write_noNavigationOrNcxTarget_bothResourcesAreCopiedByteForByte() {
        final String nav = navDocument("", List.of("The Storm"));
        final String ncx = ncx("", List.of("The Storm"));
        final Document document = support.open(support.epub(
                "book.epub", bookWith(NAV_ITEM + NCX_ITEM, Map.of("OEBPS/toc01.html", nav, "OEBPS/toc.ncx", ncx))));

        final Path output = support.write(document, Map.of(), "fr");

        assertThat(entryBytes(output, "OEBPS/toc01.html")).isEqualTo(nav.getBytes(StandardCharsets.UTF_8));
        assertThat(entryBytes(output, "OEBPS/toc.ncx")).isEqualTo(ncx.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void write_navigationRootDeclaresSourceLanguage_rootFollowsAndPrologIsKeptByteForByte() {
        final String nav = DECLARATION + "<!DOCTYPE html>\n"
                + navDocument(" xml:lang=\"en\"", List.of("The Storm")).substring(DECLARATION.length());
        final Document document =
                support.open(support.epub("book.epub", bookWith(NAV_ITEM, Map.of("OEBPS/toc01.html", nav))));

        final Path output = support.write(document, Map.of(), "en");

        final String written = entryText(output, "OEBPS/toc01.html");
        assertThat(written).contains("xml:lang=\"uk\"").doesNotContain("xml:lang=\"en\"");
        assertThat(written).startsWith(DECLARATION + "<!DOCTYPE html>\n");
    }

    @Test
    void write_rewrittenNcxHadNoDeclarationAndOnlyLineFeeds_gainsNeither() {
        final String source = ncx("", List.of("The Storm"));
        final Document document =
                support.open(support.epub("book.epub", bookWith(NCX_ITEM, Map.of("OEBPS/toc.ncx", source))));

        final Path output = support.write(document, Map.of("aux:ncx:np0", "Буря"), "en");

        assertThat(entryText(output, "OEBPS/toc.ncx"))
                .contains("<text>Буря</text>")
                .startsWith("<ncx")
                .doesNotContain("<?xml")
                .doesNotContain("\r");
    }

    @Test
    void write_titleAttributeInFullyTranslatedParagraph_hrefAndTitleAreUnchanged() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>See<a href=\"#n1\" title=\"Footnote one\">1</a> here.</p>"));
        final Document document = support.open(support.epub("book.epub", entries));

        final Path output = support.write(
                document, Map.of("OEBPS/c01.xhtml:0", "Дивись<a href=\"#n1\" title=\"Footnote one\">1</a> тут."), "en");

        assertThat(entryText(output, "OEBPS/c01.xhtml"))
                .contains("href=\"#n1\"")
                .contains("title=\"Footnote one\"");
    }

    private Document openStormBook() {
        final Map<String, String> resources = new LinkedHashMap<>();
        resources.put("OEBPS/toc01.html", navDocument("", List.of("The Storm")));
        resources.put("OEBPS/toc.ncx", ncx("", List.of("The Storm")));
        return support.open(support.epub("book.epub", bookWith(NAV_ITEM + NCX_ITEM, resources)));
    }
}
