package ua.bookloom.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * Rebuilds an opened {@link Document} with target text set on chosen segments, so a write test can drive the
 * reassembly path the pipeline will drive later.
 *
 * <p>Everything in {@link Document}, {@link Unit} and {@link Segment} is a record, so "set a target" is a rebuild
 * of the whole spine — thirteen components copied per segment. Written once here rather than per test, because a
 * hand-copied rebuild that quietly drops {@code anchor} or {@code placeholders} produces a test that passes
 * against a document the parser never emits.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentTargets {

    /**
     * Sets a target on the first unit's leading segments, positionally.
     *
     * @param document the opened document
     * @param targetsByOrder one entry per segment of the first unit, in order, from segment zero; a shorter list
     *     leaves the remaining segments untranslated, which is how a test writes some segments and not others
     * @return a document identical to {@code document} except for those targets
     */
    public static Document withTargets(Document document, List<String> targetsByOrder) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(targetsByOrder, "targetsByOrder");
        final List<Unit> units = new ArrayList<>(document.units());
        final Unit unit = units.get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        for (int index = 0; index < targetsByOrder.size(); index++) {
            segments.set(index, withTargetInner(segments.get(index), targetsByOrder.get(index)));
        }
        units.set(0, new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
        return rebuild(document, units);
    }

    private static Document rebuild(Document document, List<Unit> units) {
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                units);
    }

    private static Segment withTargetInner(Segment segment, String targetInner) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                targetInner,
                segment.status(),
                segment.confidence());
    }
}
