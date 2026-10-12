package ua.bookloom.pipeline.index;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
import ua.bookloom.api.document.UnitRole;

/** Builds small invented documents whose segments hold the given masked lines, for the book-index tests. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IndexTestBooks {

    /** A unit of paragraphs; segment ids are {@code unitId:position}. */
    public static Unit unit(final String id, final UnitRole role, final String... lines) {
        return unit(id, role, SegmentKind.PARAGRAPH, lines);
    }

    /** A unit whose segments are all of one kind. */
    public static Unit unit(final String id, final UnitRole role, final SegmentKind kind, final String... lines) {
        final List<Segment> segments = IntStream.range(0, lines.length)
                .mapToObj(order -> segment(id, order, kind, lines[order]))
                .toList();
        return new Unit(id, 0, id + ".xhtml", "application/xhtml+xml", new SkeletonHandle(id), segments, role);
    }

    /** A document declared English holding the units in the given reading order. */
    public static Document document(final Unit... units) {
        return new Document("invented", BookFormat.EPUB, "en", null, null, null, "hash", Map.of(), List.of(units));
    }

    /** A document of one body unit of paragraphs. */
    public static Document oneUnit(final String... lines) {
        return document(unit("body", UnitRole.BODY, lines));
    }

    /** The invented book of the index tests: front 0; ch1 1, 2, 3; aux 4; ch2 5; back 6. */
    public static Document sample() {
        return document(
                unit("front", UnitRole.FRONT_MATTER, SegmentKind.TITLE, "Harbour Press presents."),
                unit(
                        "ch1",
                        UnitRole.BODY,
                        "Ivo walked to the mark. A mark was cut in the stone.",
                        "The old bridge held. Ivo crossed the Bridge slowly.",
                        "He told Mark to bring Ivo’s coat."),
                unit("aux", UnitRole.BODY, SegmentKind.METADATA_TITLE, "A Tale of Ivo"),
                unit("ch2", UnitRole.BODY, "Ivo and Mark met at the Great Hall. Hall doors stood open."),
                unit("back", UnitRole.BACK_MATTER, "Thanks to Ivo."));
    }

    private static Segment segment(final String unit, final int order, final SegmentKind kind, final String masked) {
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                kind,
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
