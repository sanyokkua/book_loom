package ua.bookloom.document.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * Collects a document's <strong>body</strong> segments (task 4.5) — every unit's segments except the synthetic
 * auxiliary unit's — the population the statistics count, so a future auxiliary-unit segment (title, alt text, nav
 * label) never inflates the book's reported segment or word count.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BodySegments {

    /**
     * Collects {@code document}'s body segments, in unit and document order.
     *
     * @param document the document to read; never null
     * @return the body segments; never null, empty if the document has none
     */
    public static List<Segment> of(Document document) {
        Objects.requireNonNull(document, "document");
        final List<Segment> segments = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (!unit.isAuxiliary()) {
                segments.addAll(unit.segments());
            }
        }
        return segments;
    }
}
