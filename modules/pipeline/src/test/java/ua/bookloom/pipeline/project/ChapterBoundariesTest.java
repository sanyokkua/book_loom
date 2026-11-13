package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/** Chapters are taken from the body structure by one rule, whatever the format (design D10). */
class ChapterBoundariesTest {

    private static final String SOURCE = "en";
    // 100 characters: 29 tokens in English, so twelve of them make a chapter of 348 tokens, over the 300 minimum.
    private static final String PARAGRAPH =
            "The boats waited by the shore while the river ran slowly past the old mill and the bridge beyond it.";
    private static final int PARAGRAPH_TOKENS = 29;
    private static final int CHAPTER_PARAGRAPHS = 12;

    private record Block(SegmentKind kind, String text) {}

    private static Block heading(final String text) {
        return new Block(SegmentKind.HEADING, text);
    }

    private static List<Block> paragraphs(final int count) {
        return Collections.nCopies(count, new Block(SegmentKind.PARAGRAPH, PARAGRAPH));
    }

    private static List<Block> blocks(final Stream<List<Block>> parts) {
        return parts.flatMap(List::stream).toList();
    }

    private static Unit unit(final String id, final int order, final UnitRole role, final List<Block> blocks) {
        final List<Segment> segments = IntStream.range(0, blocks.size())
                .mapToObj(position -> segment(id, position, blocks.get(position)))
                .toList();
        return new Unit(id, order, id + ".xhtml", "application/xhtml+xml", new SkeletonHandle(id), segments, role);
    }

    private static Segment segment(final String unitId, final int position, final Block block) {
        return new Segment(
                unitId + ":" + position,
                unitId,
                position,
                block.kind(),
                block.text(),
                block.text(),
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, block.text().length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static Document book(final BookFormat format, final Unit... units) {
        return new Document("invented", format, SOURCE, null, null, null, "hash", Map.of(), List.of(units));
    }

    private static StructureNode node(
            final String unitId, final int first, final int count, final StructureNode... children) {
        return new StructureNode("Heading", unitId, count, List.of(children), first);
    }

    private static StructureNode file(final String unitId, final int count, final boolean listed) {
        return new StructureNode("File", unitId, count, List.of(), null, listed);
    }

    @Test
    void paragraph_estimate_isTheFigureTheFixturesCountOn() {
        assertThat(TokenEstimator.estimate(PARAGRAPH, SOURCE)).isEqualTo(PARAGRAPH_TOKENS);
    }

    @Test
    void chapters_nestedMarkdownH1H2_subHeadingStaysInside() {
        // One lead paragraph, # One with a ## Scene inside it, then # Two.
        final Unit body = unit(
                "book.md",
                0,
                UnitRole.BODY,
                blocks(Stream.of(
                        paragraphs(1),
                        List.of(heading("One")),
                        paragraphs(6),
                        List.of(heading("Scene")),
                        paragraphs(6),
                        List.of(heading("Two")),
                        paragraphs(CHAPTER_PARAGRAPHS))));
        final List<StructureNode> structure = List.of(
                new StructureNode("", "book.md", 1, List.of(), 0, false),
                node("book.md", 1, 14, node("book.md", 8, 7)),
                node("book.md", 15, 13));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.MARKDOWN, body), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(
                        ChapterBoundaries.Chapter::key,
                        ChapterBoundaries.Chapter::number,
                        ChapterBoundaries.Chapter::firstSegment,
                        ChapterBoundaries.Chapter::segmentCount,
                        ChapterBoundaries.Chapter::pseudo)
                .containsExactly(tuple("book.md:0", 1, 0, 15, false), tuple("book.md:15", 2, 15, 13, false));
        assertThat(chapters.sameChapter("book.md:7", "book.md:9")).isTrue();
        assertThat(chapters.sameChapter("book.md:14", "book.md:15")).isFalse();
    }

    @Test
    void chapters_nestedFb2Sections_topLevelChapters() {
        final Unit main = unit(
                "book.fb2#0",
                0,
                UnitRole.BODY,
                blocks(Stream.of(
                        List.of(heading("First")),
                        paragraphs(6),
                        List.of(heading("First, part two")),
                        paragraphs(6),
                        List.of(heading("Second")),
                        paragraphs(CHAPTER_PARAGRAPHS))));
        final Unit notes = unit("book.fb2#1", 1, UnitRole.BACK_MATTER, paragraphs(CHAPTER_PARAGRAPHS));
        final List<StructureNode> structure = List.of(
                node("book.fb2#0", 0, 14, node("book.fb2#0", 1, 6), node("book.fb2#0", 7, 7)),
                node("book.fb2#0", 14, 13, node("book.fb2#0", 15, 12)),
                file("book.fb2#1", CHAPTER_PARAGRAPHS, true));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.FB2, main, notes), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(
                        ChapterBoundaries.Chapter::unitId,
                        ChapterBoundaries.Chapter::firstSegment,
                        ChapterBoundaries.Chapter::segmentCount)
                .containsExactly(tuple("book.fb2#0", 0, 14), tuple("book.fb2#0", 14, 13));
        assertThat(chapters.chapterOf("book.fb2#1:0")).isEmpty();
    }

