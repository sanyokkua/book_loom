package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.MarkdownReader;
import ua.bookloom.document.md.OpenMarkdownRegistry;
import ua.bookloom.document.txt.OpenTxtRegistry;
import ua.bookloom.document.txt.TxtInspection;
import ua.bookloom.document.txt.TxtReader;

/**
 * {@code FormatInspection#structure} for every format (task 4.4): each format's navigation mapped onto its
 * content so every top-level node's counts add up to the book's total body-segment count.
 */
class BookInspectorServiceStructureTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    @TempDir
    private Path tempDir;

    private record Chapter(String id, String href, int chapterNumber, String bodyInner) {

        static Chapter of(int index, int paragraphs) {
            return new Chapter("c%02d".formatted(index), "c%02d.xhtml".formatted(index), index, paragraphs(paragraphs));
        }
    }

    private static int totalSegments(Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static int sumOfTopLevelCounts(List<StructureNode> nodes) {
        return nodes.stream()
                .mapToInt(node -> node.segmentCount() == null ? 0 : node.segmentCount())
                .sum();
    }

    private static String paragraphs(int count) {
        final StringBuilder body = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            body.append("<p>Paragraph ").append(i).append(".</p>");
        }
        return body.toString();
    }

    private static String navListOf(List<Chapter> chapters) {
        final StringBuilder nav = new StringBuilder();
        for (final Chapter chapter : chapters) {
            nav.append("<li><a href=\"")
                    .append(chapter.href())
                    .append("\">Chapter ")
                    .append(chapter.chapterNumber())
                    .append("</a></li>");
        }
        return nav.toString();
    }

    private static String manifestOf(List<Chapter> chapters) {
        final StringBuilder manifest = new StringBuilder();
        for (final Chapter chapter : chapters) {
            manifest.append("<item id=\"")
                    .append(chapter.id())
                    .append("\" href=\"")
                    .append(chapter.href())
                    .append("\" media-type=\"application/xhtml+xml\"/>");
        }
        return manifest.toString();
    }

    private static String spineOf(List<Chapter> chapters) {
        final StringBuilder spine = new StringBuilder();
        for (final Chapter chapter : chapters) {
            spine.append("<itemref idref=\"").append(chapter.id()).append("\"/>");
        }
        return spine.toString();
    }

    /** A book whose spine is exactly {@code chapters} and whose navigation is a flat, un-nested nav-document list. */
    private static Path buildFlatNavEpub(Path destination, List<Chapter> chapters) {
        return buildEpub(
                destination,
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>",
                "",
                "<nav epub:type=\"toc\"><ol>" + navListOf(chapters) + "</ol></nav>",
                chapters);
    }

    private static Path buildEpub(
            Path destination, String navManifestItem, String extraSpineAttr, String navBody, List<Chapter> chapters) {
        final String opf = opfHeader()
                + "<manifest>" + navManifestItem + manifestOf(chapters) + "</manifest>"
                + "<spine" + extraSpineAttr + ">" + spineOf(chapters) + "</spine></package>";
        final EpubZipBuilder builder = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf)
                .entry("OEBPS/nav.xhtml", navPage(navBody));
        for (final Chapter chapter : chapters) {
            builder.entry("OEBPS/" + chapter.href(), xhtml(chapter.bodyInner()));
        }
        return builder.writeTo(destination);
    }

    private static String navPage(String navBody) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
                + "<body>" + navBody + "</body></html>";
    }

    private static String xhtml(String bodyInner) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>%s</body>
                </html>
                """.formatted(bodyInner);
    }

    private static String opfHeader() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Structure Fixture</dc:title></metadata>
                """;
    }

    private static Document openEpub(EpubTestFactory.ReaderAndInspection pair, Path source) {
        return pair.reader().read(source);
    }

    @Test
    void structure_epubNavigationTwoChapters_titlesTreeAndCountsMatch() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final List<Chapter> chapters = List.of(Chapter.of(1, 66), Chapter.of(2, 58));
        final Path epub = buildFlatNavEpub(tempDir.resolve("two-chapters.epub"), chapters);

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(nodes).extracting(StructureNode::title).containsExactly("Chapter 1", "Chapter 2");
        assertThat(nodes).extracting(StructureNode::segmentCount).containsExactly(66, 58);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_epubEntriesIntoOneFile_parentCountsChildrenCarryNone() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final String navManifestItem =
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>";
        final String navBody = "<nav epub:type=\"toc\"><ol><li><a href=\"intro.xhtml\">Author's Introduction</a>"
                + "<ol><li><a href=\"intro.xhtml#l1\">Letter 1</a></li>"
                + "<li><a href=\"intro.xhtml#l2\">Letter 2</a></li></ol></li></ol></nav>";
        final List<Chapter> chapters = List.of(new Chapter("intro", "intro.xhtml", 0, paragraphs(41)));
        final Path epub = buildEpub(tempDir.resolve("one-file.epub"), navManifestItem, "", navBody, chapters);

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(nodes).hasSize(1);
        final StructureNode parent = nodes.get(0);
        assertThat(parent.title()).isEqualTo("Author's Introduction");
        assertThat(parent.segmentCount()).isEqualTo(41);
        assertThat(parent.children()).extracting(StructureNode::segmentCount).containsExactly(null, null);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_epub2NcxLabelsFirstSpineDocument_firstNodeTitled() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final String ncx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <navMap>
                    <navPoint id="np-1"><navLabel><text>Preface</text></navLabel><content src="c01.xhtml"/></navPoint>
                  </navMap>
                </ncx>
                """;
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry(
                        "OEBPS/content.opf",
                        opfHeader()
                                + "<manifest><item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
                                + manifestOf(List.of(Chapter.of(1, 3)))
                                + "</manifest><spine toc=\"ncx\">"
                                + spineOf(List.of(Chapter.of(1, 3)))
                                + "</spine></package>")
                .entry("OEBPS/toc.ncx", ncx)
                .entry("OEBPS/c01.xhtml", xhtml(paragraphs(3)))
                .writeTo(tempDir.resolve("ncx-preface.epub"));

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(nodes.get(0).title()).isEqualTo("Preface");
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_epubUnitWithoutEntryHasHeading_takesFirstHeadingAtItsPosition() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final List<Chapter> navChapters = List.of(Chapter.of(1, 2), Chapter.of(2, 3));
        final Chapter interlude = new Chapter("c07", "c07.xhtml", 7, "<h1>Interlude</h1><p>Text.</p>");
        final List<Chapter> allChapters = List.of(navChapters.get(0), navChapters.get(1), interlude);
        final String navManifestItem =
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>";
        final String navBody = "<nav epub:type=\"toc\"><ol>" + navListOf(navChapters) + "</ol></nav>";
        final Path epub = buildEpub(tempDir.resolve("interlude.epub"), navManifestItem, "", navBody, allChapters);

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(nodes).extracting(StructureNode::title).containsExactly("Chapter 1", "Chapter 2", "Interlude");
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_epubUnitWithNeitherEntryNorHeading_takesFileNameAtItsPosition() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final List<Chapter> navChapters = List.of(Chapter.of(1, 1), Chapter.of(2, 1), Chapter.of(3, 1));
        final Chapter fourth = Chapter.of(4, 2);
        final List<Chapter> allChapters = List.of(navChapters.get(0), navChapters.get(1), navChapters.get(2), fourth);
        final String navManifestItem =
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>";
        final String navBody = "<nav epub:type=\"toc\"><ol>" + navListOf(navChapters) + "</ol></nav>";
        final Path epub = buildEpub(tempDir.resolve("fourth-headless.epub"), navManifestItem, "", navBody, allChapters);

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(nodes)
                .extracting(StructureNode::title)
                .containsExactly("Chapter 1", "Chapter 2", "Chapter 3", "c04.xhtml");
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_epubManyChapters_topLevelCountsSumToBookTotal() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final int chapterCount = 20;
        final int perChapter = 62;
        final List<Chapter> chapters = java.util.stream.IntStream.rangeClosed(1, chapterCount)
                .mapToObj(i -> Chapter.of(i, perChapter))
                .toList();
        final Path epub = buildFlatNavEpub(tempDir.resolve("many-chapters.epub"), chapters);

        final Document document = openEpub(pair, epub);
        final List<StructureNode> nodes = pair.inspection().structure(document);

        assertThat(totalSegments(document)).isEqualTo(chapterCount * perChapter);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(chapterCount * perChapter);
    }

    @Test
    void structure_markdownHeadingsNestByLevel_chaptersAreChildrenOfPart() {
        final Path markdown = write(
                tempDir.resolve("nested.md"), "# Part One\n\nIntro.\n\n## Chapter 1\n\nA.\n\n## Chapter 2\n\nB.\n");
        final List<StructureNode> nodes = markdownStructureOf(markdown);
        final Document document = reopenMarkdown(markdown);

        assertThat(nodes).extracting(StructureNode::title).containsExactly("Part One");
        assertThat(nodes.get(0).children()).extracting(StructureNode::title).containsExactly("Chapter 1", "Chapter 2");
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(totalSegments(document));
    }

    @Test
    void structure_markdownTextBeforeFirstHeading_untitledNodeCountsToward() {
        final Path markdown = write(tempDir.resolve("foreword.md"), "Foreword text.\n\n# Chapter 1\n\nBody.\n");
        final List<StructureNode> nodes = markdownStructureOf(markdown);

        assertThat(nodes).extracting(StructureNode::title).containsExactly("", "Chapter 1");
        assertThat(nodes).extracting(StructureNode::segmentCount).containsExactly(1, 2);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(3);
    }

    private static List<StructureNode> markdownStructureOf(Path markdown) {
        final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();
        final MarkdownReader reader = new MarkdownReader(registry);
        final MarkdownInspection inspection = new MarkdownInspection(registry);
        return inspection.structure(reader.read(markdown));
    }

    private static Document reopenMarkdown(Path markdown) {
        return new MarkdownReader(new OpenMarkdownRegistry()).read(markdown);
    }

    @Test
    void structure_fb2ContentOutsideSectionsAndNotesBody_areCounted() {
        final String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><book-title>Sample</book-title></title-info></description>
                  <body>
                    <epigraph><p>Epigraph line.</p></epigraph>
                    <section>
                      <title><p>Chapter 1</p></title>
                      <p>One.</p><p>Two.</p><p>Three.</p><p>Four.</p>
                    </section>
                  </body>
                  <body name="notes"><p>Note one.</p><p>Note two.</p></body>
                </FictionBook>
                """;
        final List<StructureNode> nodes = fb2StructureOf("outside-sections.fb2", xml);

        assertThat(nodes).extracting(StructureNode::title).containsExactly("", "Chapter 1", "notes");
        assertThat(nodes).extracting(StructureNode::segmentCount).containsExactly(1, 5, 2);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(8);
    }

    @Test
    void structure_fb2NestedSections_parentCountsSumOfAllSections() {
        final String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><book-title>Sample</book-title></title-info></description>
                  <body>
                    <section>
                      <title><p>Part One</p></title>
                      <section><title><p>Chapter 1</p></title><p>A.</p></section>
                      <section><title><p>Chapter 2</p></title><p>B.</p><p>C.</p></section>
                    </section>
                  </body>
                </FictionBook>
                """;
        final List<StructureNode> nodes = fb2StructureOf("nested-sections.fb2", xml);

        assertThat(nodes).hasSize(1);
        final StructureNode partOne = nodes.get(0);
        assertThat(partOne.title()).isEqualTo("Part One");
        assertThat(partOne.children()).extracting(StructureNode::title).containsExactly("Chapter 1", "Chapter 2");
        assertThat(partOne.children()).extracting(StructureNode::segmentCount).containsExactly(2, 3);
        assertThat(partOne.segmentCount()).isEqualTo(6);
        assertThat(sumOfTopLevelCounts(nodes)).isEqualTo(6);
    }

    private List<StructureNode> fb2StructureOf(String fileName, String xml) {
        final Path fb2 = Fb2Fixtures.writeFb2(tempDir.resolve(fileName), xml, StandardCharsets.UTF_8);
        final OpenFb2Registry registry = new OpenFb2Registry();
        final Fb2Reader reader = new Fb2Reader(registry);
        final Fb2Inspection inspection = new Fb2Inspection(registry);
        return inspection.structure(reader.read(fb2));
    }

    @Test
    void structure_txt_singleNodeForTheOneUnit() {
        final Path txt = write(tempDir.resolve("notes.txt"), "First paragraph.\n\nSecond paragraph.\n");
        final OpenTxtRegistry registry = new OpenTxtRegistry();
        final TxtReader reader = new TxtReader(registry);
        final TxtInspection inspection = new TxtInspection(registry);

        final Document document = reader.read(txt);
        final List<StructureNode> nodes = inspection.structure(document);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).segmentCount()).isEqualTo(totalSegments(document));
    }

    private static Path write(Path destination, String content) {
        try {
            Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
