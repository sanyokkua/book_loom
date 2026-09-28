package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.NAV_ITEM;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.NCX_ITEM;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.auxiliaryOf;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapter;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.chapterItem;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.navDocument;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.ncx;
import static ua.bookloom.document.epub.AuxiliaryEpubSupport.opf;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;

/** The navigation labels, NCX labels and image descriptions {@code EpubReader} produces as auxiliary segments. */
class EpubReaderNavigationTest {

    @TempDir
    private Path tempDir;

    private AuxiliaryEpubSupport support;

    @BeforeEach
    void createSupport() {
        support = new AuxiliaryEpubSupport(tempDir);
    }

    @Test
    void read_navigationOutsideSpineWithMatchingNcxAndSevenImages_yieldsElevenLabelsAndSevenAlts() {
        final List<String> labels =
                IntStream.range(0, 11).mapToObj(i -> "Entry " + i).toList();
        final String images = IntStream.range(0, 7)
                .mapToObj(i -> "<p>Figure %d <img src=\"f%d.png\" alt=\"Picture %d\"/></p>".formatted(i, i, i))
                .reduce("", String::concat);
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(NAV_ITEM + NCX_ITEM + chapterItem("c01"), "c01"));
        entries.put("OEBPS/toc01.html", navDocument("", labels));
        entries.put("OEBPS/toc.ncx", ncx("", labels));
        entries.put("OEBPS/c01.xhtml", chapter(images));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(kindCount(auxiliary, SegmentKind.NAV_LABEL)).isEqualTo(11);
        assertThat(kindCount(auxiliary, SegmentKind.ALT)).isEqualTo(7);
        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .extracting(Segment::id)
                .allMatch(id -> id.startsWith("aux:nav:"));
    }

    @Test
    void read_ncxLabelDiffersFromNavigationLabel_yieldsTwoNavLabelSegments() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(NAV_ITEM + NCX_ITEM + chapterItem("c01"), "c01"));
        entries.put("OEBPS/toc01.html", navDocument("", List.of("Chapter 1")));
        entries.put("OEBPS/toc.ncx", ncx("", List.of("Chapter I")));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .extracting(Segment::id, Segment::masked)
                .containsExactly(tuple("aux:nav:1", "Chapter 1"), tuple("aux:ncx:np0", "Chapter I"));
    }

    @Test
    void read_navigationDocumentInSpine_yieldsNoNavLabelAndItsLinksAreBodySegments() {
        final List<String> labels =
                IntStream.range(0, 5).mapToObj(i -> "Entry " + i).toList();
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "OEBPS/content.opf",
                opf(
                        "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\""
                                + " properties=\"nav\"/>" + chapterItem("c01"),
                        "nav",
                        "c01"));
        entries.put("OEBPS/nav.xhtml", navDocument("", labels));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Document document = support.open(support.epub("book.epub", entries));

        assertThat(kindCount(auxiliaryOf(document), SegmentKind.NAV_LABEL)).isZero();
        assertThat(document.units().get(0).id()).isEqualTo("OEBPS/nav.xhtml");
        assertThat(document.units().get(0).segments())
                .extracting(Segment::masked)
                .containsExactly("Entry 0", "Entry 1", "Entry 2", "Entry 3", "Entry 4");
    }

    @Test
    void read_navigationDocumentInSpineAndNcx_yieldsEveryNcxLabelAsItsOwnSegment() {
        final List<String> labels = List.of("One", "Two");
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put(
                "OEBPS/content.opf",
                opf(
                        "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\""
                                + " properties=\"nav\"/>" + NCX_ITEM + chapterItem("c01"),
                        "nav",
                        "c01"));
        entries.put("OEBPS/nav.xhtml", navDocument("", labels));
        entries.put("OEBPS/toc.ncx", ncx("", labels));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .extracting(Segment::id)
                .containsExactly("aux:ncx:np0", "aux:ncx:np1");
    }

    @Test
    void read_ncxNavPointsSharingOneId_yieldsOneUniquelyIdentifiedSegmentPerLabel() {
        final String points = "<navPoint id=\"np\"><navLabel><text>One</text></navLabel><content src=\"c01.xhtml\"/>"
                + "</navPoint><navPoint id=\"np\"><navLabel><text>Two</text></navLabel><content src=\"c01.xhtml\"/>"
                + "</navPoint><navPoint id=\"np\"><navLabel><text>Three</text></navLabel>"
                + "<content src=\"c01.xhtml\"/></navPoint>";
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(NCX_ITEM + chapterItem("c01"), "c01"));
        entries.put(
                "OEBPS/toc.ncx",
                "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><head/>"
                        + "<docTitle><text>Book</text></docTitle><navMap>" + points + "</navMap></ncx>");
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .extracting(Segment::id, Segment::sourceInner)
                .containsExactly(
                        tuple("aux:ncx:np", "One"), tuple("aux:ncx:np:2", "Two"), tuple("aux:ncx:np:3", "Three"));
    }

    @Test
    void read_toc01OutsideSpineWithThreeEntries_yieldsIdsOneTwoThree() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(NAV_ITEM + chapterItem("c01"), "c01"));
        entries.put("OEBPS/toc01.html", navDocument("", List.of("A", "B", "C")));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.NAV_LABEL)
                .extracting(Segment::id)
                .containsExactly("aux:nav:1", "aux:nav:2", "aux:nav:3");
    }

    @Test
    void read_malformedNcx_opensTheBookWithoutNcxLabels() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(NCX_ITEM + chapterItem("c01"), "c01"));
        entries.put("OEBPS/toc.ncx", "<ncx><navMap><navPoint>");
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose.</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(kindCount(auxiliary, SegmentKind.NAV_LABEL)).isZero();
    }

    @Test
    void read_imageWithEmptyAlt_yieldsNoAltSegment() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Prose <img src=\"a.png\" alt=\"\"/> <img src=\"b.png\"/></p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(kindCount(auxiliary, SegmentKind.ALT)).isZero();
    }

    @Test
    void read_imageInsideParagraph_yieldsAltSegmentNamedByUnitAndNodePath() {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("OEBPS/content.opf", opf(chapterItem("c01"), "c01"));
        entries.put("OEBPS/c01.xhtml", chapter("<p>Before <img src=\"fig1.png\" alt=\"Figure 1\"/> after</p>"));

        final Unit auxiliary = auxiliaryOf(support.open(support.epub("book.epub", entries)));

        assertThat(auxiliary.segments())
                .filteredOn(segment -> segment.kind() == SegmentKind.ALT)
                .extracting(Segment::id, Segment::masked)
                .containsExactly(tuple("aux:alt:OEBPS/c01.xhtml:0.0", "Figure 1"));
    }

    private static long kindCount(Unit unit, SegmentKind kind) {
        return unit.segments().stream()
                .filter(segment -> segment.kind() == kind)
                .count();
    }
}
