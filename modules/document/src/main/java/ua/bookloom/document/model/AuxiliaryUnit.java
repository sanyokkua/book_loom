package ua.bookloom.document.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;

/**
 * Builds the one auxiliary unit every opened book of every format ends with (ADR-0041). Its identity is fixed —
 * id {@link Unit#AUXILIARY_ID}, a media type no resource can carry — so a reader that finds nothing to put in it
 * still produces it, and the same file yields the same segments whatever the Book Brief's switches say.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuxiliaryUnit {

    /** The media type that marks a unit as auxiliary rather than a resource of the source container. */
    public static final String MEDIA_TYPE = "application/x-bookloom-auxiliary";

    /**
     * Builds the auxiliary unit over a fresh skeleton handle.
     *
     * @param href the package document path, or the file name for a single-file format
     * @param order the position after the last body unit; never negative
     * @param handleId the opaque id of the slot table this unit's handle points at
     * @param segments the auxiliary segments, empty when the format has none yet
     * @return the auxiliary unit
     */
    public static Unit of(String href, int order, String handleId, List<Segment> segments) {
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(handleId, "handleId");
        Objects.requireNonNull(segments, "segments");
        return new Unit(Unit.AUXILIARY_ID, order, href, MEDIA_TYPE, new SkeletonHandle(handleId), segments);
    }

    /**
     * Builds an auxiliary unit with no segment, for a format whose slots are not read yet.
     *
     * @param href the file name
     * @param order the position after the last body unit; never negative
     * @return the empty auxiliary unit
     */
    public static Unit empty(String href, int order) {
        return of(href, order, UUID.randomUUID().toString(), List.of());
    }
}
