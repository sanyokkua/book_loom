package ua.bookloom.pipeline.index;

import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.UnitRole;

/**
 * What the index knows of a segment besides its words.
 *
 * @param ordinal its position in reading order across the whole book
 * @param segmentId the segment's id
 * @param unitId its unit's id
 * @param role its unit's role
 * @param kind the block it was parsed from
 * @param auxiliary whether its unit is the synthetic auxiliary unit
 */
public record SegmentInfo(
        int ordinal, String segmentId, String unitId, UnitRole role, SegmentKind kind, boolean auxiliary) {}
