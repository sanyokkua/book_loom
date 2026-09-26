package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;

/**
 * Hand-built parsed books for the screen tests: real {@link Document} records, never mocks, whose unit and segment
 * counts and metadata are exactly what a test states.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookFixtures {

    /**
     * A document with one unit per entry of {@code segmentsPerUnit}, holding that many segments.
     *
     * @param id the document id; distinct ids make two fixtures distinct records
     * @param format the format the document claims
     * @param declaredLang the language it declares, or {@code null}
     * @param title the metadata title, or {@code null} to leave the key out
     * @param author the metadata author, or {@code null} to leave the key out
     * @param segmentsPerUnit how many segments each unit carries; its length is the unit count
     * @return the document
     */
    public static Document book(
            final String id,
            final BookFormat format,
            final @Nullable String declaredLang,
            final @Nullable String title,
            final @Nullable String author,
            final int... segmentsPerUnit) {
        final Map<String, String> metadata = new HashMap<>();
        if (title != null) {
            metadata.put(MetadataKey.TITLE.key(), title);
        }
        if (author != null) {
            metadata.put(MetadataKey.AUTHOR.key(), author);
        }
        final List<Unit> units = new ArrayList<>();
        for (int u = 0; u < segmentsPerUnit.length; u++) {
            units.add(unit(u, "unit-" + u + ".xhtml", segmentsPerUnit[u]));
        }
        return new Document(id, format, declaredLang, null, null, null, "hash-" + id, metadata, units);
    }

    /**
     * A document whose units carry the resource paths a test states, in that order, each holding that many segments.
     *
     * @param id the document id
     * @param format the format the document claims
     * @param hrefs the resource path of each unit, in reading order; its size is the unit count
     * @param segmentsPerUnit how many segments each unit carries; must have exactly one entry per href
     * @return the document, with no title, author or declared language
     */
    public static Document book(
            final String id, final BookFormat format, final List<String> hrefs, final int... segmentsPerUnit) {
        if (hrefs.size() != segmentsPerUnit.length) {
            throw new IllegalArgumentException(
                    "one segment count per href: " + hrefs.size() + " hrefs, " + segmentsPerUnit.length + " counts");
        }
        final List<Unit> units = new ArrayList<>();
        for (int u = 0; u < segmentsPerUnit.length; u++) {
            units.add(unit(u, hrefs.get(u), segmentsPerUnit[u]));
        }
        return new Document(id, format, null, null, null, null, "hash-" + id, new HashMap<>(), units);
    }

    /** The book the specification's first scenario opens: three units, nine segments. */
    public static Document frankenstein() {
        return book("frankenstein", BookFormat.EPUB, "en", "Frankenstein", "Mary Shelley", 2, 3, 4);
    }

    private static Unit unit(final int order, final String href, final int segmentCount) {
        final String unitId = "unit-" + order;
        final List<Segment> segments = new ArrayList<>();
        for (int s = 0; s < segmentCount; s++) {
            segments.add(new Segment(
                    unitId + ":" + s,
                    unitId,
                    s,
                    SegmentKind.PARAGRAPH,
                    "text",
                    "text",
                    Map.of(),
                    "hash",
                    null,
                    null,
                    new NodeAnchor(List.of(s), 0),
                    null,
                    SegmentStatus.PENDING,
                    0.0));
        }
        return new Unit(unitId, order, href, "application/xhtml+xml", new SkeletonHandle(unitId), segments);
    }
}
