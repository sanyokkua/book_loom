package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/**
 * {@link DocumentService#unmask}'s Markdown-only escape of a construct the model's target introduces but the
 * segment's own source did not contain (task group 8.4) — {@link MarkdownEscapeRestoreTest} owns every case where
 * escaping alone settles the restore; {@link MarkdownStructureRestoreTest} owns the multiset comparison that runs
 * after escaping and the cases where escaping is not enough. Driven through the port against hand-built
 * Markdown-kind segments, whose {@code sourceInner} is real Markdown source, not a placeholder-bearing string —
 * the structure check parses it standalone.
 */
class MarkdownEscapeRestoreTest {

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it.
    @Test
    void unmask_modelIntroducedEmphasis_escapesBothDelimiters() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "звичайні *слова*");

        assertThat(result.data()).isEqualTo("звичайні \\*слова\\*");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it.
    @Test
    void unmask_asteriskFormingNoConstruct_isLeftUnescaped() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "5 * 3 дорівнює 15");

        assertThat(result.data()).isEqualTo("5 * 3 дорівнює 15");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it.
    @Test
    void unmask_translationBeginningWithAYearAndAPeriod_escapesTheOrderedListMarker() {
        final Segment segment = segment("In 1985 he opened the door.", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "1985. Він відчинив двері.");

        assertThat(result.data()).isEqualTo("1985\\. Він відчинив двері.");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it.
    @Test
    void unmask_translationBeginningWithADash_escapesTheBulletMarker() {
        final Segment segment = segment("As the author said", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "- як казав автор");

        assertThat(result.data()).isEqualTo("\\- як казав автор");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it.
    @Test
    void unmask_restoredPlaceholderFragment_isNotEscaped() {
        final Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("g0", "*");
        placeholders.put("g1", "*");
        final Segment segment = new Segment(
                "seg-1",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                "the *old* door",
                "the ⟦g0⟧old⟦g1⟧ door",
                placeholders,
                "deadbeef",
                null,
                null,
                new NodeAnchor(List.of(0), 0),
                null,
                SegmentStatus.PENDING,
                0.0);

        final Result<String> result =
                newService().unmask(BookFormat.MARKDOWN, segment, "⟦g0⟧старі⟦g1⟧ \\*нові\\* двері");

        assertThat(result.data()).isEqualTo("*старі* \\*нові\\* двері");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A paired construct
    // with no child (an empty link label) contributes only its own start, not a closing position, to the group
    // MarkdownEscaper escapes.
    @Test
    void unmask_modelIntroducedEmptyLink_escapesOnlyItsOpeningBracket() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "перевір []() тут");

        assertThat(result.data()).isEqualTo("перевір \\[]() тут");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A heading is a
    // marker-led block construct: MarkdownEscaper escapes its marker's position, not a paired delimiter.
    @Test
    void unmask_modelIntroducedHeadingMarker_isEscaped() {
        final Segment segment = segment("Not a heading", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "# Це не заголовок");

        assertThat(result.data()).isEqualTo("\\# Це не заголовок");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A block quote is
    // another marker-led block construct MarkdownEscaper escapes at its own marker.
    @Test
    void unmask_modelIntroducedBlockQuoteMarker_isEscaped() {
        final Segment segment = segment("quote", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "> Цитата");

        assertThat(result.data()).isEqualTo("\\> Цитата");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A thematic break is
    // the third marker-led block construct on MarkdownEscaper's branch shared with heading and block quote.
    @Test
    void unmask_modelIntroducedThematicBreak_isEscaped() {
        final Segment segment = segment("separator", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "---");

        assertThat(result.data()).isEqualTo("\\---");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A code span falls
    // through MarkdownEscaper's generic "any other construct with a span" branch, contributing only its own start.
    @Test
    void unmask_modelIntroducedCodeSpan_escapesOnlyItsOpeningBacktick() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "проба `код` тут");

        assertThat(result.data()).isEqualTo("проба \\`код` тут");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A strong emphasis is
    // the second of MarkdownEscaper's three paired-construct node types, sharing the branch emphasis and links use.
    @Test
    void unmask_modelIntroducedStrongEmphasis_escapesBothDelimiterPairs() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "це **жирне** слово");

        assertThat(result.data()).isEqualTo("це \\*\\*жирне\\*\\* слово");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A bullet item with no
    // child content takes the list-item branch's childless path, where the marker run's own end stands in for a
    // first child's start.
    @Test
    void unmask_modelIntroducedChildlessListItem_escapesItsMarker() {
        final Segment segment = segment("dash", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "-");

        assertThat(result.data()).isEqualTo("\\-");
    }

    // IF a segment's format is Markdown and the model's text would parse as a construct the
    // source did not contain, THEN the system SHALL escape the characters that would form it. A soft line break in
    // the model's own text takes appendGroup's early-return branch for Text and SoftLineBreak nodes, contributing
    // no group and needing no escape.
    @Test
    void unmask_targetWithAnEmbeddedSoftLineBreak_isLeftUnescaped() {
        final Result<String> result =
                newService().unmask(BookFormat.MARKDOWN, plainSegment(), "перше слово\nдруге слово");

        assertThat(result.data()).isEqualTo("перше слово\nдруге слово");
    }

    // IF a segment's format is Markdown and the escaped text still does not parse to the
    // source's construct multiset, THEN the result is a validation failure, never an internal one. A link whose
    // label ends in a hard line break has a span-less last child (the two-trailing-spaces spelling reports no
    // span), so the closing delimiter is derived from the last child that DOES carry a span — and for this shape
    // that derivation lands on the line feed, not on the "]". This test asserted "\\[foo\n](u)" until the
    // escapability guard moved to the single point where a group becomes an insertion: reaching that result
    // required inserting a backslash before the line feed (making a hard line break) and deleting it again a round
    // later, i.e. writing a backslash before a non-punctuation character, which is the very defect that guard
    // exists to stop. A group whose derived positions are not all escapable is now not acted on at all, so the
    // link survives and is reported honestly. The obligation this test was written for — settle rather than crash
    // with ErrorCode.internal — still holds.
    @Test
    void unmask_modelIntroducedLinkWhoseLabelEndsInAHardLineBreak_reportsValidationInsteadOfCrashing() {
        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, plainSegment(), "[foo  \n](u)");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
    }

    // WHERE the construct is a hard line break spelled as trailing spaces, the system SHALL
    // neutralise it by deleting those spaces, and SHALL leave untouched any space that came from a restored
    // fragment.
    // Deletion rather than a backslash because there is no backslash spelling that removes a hard break — a
    // trailing backslash before a line feed IS the other hard-break spelling.
    @Test
    void unmask_modelIntroducedHardLineBreak_deletesTheTrailingSpacesInsteadOfFailing() {
        final Segment segment = segment("alpha beta\ngamma delta", Map.of());

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "alpha beta  \ngamma delta");

        assertThat(result.data()).isEqualTo("alpha beta\ngamma delta");
    }

    private static Segment plainSegment() {
        return segment("plain words", Map.of());
    }

    /** A Markdown-kind segment whose {@code sourceInner} equals {@code masked}, carrying no placeholders. */
    private static Segment segment(String text, Map<String, String> placeholders) {
        return segment(text, text, placeholders);
    }

    /** A segment whose source text and masked form differ — the shape a real masked segment has. */
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

    private static DocumentService newService() {
        return DocumentServices.newService();
    }

    // WHERE the construct is a hard line break spelled as trailing spaces, the system SHALL
    // neutralise it by deleting those spaces, and SHALL leave untouched any space that came from a restored
    // fragment.
    // The other half of the rule, which had no test: the source's OWN hard line break is masked as a token, so its
    // spaces arrive inside a restored fragment and must survive. Deleting them would silently drop a line break the
    // author wrote.
    @Test
    void unmask_hardLineBreakOwnedByTheSource_keepsItsTrailingSpaces() {
        final Map<String, String> fragments = new LinkedHashMap<>();
        fragments.put("g0", "  ");
        // sourceInner is the raw source; masked is the tokenised form. The source's own hard line break is
        // masked as g0, so its two spaces arrive back inside a restored fragment.
        final Segment segment = segment("alpha beta  \ngamma delta", "alpha beta⟦g0⟧\ngamma delta", fragments);

        final Result<String> result = newService().unmask(BookFormat.MARKDOWN, segment, "альфа бета⟦g0⟧\nгама дельта");

        assertThat(result.isOk())
                .withFailMessage("expected ok but got: %s", result.error())
                .isTrue();
        assertThat(result.data()).isEqualTo("альфа бета  \nгама дельта");
    }
}
