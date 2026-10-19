package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** Packing a unit's segments into chunks by token budget and segment cap. */
class ChunkPackerTest {

    private static final int BUDGET = 1200;

    @ParameterizedTest
    @CsvSource({"4,'4,4,2'", "1,'1,1,1,1,1,1,1,1,1,1'", "8,'8,2'"})
    void pack_tenShortSegments_splitsByCap(final int cap, final String expectedSizes) {
        final List<Segment> segments = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> segment("U", i, "Short line " + i + "."))
                .toList();

        assertThat(sizes(ChunkPacker.pack(segments, "en", BUDGET, cap))).isEqualTo(expectedSizes);
    }

    @Test
    void pack_twoUnitsPackedSeparately_neverShareAChunk() {
        final List<Chunk> first = ChunkPacker.pack(unit("A", 3), "en", BUDGET, 8);
        final List<Chunk> second = ChunkPacker.pack(unit("B", 2), "en", BUDGET, 8);

        assertThat(sizes(first)).isEqualTo("3");
        assertThat(sizes(second)).isEqualTo("2");
        assertThat(second.get(0).unitId()).isEqualTo("B");
    }

    @Test
    void pack_segmentsOfFourHundredTokens_closeAChunkAtTheBudget() {
        final String text = "a".repeat(1390);
        final List<Segment> segments =
                IntStream.range(0, 7).mapToObj(i -> segment("U", i, text)).toList();

        assertThat(sizes(ChunkPacker.pack(segments, "en", BUDGET, 8))).isEqualTo("3,3,1");
    }

    @Test
    void pack_oversizedParagraph_becomesItsOwnFlaggedChunk() {
        final List<Segment> segments =
                List.of(segment("U", 0, "Short."), segment("U", 1, "a".repeat(6000)), segment("U", 2, "Short."));

        final List<Chunk> chunks = ChunkPacker.pack(segments, "en", BUDGET, 8);

        assertThat(sizes(chunks)).isEqualTo("1,1,1");
        assertThat(chunks).extracting(Chunk::oversized).containsExactly(false, true, false);
    }

    @Test
    void pack_emptyUnit_yieldsNoChunk() {
        assertThat(ChunkPacker.pack(List.of(), "en", BUDGET, 8)).isEmpty();
    }

    private static String sizes(final List<Chunk> chunks) {
        return chunks.stream()
                .map(c -> String.valueOf(c.segments().size()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private static List<Segment> unit(final String unitId, final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> segment(unitId, i, "Line " + i + "."))
                .toList();
    }

    private static Segment segment(final String unitId, final int order, final String masked) {
        return new Segment(
                unitId + ":" + order,
                unitId,
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
                0.0);
    }
}
