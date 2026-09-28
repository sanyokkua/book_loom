package ua.bookloom.document.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/** Test helper: a copy of a one-unit document in which one segment carries target text. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TargetedDocuments {

    /**
     * @param document a document with at least one unit
     * @param segmentOrder the index of the first unit's segment to translate
     * @param targetInner the target text
     * @return the same document with that segment's target set
     */
    public static Document withTarget(Document document, int segmentOrder, String targetInner) {
        final Unit unit = document.units().get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(segmentOrder, withTargetInner(segments.get(segmentOrder), targetInner));
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                List.of(new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments)));
    }

    private static Segment withTargetInner(Segment s, String targetInner) {
        return new Segment(
                s.id(),
                s.unit(),
                s.order(),
                s.kind(),
                s.sourceInner(),
                s.masked(),
                s.placeholders(),
                s.sourceHash(),
                s.prevKey(),
                s.nextKey(),
                s.anchor(),
                targetInner,
                s.status(),
                s.confidence());
    }
}
