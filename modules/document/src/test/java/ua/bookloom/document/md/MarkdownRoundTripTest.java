package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.MarkdownFixtures;

/**
 * The Markdown read and write paths, over the primary fixture.
 */
class MarkdownRoundTripTest {

    @TempDir
    private Path tempDir;

    private final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();

    private Document open(String markdown, String fileName) {
        return new MarkdownReader(registry).read(MarkdownFixtures.write(tempDir.resolve(fileName), markdown));
    }

    private Document openPrimary() {
        return open(MarkdownFixtures.PRIMARY, "chapter.md");
    }

    private Path writeOut(Document document, String targetLanguage) {
        return new MarkdownWriter(registry).write(document, tempDir.resolve("out.md"), targetLanguage);
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().get(0).segments();
    }

    private static List<String> innersOf(Document document) {
        return segmentsOf(document).stream().map(Segment::sourceInner).toList();
    }

    private static String textOf(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Covers: FR-DOC-MD-1 — headings, paragraphs and list items are distinguished by kind.
    @Test
    void read_headingsParagraphsAndListItems_areDistinguishedByKind() {
        final List<Segment> segments = segmentsOf(openPrimary());

        assertThat(kindOf(segments, "Chapter One")).isEqualTo(SegmentKind.HEADING);
        assertThat(kindOf(segments, "Some _emphasis_ here and a [ref][r].")).isEqualTo(SegmentKind.PARAGRAPH);
        assertThat(kindOf(segments, "item one")).isEqualTo(SegmentKind.LIST_ITEM);
        assertThat(kindOf(segments, "Left")).isEqualTo(SegmentKind.TABLE_CELL);
        assertThat(kindOf(segments, "Quoted prose.")).isEqualTo(SegmentKind.PARAGRAPH);
    }

    // Covers: FR-DOC-MD-1 — a list item containing a fenced code block yields segments only for its prose, and no
    // segment's extent includes the fence.
    @Test
    void read_listItemContainingAFence_yieldsSegmentsOnlyForItsProse() {
        assertThat(innersOf(openPrimary())).contains("item one", "more prose");
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("echo hi"));
    }

    // Covers: FR-DOC-MD-1 — a table's six cells are segments and neither the table nor a row is.
    @Test
    void read_table_yieldsOneSegmentPerCellAndNoneForTheTable() {
        assertThat(segmentsOf(openPrimary()))
                .filteredOn(s -> s.kind() == SegmentKind.TABLE_CELL)
                .extracting(Segment::sourceInner)
                .containsExactly("Left", "Center", "Right", "a1", "b1", "c1");
    }

