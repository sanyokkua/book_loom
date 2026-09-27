package ua.bookloom.api.project;

import java.util.Objects;
import ua.bookloom.api.document.SegmentKind;

/**
 * The human-readable position of a segment within its book — what the review panel, toasts, the log and the export
 * report show, never the raw segment id.
 *
 * @param text the rendered locator text
 */
public record SegmentLocator(String text) {

    private static final String SEPARATOR = " · ";

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public SegmentLocator {
        Objects.requireNonNull(text, "text");
    }

    /**
     * Renders a segment's locator from its position.
     *
     * @param kind the non-null kind of the segment being located
     * @param unitOrdinal the segment's unit's position among body units, 1-based, or {@code 0} for the auxiliary
     *     unit
     * @param segmentOrdinal a body segment's 1-based position within its unit, or an auxiliary segment's 1-based
     *     position among the auxiliary segments of its kind
     * @return the rendered locator
     */
    public static SegmentLocator of(final SegmentKind kind, final int unitOrdinal, final int segmentOrdinal) {
        Objects.requireNonNull(kind, "kind");
        return unitOrdinal == 0
                ? new SegmentLocator(auxiliaryText(kind, segmentOrdinal))
                : new SegmentLocator("ch" + unitOrdinal + SEPARATOR + "p" + pad(segmentOrdinal));
    }

    private static String auxiliaryText(final SegmentKind kind, final int segmentOrdinal) {
        return switch (kind) {
            case METADATA_TITLE -> "title";
            case METADATA_AUTHOR -> "author" + SEPARATOR + pad(segmentOrdinal);
            case METADATA_DESCRIPTION -> "description" + SEPARATOR + pad(segmentOrdinal);
            case NAV_LABEL -> "nav" + SEPARATOR + pad(segmentOrdinal);
            case TITLE -> "page title" + SEPARATOR + pad(segmentOrdinal);
            case ALT -> "alt" + SEPARATOR + pad(segmentOrdinal);
            case FRONTMATTER_VALUE -> "frontmatter" + SEPARATOR + pad(segmentOrdinal);
            default -> throw new IllegalArgumentException("unsupported auxiliary segment kind: " + kind);
        };
    }

    private static String pad(final int ordinal) {
        return String.format("%02d", ordinal);
    }
}
