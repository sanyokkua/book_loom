package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;

/** Builds pending segments whose masked text is the given line, for the name-scan tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryTestSegments {

    static List<Segment> of(final List<String> masked) {
        return IntStream.range(0, masked.size())
                .mapToObj(index -> segment(index, masked.get(index)))
                .toList();
    }

    private static Segment segment(final int order, final String masked) {
        return new Segment(
                "u1:" + order,
                "u1",
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