    // Covers: FR-DOC-MD-2 — Markdown-looking content inside a fence yields no segment.
    @Test
    void read_markdownLookingContentInsideAFence_yieldsNoSegment() {
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("not a heading"));
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("int x = 1;"));
    }

    // Covers: FR-DOC-MD-2 — an indented code block yields no segment either.
    @Test
    void read_indentedCodeBlock_yieldsNoSegment() {
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("indented code block"));
    }

    // Covers: EC-MD-2 — a raw HTML block yields no segment.
    @Test
    void read_rawHtmlBlock_yieldsNoSegment() {
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("class=\"note\""));
    }

    // Covers: FR-DOC-MD-3 — frontmatter yields no segment for any key or value it contains.
    @Test
    void read_frontmatter_yieldsNoSegmentForItsKeysOrValues() {
        assertThat(innersOf(openPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("title:"));
        assertThat(innersOf(openPrimary())).doesNotContain("The Book");
    }

    // Covers: FR-IMPORT-07 — the frontmatter's title and lang reach the metadata map and declaredLang.
    @Test
    void read_frontmatter_populatesDeclaredLangAndTitle() {
        final Document document = openPrimary();

        assertThat(document.declaredLang()).isEqualTo("en");
        assertThat(document.metadata()).containsEntry("title", "The Book");
    }

    // Covers: EC-MD-3 — a `---` line later in the file is a thematic break, not frontmatter, so the paragraphs
    // around it are ordinary prose.
    @Test
    void read_thematicBreakLaterInTheFile_isNotTreatedAsFrontmatter() {
        final Document document = open(MarkdownFixtures.NO_FRONTMATTER, "plain.md");

        assertThat(document.declaredLang()).isNull();
        assertThat(innersOf(document)).contains("Prose one.", "Prose two.");
    }

    // Covers: FR-DOC-MD-1 — the unit's id, href and media type identify a single-unit format.
    @Test
    void read_markdownFile_yieldsOneUnitNamedForTheFile() {
        final Unit unit = openPrimary().units().get(0);

        assertThat(unit.id()).isEqualTo("chapter.md");
        assertThat(unit.href()).isEqualTo("chapter.md");
        assertThat(unit.mediaType()).isEqualTo("text/markdown");
        assertThat(openPrimary().format()).isEqualTo(BookFormat.MARKDOWN);
    }

    // Covers: FR-DOC-MD-2 — a document that is entirely one fenced block yields zero segments and still
    // reassembles.
    @Test
    void read_documentThatIsEntirelyOneFence_yieldsNoSegmentsAndStillRoundTrips() {
        final Document document = open(MarkdownFixtures.ONLY_A_FENCE, "code.md");

        assertThat(segmentsOf(document)).isEmpty();
        assertThat(textOf(writeOut(document, "uk"))).isEqualTo(MarkdownFixtures.ONLY_A_FENCE);
    }

    // Covers: FR-DOC-MD-4 — WHEN a Markdown file is reassembled with no segment carrying target text, THEN the
    // output bytes are identical to the source's.
    @Test
    void write_zeroEditRoundTrip_isByteIdentical() {
        final Document document = openPrimary();

        final Path output = writeOut(document, "uk");

        assertThat(textOf(output)).isEqualTo(MarkdownFixtures.PRIMARY);
    }

    // Covers: FR-DOC-MD-4 — a file whose last byte is not a newline still has no trailing newline afterwards.
    @Test
    void write_fileWithoutATrailingNewline_stillHasNone() {
        assertThat(textOf(writeOut(openPrimary(), "uk"))).doesNotEndWith("\n");
    }

    // Covers: FR-DOC-MD-4 — emphasis spelling outside a translated span is untouched, because nothing outside a
    // replaced span is ever re-rendered.
    @Test
    void write_translatingOneParagraph_leavesAnotherParagraphsEmphasisSpellingAlone() {
        final Document document = withTarget(openPrimary(), "Quoted prose.", "Цитата.");

        final String output = textOf(writeOut(document, "uk"));

        assertThat(output).contains("Some _emphasis_ here and a [ref][r].");
        assertThat(output).contains("Цитата.");
    }

    // Covers: FR-DOC-MD-4 — WHEN a paragraph ending with two trailing spaces followed by a newline is written
    // back, THEN the output paragraph still ends with two trailing spaces followed by a newline, because the
    // replaced range is trimmed of the whitespace that carries the break.
    @Test
    void write_trailingHardBreak_survivesTranslationOfItsOwnParagraph() {
        final Document document = withTarget(
                openPrimary(), "A paragraph whose own last line ends", "Абзац, останній рядок якого закінчується.");

        final String output = textOf(writeOut(document, "uk"));

        assertThat(output).contains("Абзац, останній рядок якого закінчується.  \n");
    }

    // Covers: FR-DOC-MD-4 — an interior hard line break survives a zero-edit round trip untouched.
    @Test
    void write_zeroEditRoundTrip_preservesAnInteriorHardLineBreak() {
        assertThat(textOf(writeOut(openPrimary(), "uk"))).contains("hard break  \nand continuing");
    }

    // Covers: FR-DOC-MD-2 — the fenced block keeps its info string and content after a round trip.
    @Test
    void write_zeroEditRoundTrip_keepsTheFenceInfoStringAndContent() {
        final String output = textOf(writeOut(openPrimary(), "uk"));

        assertThat(output).contains("```java\nint x = 1;");
        assertThat(output).contains("```bash\n  echo hi");
    }

    // Covers: FR-DOC-07 — WHERE the exported format has no language field, the content is left unchanged rather
    // than gaining an invented one.
    @Test
    void write_targetLanguage_addsNoFrontmatterBlockAndNoLangKey() {
        final Document document = open(MarkdownFixtures.NO_FRONTMATTER, "plain.md");

        final String output = textOf(writeOut(document, "uk"));

        assertThat(output).isEqualTo(MarkdownFixtures.NO_FRONTMATTER);
        assertThat(output).doesNotContain("lang:");
    }

    private static SegmentKind kindOf(List<Segment> segments, String sourceInner) {
        return segments.stream()
                .filter(s -> sourceInner.equals(s.sourceInner()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no segment with sourceInner " + sourceInner))
                .kind();
    }

    /** Rebuilds {@code document} with the segment whose source starts with {@code sourcePrefix} translated. */
    private static Document withTarget(Document document, String sourcePrefix, String targetInner) {
        final Unit unit = document.units().get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        final int index = indexOfPrefix(segments, sourcePrefix);
        segments.set(index, withTargetInner(segments.get(index), targetInner));
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                List.of(new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments)));
    }

    private static int indexOfPrefix(List<Segment> segments, String sourcePrefix) {
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).sourceInner().startsWith(sourcePrefix)) {
                return i;
            }
        }
        throw new AssertionError("no segment starting with " + sourcePrefix);
    }

    private static Segment withTargetInner(Segment segment, String targetInner) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                targetInner,
                segment.status(),
                segment.confidence());
    }
}
