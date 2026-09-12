package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.fixture.MarkdownFixtures;

/**
 * {@link DocumentService#unmask}'s Markdown-only structure check, run after {@link MarkdownEscapeRestoreTest}'s
 * escaping settles what the model wrote (task group 8.5) — the multiset comparison's matching and differing
 * outcomes, driven through the port against hand-built Markdown-kind segments whose {@code sourceInner} is real
 * Markdown source, plus a sweep of every segment the module's own Markdown fixtures produce.
 */
class MarkdownStructureRestoreTest {

    @TempDir
    private Path tempDir;

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure.
    @Test
    void unmask_spaceIntroducedInsideAnEmphasisPair_returnsValidationError() {
        final Segment segment = emphasisSegment("the *old* door", "the ⟦g0⟧old⟦g1⟧ door");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "⟦g0⟧ старі ⟦g1⟧ двері");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure.
    @Test
    void unmask_translationMovingTheEmphasisToTheFront_passes() {
        final Segment segment = emphasisSegment("the *old* door", "the ⟦g0⟧old⟦g1⟧ door");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "⟦g0⟧старі⟦g1⟧ двері");

        assertThat(result.data()).isEqualTo("*старі* двері");
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure.
    @Test
    void unmask_restoredSegmentThatBecomesABulletList_returnsValidationError() {
        final Segment segment = emphasisSegment("*old* door", "⟦g0⟧old⟦g1⟧ door");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "⟦g0⟧ старі⟦g1⟧ двері");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure.
    @Test
    void unmask_twoSourceLinesRenderedAsOne_passes() {
        final Segment segment = segment("line one\nline two", "line one\nline two", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "рядок один рядок два");

        assertThat(result.data()).isEqualTo("рядок один рядок два");
    }

