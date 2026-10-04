package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.NAV_ITEM;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapter;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapterItem;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.opf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;

/** Which spine documents the reader places in the front matter, the body and the back matter, and on what evidence. */
class EpubReaderRolesTest {

    private static final String NAV_HEAD =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\""
                    + " xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>N</title></head><body>";
    private static final String NAV_TAIL = "</body></html>";
    private static final String STORY = "<p>The story goes on and on for a while.</p>";

    @TempDir
    private Path tempDir;

    private AuxiliaryEpubSupport support;

    @BeforeEach
    void createSupport() {
        support = new AuxiliaryEpubSupport(tempDir);
    }

    @Test
    void read_landmarksNameFrontBodyAndBack_unitsTakeThoseRoles() {
        final String landmarks = "<nav epub:type=\"landmarks\"><ol>"
                + "<li><a epub:type=\"titlepage\" href=\"c01.xhtml\">Title</a></li>"
                + "<li><a epub:type=\"bodymatter\" href=\"c02.xhtml\">Start</a></li>"
                + "<li><a epub:type=\"backmatter\" href=\"c03.xhtml\">End</a></li></ol></nav>";

        final List<UnitRole> roles = rolesOf(book(landmarks, "<p>Title Page text</p>", STORY, "<p>Thanks to all.</p>"));

        assertThat(roles).containsExactly(UnitRole.FRONT_MATTER, UnitRole.BODY, UnitRole.BACK_MATTER);
    }

    @Test
    void read_guideNamesCopyrightPage_unitIsFrontMatter() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "OEBPS/content.opf",
                opf(chapterItem("c01") + chapterItem("c02"), "c01", "c02")
                        .replace(
                                "</spine>",
                                "</spine><guide><reference type=\"copyright-page\" href=\"c01.xhtml\" title=\"C\"/>"
                                        + "</guide>"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>All rights reserved by somebody.</p>"));
        entries.put("OEBPS/c02.xhtml", chapter(STORY));

        final List<UnitRole> roles = rolesOf(support.open(support.epub("guide.epub", entries)));

        assertThat(roles).containsExactly(UnitRole.FRONT_MATTER, UnitRole.BODY);
    }

    @Test
    void read_bodyCarriesEpubType_unitTakesItsRole() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01") + chapterItem("c02"), "c01", "c02"));
        entries.put("OEBPS/c01.xhtml", chapter(STORY));
        entries.put(
                "OEBPS/c02.xhtml",
                chapter(STORY)
                        .replace(
                                "<body>",
                                "<body xmlns:epub=\"http://www.idpf.org/2007/ops\""
                                        + " epub:type=\"backmatter colophon\">"));

        final List<UnitRole> roles = rolesOf(support.open(support.epub("typed.epub", entries)));

        assertThat(roles).containsExactly(UnitRole.BODY, UnitRole.BACK_MATTER);
    }

    @Test
    void read_contentsLabelsNameMatterAroundTheStory_frontBeforeAndBackAfter() {
        final String toc = "<nav epub:type=\"toc\"><ol><li><a href=\"c01.xhtml\">Copyright Page</a></li>"
                + "<li><a href=\"c02.xhtml\">Chapter 1</a></li>"
                + "<li><a href=\"c03.xhtml\">Acknowledgements</a></li></ol></nav>";

        final List<UnitRole> roles = rolesOf(book(toc, "<p>Text copyright by A. Writer.</p>", STORY, "<p>Thanks.</p>"));

        assertThat(roles).containsExactly(UnitRole.FRONT_MATTER, UnitRole.BODY, UnitRole.BACK_MATTER);
    }

    @Test
    void read_unlistedAlsoByPageAfterStory_isBackMatterByItsFirstLine() {
        final String toc = "<nav epub:type=\"toc\"><ol><li><a href=\"c01.xhtml\">Chapter 1</a></li></ol></nav>";

        final List<UnitRole> roles =
                rolesOf(book(toc, STORY, "<p>Also by Jane Doe</p><p>Some Title</p>", "<p>More story text.</p>"));

        assertThat(roles).containsExactly(UnitRole.BODY, UnitRole.BACK_MATTER, UnitRole.BODY);
    }

    @Test
    void read_bookSaysNothing_everyUnitIsBody() {
        final String toc = "<nav epub:type=\"toc\"><ol><li><a href=\"c01.xhtml\">One</a></li></ol></nav>";

        final List<UnitRole> roles = rolesOf(book(toc, STORY, STORY, STORY));

        assertThat(roles).containsOnly(UnitRole.BODY);
    }

    @Test
    void read_titlepageFileName_isFrontMatter() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("titlepage") + chapterItem("c02"), "titlepage", "c02"));
        entries.put("OEBPS/titlepage.xhtml", chapter("<p>A Big Book</p>"));
        entries.put("OEBPS/c02.xhtml", chapter(STORY));

        final List<UnitRole> roles = rolesOf(support.open(support.epub("named.epub", entries)));

        assertThat(roles).containsExactly(UnitRole.FRONT_MATTER, UnitRole.BODY);
    }

    /** Three chapter documents c01..c03 and a navigation document holding {@code navigation}. */
    private Document book(final String navigation, final String first, final String second, final String third) {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "OEBPS/content.opf",
                opf(NAV_ITEM + chapterItem("c01") + chapterItem("c02") + chapterItem("c03"), "c01", "c02", "c03"));
        entries.put("OEBPS/toc01.html", NAV_HEAD + navigation + NAV_TAIL);
        entries.put("OEBPS/c01.xhtml", chapter(first));
        entries.put("OEBPS/c02.xhtml", chapter(second));
        entries.put("OEBPS/c03.xhtml", chapter(third));
        return support.open(support.epub("book.epub", entries));
    }

    private static List<UnitRole> rolesOf(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .map(Unit::role)
                .toList();
    }
}
