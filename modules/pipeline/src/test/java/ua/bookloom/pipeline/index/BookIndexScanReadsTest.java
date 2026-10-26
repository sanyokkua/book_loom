package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** The running-text view the scans read: built once per source language on one index, and cut only where needed. */
class BookIndexScanReadsTest {

    private static final List<Integer> BOTH = List.of(0, 1);

    private BookIndex index;

    @BeforeEach
    void setUp() {
        index = IndexTestBooks.ofSegments(List.of(
                segment(0, "Ivo said ⟦g1⟧Gran Bazar⟦g2⟧ to Ann.", List.of(new PlaceholderPair("⟦g1⟧", "⟦g2⟧", "fr"))),
                segment(1, "Ann walked home.", List.of())));
    }

    // IF one index is read for two source languages, THEN a run marked French is dropped for English and kept for
    // French.
    @Test
    void scanReads_twoLanguagesOnOneIndex_theForeignRunDropsOnlyForTheOther() {
        final List<Occurrences.Read> english = index.scanReads(BOTH, "en");
        final List<Occurrences.Read> french = index.scanReads(BOTH, "fr");

        assertThat(english.getFirst().words())
                .extracting(Occurrences.Word::text)
                .containsExactly("Ivo", "said", "to", "Ann");
        assertThat(french.getFirst().words())
                .extracting(Occurrences.Word::text)
                .containsExactly("Ivo", "said", "Gran", "Bazar", "to", "Ann");
    }

    @Test
    void scanReads_sameLanguageTwice_reusesTheCachedView() {
        final List<Occurrences.Read> first = index.scanReads(BOTH, "en");
        index.scanReads(BOTH, "fr");
        final List<Occurrences.Read> second = index.scanReads(BOTH, "en");

        assertThat(second.getFirst()).isSameAs(first.getFirst());
        assertThat(second.getLast()).isSameAs(first.getLast());
    }

    // IF nothing of a segment is cut, THEN its scan read holds the same words and sentences as the whole segment's.
    @Test
    void scanReads_segmentNothingIsCutFrom_readsAsTheWholeSegment() {
        assertThat(index.scanReads(List.of(1), "en").getFirst())
                .isEqualTo(index.reads(List.of(1)).getFirst());
    }

    private static Segment segment(final int order, final String masked, final List<PlaceholderPair> pairs) {
        return new Segment(
                "body:" + order,
                "body",
                order,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                null,
                pairs,
                List.of());
    }
}
