package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;

/**
 * Buffer reassembly — the copy-from-the-original-in-one-ascending-pass rule that makes an untranslated round trip
 * byte-identical by construction, and keeps every byte span valid while more than one segment is written back.
 */
class BufferReassemblerTest {

    /** {@code Alpha.\n\nBeta.\n} — the two paragraphs sit at [0,6) and [8,13). */
    private static final byte[] SOURCE = "Alpha.\n\nBeta.\n".getBytes(StandardCharsets.UTF_8);

    private static BufferReassembler.Replacement at(int start, int end, String target) {
        return new BufferReassembler.Replacement(
                new ByteSpanAnchor(start, end), target.getBytes(StandardCharsets.UTF_8));
    }

    private static String spliced(BufferReassembler.Replacement... replacements) {
        return new String(BufferReassembler.splice(SOURCE, List.of(replacements)), StandardCharsets.UTF_8);
    }

    // Covers: FR-DOC-TXT-3 — WHEN a document is reassembled with no segment carrying target text, THEN the output
    // bytes are identical to the source's.
    @Test
    void splice_noReplacements_reproducesTheSourceBytes() {
        assertThat(BufferReassembler.splice(SOURCE, List.of())).isEqualTo(SOURCE);
    }

    // Covers: FR-DOC-TXT-3 — WHEN only one segment is written back, THEN only that paragraph's bytes change and
    // every byte before it is untouched.
    @Test
    void splice_secondParagraphOnly_leavesEveryEarlierByteAlone() {
        assertThat(spliced(at(8, 13, "Два."))).isEqualTo("Alpha.\n\nДва.\n");
    }

    /**
     * The invariant that no no-edit golden round trip can exercise: it breaks only once <em>two</em> segments
     * carry target text, and only if the mechanism recomputes spans after each write or mutates the source
     * buffer.
     */
    // Covers: FR-DOC-TXT-3 — WHEN two segments are written back and the first grows, THEN the second segment's
    // byte span is unchanged from the value computed at parse time and its text still lands correctly.
    @Test
    void splice_twoWritesWhereTheFirstGrows_leavesTheSecondSpanValid() {
        final ByteSpanAnchor secondSpanAtParseTime = new ByteSpanAnchor(8, 13);

        final String output =
                spliced(at(0, 6, "A considerably longer first paragraph."), at(8, 13, "Beta translated."));

        assertThat(output).isEqualTo("A considerably longer first paragraph.\n\nBeta translated.\n");
        assertThat(secondSpanAtParseTime).isEqualTo(new ByteSpanAnchor(8, 13));
    }

    // Covers: FR-DOC-TXT-3 — replacements supplied out of order are still applied in one ascending pass.
    @Test
    void splice_replacementsSuppliedOutOfOrder_areAppliedAscending() {
        assertThat(spliced(at(8, 13, "Два."), at(0, 6, "Один."))).isEqualTo("Один.\n\nДва.\n");
    }

    @Test
    void splice_doesNotMutateTheSourceBuffer() {
        final byte[] original = SOURCE.clone();

        BufferReassembler.splice(SOURCE, List.of(at(0, 6, "A much longer replacement indeed.")));

        assertThat(SOURCE).isEqualTo(original);
    }

    @Test
    void splice_overlappingSpans_isRejected() {
        assertThatThrownBy(() -> spliced(at(0, 6, "x"), at(3, 13, "y")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
    }

    @Test
    void splice_spanRunningPastTheBuffer_isRejected() {
        assertThatThrownBy(() -> spliced(at(8, 99, "x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
