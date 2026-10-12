package ua.bookloom.pipeline.index;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What one pass over a book leaves: the reading order and the postings. Segment texts are the document's own masked
 * strings, kept to read a segment again when a mention or a window needs its words.
 *
 * @param infos what is known of each segment, by ordinal
 * @param texts each segment's masked text, by ordinal
 * @param spans each segment's unit span, by ordinal
 * @param ordinalById the ordinal of each segment id
 * @param story the ordinals of the non-auxiliary body segments
 * @param postings each word key's occurrences in reading order
 * @param stats each word key's counts
 * @param language the book's language tag for token estimates, or {@code null} when unknown
 */
record IndexData(
        List<SegmentInfo> infos,
        List<String> texts,
        List<UnitSpan> spans,
        Map<String, Integer> ordinalById,
        List<Integer> story,
        Map<String, List<Posting>> postings,
        Map<String, WordStats> stats,
        @Nullable String language) {}
