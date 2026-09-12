package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.fixture.MarkdownFixtures;

/**
 * {@link MarkdownMasker}'s inline-construct masking, driven end to end through {@link MarkdownReader} — a real
 * file, the real parser, the real {@link MarkdownWalker} wiring — so the port is proven, not just the masker in
 * isolation. Each single-paragraph body below yields exactly one segment.
 *
 * <p>The final two cases are regression guards for the hard-line-break derivation {@link MarkdownMasker}'s own
 * class Javadoc documents as deliberately <em>not</em> matching the formula {@code tasks.md}/{@code design.md} D7
 * state ("that node's span end minus its literal length"): a backslash escape or a character reference ahead of a
 * two-trailing-spaces hard break makes the preceding {@code Text} node's literal length differ from its span
 * length, so the stated formula would slice the wrong range.
 */
class MarkdownMaskerPortTest {

    private static final String TWO_SPACES = "  ";

    @TempDir
    private Path tempDir;

    private final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();

    private Segment onlySegment(String markdownBody) {
        final Path file = MarkdownFixtures.write(tempDir.resolve("doc.md"), markdownBody);
        final Document document = new MarkdownReader(registry).read(file);
        final List<Segment> segments = document.units().get(0).segments();
        assertThat(segments).hasSize(1);
        return segments.get(0);
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_emphasis_isMaskedAsAPairedGroup() {
        final Segment segment = onlySegment("He opened the *old* door.");

        assertThat(segment.masked()).isEqualTo("He opened the ⟦g0⟧old⟦g1⟧ door.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_emphasisDelimiters_mapHoldsBothAsterisks() {
        final Segment segment = onlySegment("He opened the *old* door.");

        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "*", "g1", "*"));
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_linkWhoseDestinationRidesInItsClosingToken_labelStaysTranslatable() {
        final Segment segment = onlySegment("See [chapter two](ch2.md) now.");

        assertThat(segment.masked()).isEqualTo("See ⟦g0⟧chapter two⟦g1⟧ now.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_linkDestination_mapHoldsTheClosingToken() {
        final Segment segment = onlySegment("See [chapter two](ch2.md) now.");

        assertThat(segment.placeholders()).containsEntry("g1", "](ch2.md)");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_autolinkedUrl_isOneAtomicToken() {
        final Segment segment = onlySegment("See <https://x.org/a> now.");

        assertThat(segment.masked()).isEqualTo("See ⟦g0⟧ now.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_autolinkedEmailAddress_isOneAtomicToken() {
        final Segment segment = onlySegment("Write to <me@example.com> now.");

        assertThat(segment.masked()).isEqualTo("Write to ⟦g0⟧ now.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_selfReferentialLink_isOneAtomicToken() {
        final Segment segment = onlySegment("See [https://x.org](https://x.org) now.");

        assertThat(segment.masked()).isEqualTo("See ⟦g0⟧ now.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_codeSpan_isOneAtomicToken() {
        final Segment segment = onlySegment("Call `List.of()` first.");

        assertThat(segment.masked()).isEqualTo("Call ⟦g0⟧ first.");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_image_isOneAtomicToken() {
        final Segment segment = onlySegment("Before ![Figure 1](fig1.png) after");

        assertThat(segment.masked()).isEqualTo("Before ⟦g0⟧ after");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_inlineHtmlStartAndEndTags_areTwoSeparateAtomicTokens() {
        final Segment segment = onlySegment("a <span class=\"x\">b</span> c");

        assertThat(segment.masked()).isEqualTo("a ⟦g0⟧b⟦g1⟧ c");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_unpairedInlineHtmlTag_isStillOneAtomicToken() {
        final Segment segment = onlySegment("a <br> b");

        assertThat(segment.masked()).isEqualTo("a ⟦g0⟧ b");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_strongEmphasisNestedInEmphasis_producesWellFormedPairs() {
        final Segment segment = onlySegment("*a **b** c*");

        assertThat(segment.masked()).isEqualTo("⟦g0⟧a ⟦g1⟧b⟦g2⟧ c⟦g3⟧");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_emphasisSpanningALineBreak_isMaskedAcrossBothOfItsRanges() {
        final Segment segment = onlySegment("*bcd\nefg*");

        assertThat(segment.masked()).isEqualTo("⟦g0⟧bcd\nefg⟦g1⟧");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_hardLineBreak_isMaskedAsOneToken() {
        final Segment segment = onlySegment("line one" + TWO_SPACES + "\nline two");

        assertThat(segment.masked()).isEqualTo("line one⟦g0⟧\nline two");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_hardLineBreakOwnSpelling_mapHoldsTheTwoTrailingSpaces() {
        final Segment segment = onlySegment("line one" + TWO_SPACES + "\nline two");

        assertThat(segment.placeholders()).containsEntry("g0", TWO_SPACES);
    }

    // IF a segment's format reassembles by splicing into the original byte buffer, THEN the
    // system SHALL carry its source text exactly as the file spells it.
    @Test
    void mask_markdownBackslashEscape_isLeftAsWritten() {
        final Segment segment = onlySegment("A \\* B and C");

        assertThat(segment.masked()).isEqualTo("A \\* B and C");
    }

    // IF a segment's format reassembles by splicing into the original byte buffer, THEN the
    // system SHALL carry its source text exactly as the file spells it.
    @Test
    void mask_markdownCharacterReference_isLeftAsWritten() {
        final Segment segment = onlySegment("AT&amp;T and more");

        assertThat(segment.masked()).isEqualTo("AT&amp;T and more");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered. Regression
    // guard: a backslash-escaped asterisk ahead of the break makes the preceding Text node's literal ("A * B",
    // length 5) shorter than its span ("A \* B  ", length 8) by more than the two escaped characters, so the
    // stated span-end-minus-literal-length formula would yield "* B  ", not the two trailing spaces.
    @Test
    void mask_hardLineBreakAfterABackslashEscapedAsterisk_fragmentIsExactlyTwoSpaces() {
        final Segment segment = onlySegment("A \\* B" + TWO_SPACES + "\nnext line");

        assertThat(segment.placeholders()).containsEntry("g0", TWO_SPACES);
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered. Regression
    // guard: a character reference ahead of the break makes the preceding Text node's literal ("AT&T", length 4)
    // shorter than its span ("AT&amp;T  ", length 10), so the stated formula would yield "mp;T  ", not the two
    // trailing spaces.
    @Test
    void mask_hardLineBreakAfterACharacterReference_fragmentIsExactlyTwoSpaces() {
        final Segment segment = onlySegment("AT&amp;T" + TWO_SPACES + "\nnext line");

        assertThat(segment.placeholders()).containsEntry("g0", TWO_SPACES);
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    // A paired construct whose first or last child carries NO source span. Measured on commonmark 0.24.0, a
    // SoftLineBreak always reports an empty span list and so does the two-trailing-spaces spelling of a hard line
    // break; either can sit at the edge of a link whose label is wrapped across a line, which real Markdown does
    // routinely. Deriving the delimiters from getFirstChild()/getLastChild() asked one of them for a span index
    // and threw ArrayIndexOutOfBoundsException out of the masker — surfacing as ErrorCode.internal and making the
    // whole book impossible to open, not merely mis-masking one segment.
    @Test
    void mask_linkLabelWrappedAcrossALine_masksInsteadOfFailingToOpenTheBook() {
        final Segment segment = onlySegment("Read the [BookLoom docs\n](https://example.org/d) now.");

        assertThat(segment.masked()).isEqualTo("Read the ⟦g0⟧BookLoom docs⟦g1⟧ now.");
        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "[", "g1", "\n](https://example.org/d)"));
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_linkLabelBeginningAtALineBreak_masksInsteadOfFailingToOpenTheBook() {
        final Segment segment = onlySegment("Read [\nthe docs](https://example.org/d) now.");

        assertThat(segment.masked()).isEqualTo("Read ⟦g0⟧the docs⟦g1⟧ now.");
        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "[\n", "g1", "](https://example.org/d)"));
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    @Test
    void mask_linkLabelEndingInAHardLineBreak_masksInsteadOfFailingToOpenTheBook() {
        final Segment segment = onlySegment("Read the [BookLoom docs" + TWO_SPACES + "\n](https://example.org/d) now.");

        // Three tokens, not two: the hard line break inside the label is masked in its own right before the
        // closing delimiter, so its two spaces survive as a fragment the model is told to preserve rather than
        // being folded into the link's closing token.
        assertThat(segment.masked()).isEqualTo("Read the ⟦g0⟧BookLoom docs⟦g1⟧⟦g2⟧ now.");
        assertThat(segment.placeholders())
                .containsEntry("g0", "[")
                .containsEntry("g1", TWO_SPACES)
                .containsEntry("g2", "\n](https://example.org/d)");
    }

    // WHEN a Markdown block is segmented, the system SHALL replace each inline
    // construct using the source ranges it occupies and leave the remaining source text unaltered.
    // The branch where NO child of a paired construct carries a span: a link whose entire label is a line break.
    // There is nothing to wrap a pair around, so it falls through to one atomic token — lossless, and the only
    // path by which the span-less-child guard can be reached with both ends empty.
    @Test
    void mask_linkWhoseWholeLabelIsALineBreak_masksAtomically() {
        final Segment segment = onlySegment("Read [\n](https://example.org/d) now.");

        assertThat(segment.masked()).isEqualTo("Read ⟦g0⟧ now.");
        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "[\n](https://example.org/d)"));
    }
}
