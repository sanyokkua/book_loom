package ua.bookloom.document.golden;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the corpus verification's P0 probe records for one book — the outcome of {@code DocumentPort.open}
 * (design.md D6, task 11.1).
 */
sealed interface CorpusOpenOutcome {

    /**
     * The book failed to open at all.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record Failed(String errorCode) implements CorpusOpenOutcome {}

    /**
     * The book opened; every field design.md D6's P0 row asks for.
     *
     * @param format the resolved {@code BookFormat} name
     * @param charset the resolved charset, or {@code null} for a container format with no document-level encoding
     * @param hasBom whether a byte-order mark was present, or {@code null} where the question has no referent
     * @param declaredLang the book's own declared language, or {@code null} when it declares none
     * @param unitCount the number of content units
     * @param segmentCount the total number of segments across every unit
     * @param kindHistogram segment count per {@code SegmentKind} name
     * @param minSegmentLength the shortest segment's {@code sourceInner} length in characters, or {@code 0} when
     *     there are no segments
     * @param medianSegmentLength the median segment length in characters, or {@code 0} when there are no segments
     * @param p95SegmentLength the 95th-percentile segment length in characters, or {@code 0} when there are no
     *     segments
     * @param maxSegmentLength the longest segment's length in characters, or {@code 0} when there are no segments
     * @param emptyOrDuplicateIdSegmentCount the count of segments whose {@code sourceInner} is empty, plus
     *     segments whose id repeats an earlier segment's id
     * @param elapsedMs wall-clock time the open call took
     */
    record Opened(
            String format,
            @Nullable String charset,
            @Nullable Boolean hasBom,
            @Nullable String declaredLang,
            int unitCount,
            int segmentCount,
            Map<String, Integer> kindHistogram,
            int minSegmentLength,
            int medianSegmentLength,
            int p95SegmentLength,
            int maxSegmentLength,
            int emptyOrDuplicateIdSegmentCount,
            long elapsedMs)
            implements CorpusOpenOutcome {}
}
