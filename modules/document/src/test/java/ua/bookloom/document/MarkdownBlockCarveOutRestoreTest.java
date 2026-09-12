package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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

/**
 * The block carve-out for a heading or a table cell, and the containment conditions that bound it — the second
 * audit's findings against the Markdown restore path.
 *
 * <p>Two separate defects meet here, and each test names which. The <strong>escape</strong> half: round one added
 * an ASCII-punctuation guard to one of four escaper branches, so a setext heading — whose marker is the underline
 * on the <em>next</em> line — took a backslash before the first letter of the content, failed to neutralise
 * anything, and repeated on every one of the twenty escape rounds, putting twenty literal backslashes ahead of a
 * translated heading in 2,330 of 35,290 accepted restores. The <strong>containment</strong> half: the carve-out
 * that lets a numbered heading be translated at all was vacuous rather than merely relaxed, because the multiset
 * already disregards text nodes — so a heading target holding a blank line reduced to {@code [Paragraph,
 * Paragraph]} against {@code [Paragraph]}, both sides emptied, and the target was accepted verbatim and written to
 * disk as a heading <em>plus</em> a whole new block.
 *
 * <p>{@link MarkdownEscapeRestoreTest} and {@link MarkdownStructureRestoreTest} own the first round's cases; this
 * class is deliberately separate rather than appended to either, because both already sit close to Checkstyle's
 * 400-line file gate and because these cases are one coherent story about one carve-out.
 */
class MarkdownBlockCarveOutRestoreTest {

    @TempDir
    private Path tempDir;

    // IF the character that would form the model-introduced construct is neither ASCII
    // punctuation nor a hard line break spelled as trailing spaces, THEN the system SHALL leave it as written and
    // allow the structure comparison to report the difference.
    // A setext heading is that case at its sharpest: its marker is the "---" underline on the SECOND line, so the
    // position an escape would reach is the first letter of the first line — a letter, not punctuation. Writing a
    // backslash there neutralises nothing, so the escaper re-ran and wrote another, twenty times over.
    @Test
    void unmask_headingTargetThatWouldParseAsASetextHeading_returnsValidationErrorAndWritesNoBackslash() {
        final Segment segment = segmentOfKind(SegmentKind.HEADING, "Title");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Заголовок\n---\nx");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data())
                .as("nothing is restored, so no backslash can reach the book")
                .isNull();
    }

    // IF the character that would form the model-introduced construct is neither ASCII
    // punctuation nor a hard line break spelled as trailing spaces, THEN the system SHALL leave it as written and
    // allow the structure comparison to report the difference.
    // The same target under a PARAGRAPH segment: the escape position is a letter there too, so the outcome must be
    // identical. Pairing the two kinds is what proves the fix moved the guard to the single point where a group
    // becomes an insertion, rather than adding a fifth kind-specific special case.
    @Test
    void unmask_paragraphTargetThatWouldParseAsASetextHeading_returnsValidationErrorAndWritesNoBackslash() {
        final Segment segment = segmentOfKind(SegmentKind.PARAGRAPH, "Title");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Заголовок\n---\nx");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data()).isNull();
    }

    // WHERE the segment's content is inline content owned by a block marker the skeleton
    // holds, the system SHALL NOT neutralise a block construct type the structure comparison disregards for that
    // kind — so a heading's ordered-list marker is left exactly as the model wrote it.
    @Test
    void unmask_headingTargetBeginningWithAnOrderedListMarker_restoresWithNoBackslash() {
        final Segment segment = segmentOfKind(SegmentKind.HEADING, "Alpha beta");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "1. Альфа бета");

