package ua.bookloom.pipeline.project;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentLocator;

/**
 * The locator every segment of an opened book is shown by, built once from the book so that a run, the review desk
 * and the export report all name a segment the same way. A body unit is numbered among body units only, so the
 * auxiliary unit never shifts a chapter number.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentLocators {

    /**
     * Builds the locators of a book.
     *
     * @param document the non-null opened book
     * @return each segment id's locator, in document order; never null, and without an entry for an id the book does
     *     not hold
     */
    public static Map<String, SegmentLocator> of(final Document document) {
        Objects.requireNonNull(document, "document");
        final Map<String, SegmentLocator> locators = new LinkedHashMap<>();
        int bodyUnits = 0;
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary()) {
                addAuxiliary(locators, unit.segments());
            } else {
                bodyUnits++;
                addBody(locators, unit.segments(), bodyUnits);
            }
        }
        log.debug("Built segment locators segments={} bodyUnits={}", locators.size(), bodyUnits);
        return Collections.unmodifiableMap(locators);
    }

    private static void addBody(
            final Map<String, SegmentLocator> locators, final List<Segment> segments, final int unitOrdinal) {
        for (int index = 0; index < segments.size(); index++) {
            final Segment segment = segments.get(index);
            locators.put(segment.id(), SegmentLocator.of(segment.kind(), unitOrdinal, index + 1));
        }
    }

    // An auxiliary segment is numbered among the auxiliary segments of its own kind.
    private static void addAuxiliary(final Map<String, SegmentLocator> locators, final List<Segment> segments) {
        final Map<SegmentKind, Integer> seen = new EnumMap<>(SegmentKind.class);
        for (final Segment segment : segments) {
            final int ordinal = seen.merge(segment.kind(), 1, Integer::sum);
            locators.put(segment.id(), SegmentLocator.of(segment.kind(), 0, ordinal));
        }
    }
}