    // WHEN the model introduces no more constructs than the neutralisation bound allows,
    // THEN every one of them is escaped and the operation succeeds.
    // This is the other half of the bound: without it the twenty-five-pair case below would still pass if the
    // bound were five. The pair confines it to at least twenty and fewer than twenty-five — not to twenty exactly
    // — which is the useful guarantee, since these tests should not break when the constant is retuned inside a
    // range that still neutralises every construct a real translation produces.
    @Test
    void unmask_exactlyTheBoundOfModelIntroducedConstructs_escapesThemAllAndSucceeds() {
        final Segment segment = segment("plain words", "plain words", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, independentEmphases(20));

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isEqualTo(independentEmphases(20).replace("*", "\\*"));
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure. Twenty-five independent model-introduced emphasis pairs need twenty-five
    // rounds to escape in full; MarkdownEscaper's MAX_ESCAPE_ROUNDS bound stops it at twenty, so five constructs
    // reach the structure check unescaped and the mismatch is reported rather than silently repaired.
    @Test
    void unmask_moreThanTwentyIndependentModelIntroducedConstructs_returnsValidationError() {
        final Segment segment = segment("plain words", "plain words", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, twentyFiveEmphases());

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure.
    @ParameterizedTest
    @MethodSource("markdownFixtures")
    void unmask_everyFixtureSegmentGivenItsOwnMaskedFormAsTarget_passesTheStructureCheck(String markdown) {
        final DocumentService service = newService();
        final List<Segment> segments = segmentsOf(service, markdown);

        assertThat(segments)
                .allSatisfy(segment -> assertThat(service.unmask(BookFormat.MARKDOWN, segment, segment.masked())
                                .isOk())
                        .isTrue());
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure. A heading's content is segmented as its own inner text, excluding the
    // "## " marker — so a heading reading "1. What BMAD is" has an inner text that, parsed standalone, is itself
    // an ordered-list item. An identity restore of that inner text must not be escaped into a different construct
    // just because no placeholder fragment covers its list marker.
    @Test
    void unmask_headingContentThatParsesStandaloneAsAnOrderedListItem_passesUnchanged() {
        final Segment segment = segment("1. What BMAD is", "1. What BMAD is", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, segment.masked());

        assertThat(result.data()).isEqualTo("1. What BMAD is");
    }

    // IF a restored Markdown segment's construct multiset differs from its source's, THEN
    // the result is a validation failure. A heading's inner text can take other shapes that parse standalone as a
    // different block construct entirely — a bullet item, a block quote, or another heading's marker — and each
    // must still pass an identity restore unchanged, the same way the ordered-list-item shape above does.
    @ParameterizedTest
    @ValueSource(strings = {"- item", "> item", "# item"})
    void unmask_headingContentThatParsesStandaloneAsAnotherBlockConstruct_passesUnchanged(String text) {
        final Segment segment = segment(text, text, Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, segment.masked());

        assertThat(result.data()).isEqualTo(text);
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. The source's own
    // emphasis carries no placeholder fragment at all — it is literal, untranslated punctuation — yet it must
    // survive unescaped because the source already owns one instance of it; only the model's separately added
    // code span, a construct type the source holds none of, is genuinely in excess and gets escaped.
    @Test
    void unmask_modelAddsAConstructAlongsideASourceOwnedEmphasis_escapesOnlyTheAddedConstruct() {
        final Segment segment = segment("*legit* text", "*legit* text", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "*legit* text `code`");

        assertThat(result.data()).isEqualTo("*legit* text \\`code`");
    }

    // WHERE the segment's content is inline content owned by a block marker the skeleton
    // holds — a heading or a table cell — the comparison SHALL disregard block construct types on both sides. A
    // heading segment's inner text parses standalone as an OrderedList/ListItem/Paragraph; a translation that does
    // not keep the numeral at position zero must not be rejected for losing block structure the heading never had.
    @Test
    void unmask_numberedHeadingWithReorderedWords_passesTheStructureCheck() {
        final Segment segment = segmentOfKind(SegmentKind.HEADING, "1. Alpha beta gamma");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "gamma beta Alpha 1.");

        assertThat(result.data()).isEqualTo("gamma beta Alpha 1.");
    }

    // WHERE the segment's content is inline content owned by a block marker the skeleton
    // holds — a heading or a table cell — the comparison SHALL disregard block construct types on both sides. A
    // table cell's inner text can parse standalone as an OrderedList/ListItem/Paragraph the same way a heading's
    // does, and must not be rejected for losing block structure the cell never had either.
    @Test
    void unmask_numberedTableCellWithReorderedWords_passesTheStructureCheck() {
        final Segment segment = segmentOfKind(SegmentKind.TABLE_CELL, "1. Alpha");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Alpha 1.");

        assertThat(result.data()).isEqualTo("Alpha 1.");
    }

    // IF the two multisets differ, THEN the system SHALL return a failed result carrying
    // ErrorCode.validation, and SHALL NOT attempt a repair. An indented code block's marker position is a space,
    // not ASCII punctuation, so no backslash escape exists for it — the construct must survive unescaped and be
    // reported as a mismatch rather than have a stray, visible backslash written into the book.
    @Test
    void unmask_modelIntroducedIndentedCodeBlock_returnsValidationErrorInsteadOfAStrayBackslash() {
        final Segment segment = segment("Just prose here", "Just prose here", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "    відступ");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    private static Stream<String> markdownFixtures() {
        return Stream.of(
                MarkdownFixtures.PRIMARY,
                MarkdownFixtures.ONLY_A_FENCE,
                MarkdownFixtures.NO_FRONTMATTER,
                MarkdownFixtures.MARKUP_DENSE);
    }

    private List<Segment> segmentsOf(DocumentService service, String markdown) {
        final Path file = MarkdownFixtures.write(tempDir.resolve("fixture.md"), markdown);
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk()).isTrue();
        return Objects.requireNonNull(opened.data(), "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    /** Twenty-five independent {@code *x*} emphasis pairs, one per letter {@code a} through {@code y}. */
    private static String twentyFiveEmphases() {
        return independentEmphases(25);
    }

    /**
     * {@code count} independent {@code *x*} emphasis pairs, one per letter from {@code a}. Each pair is its own
     * construct, so the count is exactly how many neutralisation rounds the escaper needs.
     */
    private static String independentEmphases(int count) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append('*').append((char) ('a' + index)).append('*');
        }
        return builder.toString();
    }

    /** A Markdown-kind segment whose one emphasis pair is keyed {@code g0}/{@code g1}, both mapped to {@code *}. */
    private static Segment emphasisSegment(String sourceInner, String masked) {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "*");
        placeholders.put("g1", "*");
        return segment(sourceInner, masked, placeholders);
    }

    /** A segment of {@code kind} carrying no placeholders, whose {@code sourceInner} equals {@code masked}. */
    private static Segment segmentOfKind(SegmentKind kind, String text) {
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                kind,
                text,
                text,
                Map.of(),
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static Segment segment(String sourceInner, String masked, Map<String, String> placeholders) {
        return new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                sourceInner,
                masked,
                placeholders,
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