        assertThat(result.data()).isEqualTo("1. Альфа бета");
        assertThat(Objects.requireNonNull(result.data(), "data")).doesNotContain("\\");
    }

    // WHERE the segment's content is owned by a block marker the skeleton holds, IF the
    // restored text carries more line terminators than the segment's source text, THEN the system SHALL treat the
    // segment as a structure mismatch.
    // Measured before the containment test existed: this exact target was accepted verbatim and written to disk as
    // a heading plus a new paragraph, turning two segments into three.
    @Test
    void unmask_headingTargetGainingASecondBlock_returnsValidationError() {
        final Segment segment = segmentOfKind(SegmentKind.HEADING, "Title");

        final Result<String> result =
                newService().unmask(BookFormat.MARKDOWN, segment, "Заголовок\n\nsecond paragraph");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // IF the two multisets differ, THEN the system SHALL return a failed result carrying
    // ErrorCode.validation, and SHALL NOT attempt a repair.
    // The carve-out is scoped to HEADING and TABLE_CELL: a paragraph keeps the full comparison, so the same target
    // that the heading case above rejects on containment must be rejected here on the multiset alone. Without this
    // pairing, a carve-out widened to every kind would still pass the test above and silently gain blocks anywhere.
    @Test
    void unmask_paragraphTargetGainingASecondBlock_returnsValidationError() {
        final Segment segment = segmentOfKind(SegmentKind.PARAGRAPH, "Title");

        final Result<String> result =
                newService().unmask(BookFormat.MARKDOWN, segment, "Заголовок\n\nsecond paragraph");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // WHERE the segment is a table cell, IF the restored text carries more unescaped `|`
    // characters than the segment's source text, THEN the system SHALL treat the segment as a structure mismatch.
    // This retires decision debt D10: a pipe written into a translated cell was measured to collapse a four-cell
    // row into a single paragraph, destroying the table.
    @Test
    void unmask_tableCellTargetAddingAnUnescapedPipe_returnsValidationError() {
        final Segment segment = segmentOfKind(SegmentKind.TABLE_CELL, "cell");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Комірка | друга");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // WHERE the segment is a table cell, IF the restored text carries more unescaped `|`
    // characters than the segment's source text, THEN the system SHALL treat the segment as a structure mismatch.
    // The condition counts UNESCAPED pipes, so a model that escapes its own pipe is writing a literal character
    // into one cell and is accepted. A condition that forbade the character outright would reject this.
    @Test
    void unmask_tableCellTargetEscapingItsOwnPipe_restoresUnchanged() {
        final Segment segment = segmentOfKind(SegmentKind.TABLE_CELL, "cell");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Комірка \\| друга");

        assertThat(result.data()).isEqualTo("Комірка \\| друга");
    }

    // WHERE the segment is a table cell, IF the restored text carries more unescaped `|`
    // characters than the segment's source text, THEN the system SHALL treat the segment as a structure mismatch.
    // Compared against the source rather than forbidden outright: a cell whose own source already spells a pipe is
    // unaffected, so a book that legitimately holds one is still translatable.
    @Test
    void unmask_tableCellWhoseSourceAlreadyHoldsAPipe_acceptsATargetHoldingOne() {
        final Segment segment = segmentOfKind(SegmentKind.TABLE_CELL, "a | b");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Комірка | друга");

        assertThat(result.data()).isEqualTo("Комірка | друга");
    }

    // WHERE the segment's content is owned by a block marker the skeleton holds, IF the
    // restored text carries more line terminators than the segment's source text, THEN the system SHALL treat the
    // segment as a structure mismatch — a newline inside a cell ends its row exactly as a heading ends at its line.
    @Test
    void unmask_tableCellTargetAddingALineTerminator_returnsValidationError() {
        final Segment segment = segmentOfKind(SegmentKind.TABLE_CELL, "cell");

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "Комірка\nдруга");

        assertThat(result.isErr()).isTrue();
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
    }

    // WHERE the segment's content is inline content owned by a block marker the skeleton
    // holds — a heading or a table cell — the comparison SHALL disregard block construct types on both sides.
    // THE REGRESSION GUARD THE CONTAINMENT TEST MUST NOT BREAK. A heading's segment is the text after its "#"
    // marker, so "1. Alpha beta gamma" parsed standalone is an ordered list the document never contained, and any
    // translation that moves the numeral off position zero "loses" it. Measured against the 213-book corpus, 54 of
    // 2,065 Markdown segments across four books are in this position — 43 headings and 11 table cells — so a
    // containment condition that also re-tightened the carve-out would make those books untranslatable. The
    // fixture is a real parsed document rather than a hand-built segment, so the heading's marker really does live
    // in the skeleton and the segment really is only its inner text.
    @Test
    void unmask_numberedHeadingOfARealDocumentWithReorderedWords_restoresUnchanged() {
        final DocumentService service = newService();
        final Segment heading = headingSegmentOf(service, "## 1. Alpha beta gamma\n\nProse here.\n");

        final Result<String> result = service.unmask(BookFormat.MARKDOWN, heading, "gamma beta Alpha 1.");

        assertThat(heading.sourceInner()).isEqualTo("1. Alpha beta gamma");
        assertThat(result.data()).isEqualTo("gamma beta Alpha 1.");
    }

    // WHERE the segment's content is inline content owned by a block marker the skeleton
    // holds — a heading or a table cell — the comparison SHALL disregard block construct types on both sides.
    // The other block shapes a heading's inner text can take standalone, each of which the corpus carries and each
    // of which must survive a reordering translation for the same reason the numbered heading above does.
    @ParameterizedTest
    @ValueSource(strings = {"- Alpha beta", "> Alpha beta", "# Alpha beta"})
    void unmask_headingWhoseInnerTextIsAnotherBlockConstruct_acceptsAReorderingTranslation(String innerText) {
        final Segment segment = segmentOfKind(SegmentKind.HEADING, innerText);

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "бета альфа");

        assertThat(result.data()).isEqualTo("бета альфа");
    }

    /** The one HEADING segment of a Markdown file written into the temp directory and opened through the port. */
    private Segment headingSegmentOf(DocumentService service, String markdown) {
        final Path file = write(tempDir.resolve("heading.md"), markdown);
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        final List<Segment> headings = Objects.requireNonNull(opened.data(), "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.kind() == SegmentKind.HEADING)
                .toList();
        assertThat(headings).as("heading segments").hasSize(1);
        return headings.get(0);
    }

    private static Path write(Path file, String content) {
        try {
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A segment of {@code kind} carrying no placeholders, whose {@code sourceInner} equals its masked form. */
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

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
