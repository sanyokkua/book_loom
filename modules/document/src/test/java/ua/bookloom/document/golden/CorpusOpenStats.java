package ua.bookloom.document.golden;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;

/**
 * Builds the corpus verification's P0 outcome — every field design.md D6's P0 row asks for beyond the bare fact
 * that the book opened (design.md D6, task 11.1).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusOpenStats {

    static CorpusOpenOutcome.Opened openedOutcomeOf(Path source, Document document, long elapsedMs) {
        final List<Segment> segments = CorpusDocuments.segmentsOf(document);
        final List<Integer> lengths =
                segments.stream().map(s -> s.sourceInner().length()).sorted().toList();
        return new CorpusOpenOutcome.Opened(
                document.format().name(),
                document.charset(),
                document.hasBom(),
                document.declaredLang(),
                document.units().size(),
                segments.size(),
                kindHistogramOf(segments),
                percentile(lengths, 0.0),
                percentile(lengths, 0.5),
                percentile(lengths, 0.95),
                percentile(lengths, 1.0),
                emptyOrDuplicateIdCountOf(segments),
                elapsedMs,
                TextCoverage.of(source, document.format(), document));
    }

    private static Map<String, Integer> kindHistogramOf(List<Segment> segments) {
        final Map<String, Integer> histogram = new LinkedHashMap<>();
        for (final Segment segment : segments) {
            histogram.merge(segment.kind().name(), 1, Integer::sum);
        }
        return histogram;
    }

    private static int emptyOrDuplicateIdCountOf(List<Segment> segments) {
        final Map<String, Integer> idCounts = new LinkedHashMap<>();
        int emptyOrDuplicate = 0;
        for (final Segment segment : segments) {
            if (segment.sourceInner().isEmpty()) {
                emptyOrDuplicate++;
            }
            if (idCounts.merge(segment.id(), 1, Integer::sum) > 1) {
                emptyOrDuplicate++;
            }
        }
        return emptyOrDuplicate;
    }

    private static int percentile(List<Integer> sortedLengths, double fraction) {
        if (sortedLengths.isEmpty()) {
            return 0;
        }
        final int index = (int) Math.ceil(fraction * (sortedLengths.size() - 1));
        return sortedLengths.get(Math.clamp(index, 0, sortedLengths.size() - 1));
    }
}
