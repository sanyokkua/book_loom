package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;

/**
 * The segments of one unit a picked structure node stands for: all of them, or, for a chapter of a single-unit book
 * (Markdown, FB2), the run of them from its first segment.
 *
 * @param unitId the unit
 * @param first the position of the first segment kept; zero or more
 * @param count how many segments are kept from {@code first}; {@link #ALL} keeps every one to the unit's end
 */
public record UnitPart(String unitId, int first, int count) {

    /** The count that keeps every segment from {@code first} to the end of the unit. */
    public static final int ALL = Integer.MAX_VALUE;

    /** Rejects a missing unit and a negative position or count. */
    public UnitPart {
        Objects.requireNonNull(unitId, "unitId");
        if (first < 0 || count < 0) {
            throw new IllegalArgumentException("a unit part starts and counts from zero");
        }
    }

    /**
     * Every segment of a unit.
     *
     * @param unitId the unit
     * @return the part that keeps the whole unit
     */
    public static UnitPart whole(final String unitId) {
        return new UnitPart(unitId, 0, ALL);
    }

    /**
     * Keeps this part's segments of the unit's whole list.
     *
     * @param all every segment of the unit, in order
     * @param <T> what a segment is listed as
     * @return the kept segments in order; empty when the part starts past the end
     */
    public <T> List<T> of(final List<T> all) {
        Objects.requireNonNull(all, "all");
        final int from = Math.min(first, all.size());
        final int to = (int) Math.min((long) from + count, all.size());
        return all.subList(from, to);
    }
}
