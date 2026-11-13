package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** Small invented books and their stored records for the work list's tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WorkListFixtures {

    static final String PROJECT = "p1";

    static Document chapterBook() {
        final String paragraph =
                "The boats waited by the shore while the river ran slowly past the old mill and the bridge beyond it.";
        final List<Segment> segments = java.util.stream.IntStream.range(0, 36)
                .mapToObj(index -> new Segment(
                        "big:" + index,
                        "big",
                        index,
                        SegmentKind.PARAGRAPH,
                        paragraph,
                        paragraph,
                        Map.of(),
                        "hash-" + index,
                        null,
                        null,
                        new ByteSpanAnchor(0, paragraph.length()),
                        null,
                        SegmentStatus.PENDING,
                        0.0))
                .toList();
        final Unit big = new Unit("big", 0, "big.md", "text/markdown", new SkeletonHandle("big"), segments);
        return new Document("Book", BookFormat.MARKDOWN, "en", null, "UTF-8", false, "hash", Map.of(), List.of(big));
    }

    static SegmentRecord record(final Segment segment, final SegmentStatus status) {
        return new SegmentRecord(
                PROJECT,
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                status,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }

    static Document documentWithAuxiliary() {
        final Document body = document();
        final Segment title = new Segment(
                "aux:title",
                Unit.AUXILIARY_ID,
                0,
                SegmentKind.METADATA_TITLE,
                "Book",
                "Book",
                Map.of(),
                "hash-title",
                null,
                null,
                new ByteSpanAnchor(0, 4),
                null,
                SegmentStatus.PENDING,
                0.0);
        final Unit auxiliary = new Unit(
                Unit.AUXILIARY_ID,
                2,
                "one.md",
                "application/x-bookloom-auxiliary",
                new SkeletonHandle("aux"),
                List.of(title));
        final List<Unit> units = new ArrayList<>(body.units());
        units.add(auxiliary);
        return body.withUnits(units);
    }

    static Document document() {
        return new Document(
                "Book",
                BookFormat.MARKDOWN,
                "en",
                null,
                "UTF-8",
                false,
                "hash",
                Map.of(),
                List.of(unit("one", 0, 4), unit("two", 1, 1)));
    }

    static Unit unit(final String id, final int order, final int segmentCount) {
        final List<Segment> segments = java.util.stream.IntStream.range(0, segmentCount)
                .mapToObj(index -> segment(id, index))
                .toList();
        return new Unit(id, order, id + ".md", "text/markdown", new SkeletonHandle(id), segments);
    }

    static Segment segment(final String unit, final int order) {
        final String text = "Source " + order + ".";
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                SegmentKind.PARAGRAPH,
                text,
                text,
                Map.of(),
                "hash-" + unit + "-" + order,
                null,
                null,
                new ByteSpanAnchor(0, text.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
