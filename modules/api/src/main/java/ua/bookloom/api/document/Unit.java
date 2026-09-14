package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;

/**
 * One content unit of a parsed {@link Document} — an EPUB spine document, an FB2 body, or the equivalent for
 * Markdown/TXT — carrying its immutable skeleton and the ordered segments extracted from it
 * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}).
 *
 * @param id the unit's stable id, unique within its document (an EPUB spine item's manifest id, for example)
 * @param order this unit's position in reading order (spine order for EPUB); never negative
 * @param href the unit's resource path/name within the source container
 * @param mediaType the unit's declared media type (for example {@code application/xhtml+xml})
 * @param skeleton the opaque handle to this unit's parsed tree; see {@link SkeletonHandle}
 * @param segments this unit's translatable segments, in document order
 */
public record Unit(
        String id, int order, String href, String mediaType, SkeletonHandle skeleton, List<Segment> segments) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies {@code segments} into an
     * unmodifiable list, so a caller-held mutable list cannot corrupt this record after construction.
     */
    public Unit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(skeleton, "skeleton");
        Objects.requireNonNull(segments, "segments");
        if (order < 0) {
            throw new IllegalArgumentException("order must be >= 0, but was " + order);
        }
        segments = List.copyOf(segments);
    }

    /**
     * Returns a unit with a replacement segment list while preserving its identity and skeleton.
     *
     * @param segments the non-null replacement segments in document order
     * @return a new unit with the supplied segments
     */
    public Unit withSegments(final List<Segment> segments) {
        Objects.requireNonNull(segments, "segments");
        return new Unit(id, order, href, mediaType, skeleton, segments);
    }
}