    @Test
    void chapters_singleTopNode_isLookedThrough() {
        final Unit main = unit(
                "book.fb2#0",
                0,
                UnitRole.BODY,
                blocks(Stream.of(paragraphs(CHAPTER_PARAGRAPHS), paragraphs(CHAPTER_PARAGRAPHS))));
        final List<StructureNode> structure = List.of(node(
                "book.fb2#0",
                0,
                2 * CHAPTER_PARAGRAPHS,
                node("book.fb2#0", 0, CHAPTER_PARAGRAPHS),
                node("book.fb2#0", CHAPTER_PARAGRAPHS, CHAPTER_PARAGRAPHS)));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.FB2, main), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::key)
                .containsExactly("book.fb2#0:0", "book.fb2#0:12");
    }

    @Test
    void chapters_epubContinuationFile_joinsPreviousChapter() {
        final Unit cover = unit("cover", 0, UnitRole.FRONT_MATTER, paragraphs(1));
        final Unit first = unit("ch1", 1, UnitRole.BODY, paragraphs(6));
        final Unit continued = unit("ch1b", 2, UnitRole.BODY, paragraphs(6));
        final Unit second = unit("ch2", 3, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final List<StructureNode> structure =
                List.of(file("cover", 1, false), file("ch1", 6, true), file("ch1b", 6, false), file("ch2", 12, true));

        final ChapterBoundaries chapters =
                ChapterBoundaries.of(book(BookFormat.EPUB, cover, first, continued, second), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::key)
                .containsExactly("ch1:0", "ch2:0");
        assertThat(chapters.chapters().getFirst().segmentIds())
                .containsExactly(
                        "ch1:0", "ch1:1", "ch1:2", "ch1:3", "ch1:4", "ch1:5", "ch1b:0", "ch1b:1", "ch1b:2", "ch1b:3",
                        "ch1b:4", "ch1b:5");
        assertThat(chapters.chapters().getFirst().tokens()).isEqualTo(CHAPTER_PARAGRAPHS * PARAGRAPH_TOKENS);
        assertThat(chapters.chapterOf("cover:0")).isEmpty();
    }

    @Test
    void chapters_shortChapter_joinsNext() {
        final Unit part = unit("part1", 0, UnitRole.BODY, List.of(heading("Part One")));
        final Unit first = unit("c1", 1, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final Unit second = unit("c2", 2, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final List<StructureNode> structure = List.of(
                file("part1", 1, true), file("c1", CHAPTER_PARAGRAPHS, true), file("c2", CHAPTER_PARAGRAPHS, true));

        final ChapterBoundaries chapters =
                ChapterBoundaries.of(book(BookFormat.EPUB, part, first, second), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(
                        ChapterBoundaries.Chapter::key,
                        ChapterBoundaries.Chapter::number,
                        ChapterBoundaries.Chapter::segmentCount)
                .containsExactly(tuple("part1:0", 1, 13), tuple("c2:0", 2, 12));
    }

    @Test
    void chapters_shortLastChapter_joinsPrevious() {
        final Unit first = unit("c1", 0, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final Unit last = unit("c2", 1, UnitRole.BODY, paragraphs(3));
        final List<StructureNode> structure = List.of(file("c1", CHAPTER_PARAGRAPHS, true), file("c2", 3, true));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.EPUB, first, last), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::key, ChapterBoundaries.Chapter::segmentCount)
                .containsExactly(tuple("c1:0", 15));
    }

    @Test
    void chapters_plainTxt_pseudoChaptersAtParagraphEnds() {
        // 103 paragraphs make 2,987 tokens; the 104th would pass 3,000, so a pseudo-chapter closes before it.
        final Unit text = unit("book.txt", 0, UnitRole.BODY, paragraphs(220));
        final List<StructureNode> structure = List.of(new StructureNode("", "book.txt", 220, List.of(), null, false));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.TXT, text), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(
                        ChapterBoundaries.Chapter::number,
                        ChapterBoundaries.Chapter::firstSegment,
                        ChapterBoundaries.Chapter::segmentCount,
                        ChapterBoundaries.Chapter::pseudo)
                .containsExactly(tuple(1, 0, 103, true), tuple(2, 103, 103, true), tuple(3, 206, 14, true));
    }

    @Test
    void chapters_pseudoCutAfterHeading_movesBeforeTheHeading() {
        // The 3,000-token cut would fall right after the heading at 103, so the heading opens the next chapter.
        final Unit text = unit(
                "book.txt",
                0,
                UnitRole.BODY,
                blocks(Stream.of(paragraphs(103), List.of(heading("Two")), paragraphs(20))));
        final List<StructureNode> structure = List.of(new StructureNode("", "book.txt", 124, List.of(), null, false));

        final ChapterBoundaries chapters = ChapterBoundaries.of(book(BookFormat.TXT, text), structure, SOURCE);

        assertThat(chapters.chapters())
                .extracting(ChapterBoundaries.Chapter::firstSegment, ChapterBoundaries.Chapter::segmentCount)
                .containsExactly(tuple(0, 103), tuple(103, 21));
    }

    @Test
    void split_segmentsOfTwoChapters_cutAtTheBoundary() {
        final Unit first = unit("c1", 0, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final Unit second = unit("c2", 1, UnitRole.BODY, paragraphs(CHAPTER_PARAGRAPHS));
        final Document document = book(BookFormat.MARKDOWN, first, second);
        final ChapterBoundaries chapters = ChapterBoundaries.of(
                document, List.of(file("c1", CHAPTER_PARAGRAPHS, true), file("c2", CHAPTER_PARAGRAPHS, true)), SOURCE);
        final List<Segment> run = List.of(
                first.segments().get(10),
                first.segments().get(11),
                second.segments().get(0));

        assertThat(chapters.split(run))
                .extracting(part -> part.stream().map(Segment::id).toList())
                .containsExactly(List.of("c1:10", "c1:11"), List.of("c2:0"));
    }
}
