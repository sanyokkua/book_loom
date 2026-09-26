package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * One unit of the parsed book as the structure screen lists it: the path, the position and the segment count the
 * parse produced, and no title, because {@link ua.bookloom.api.document.Unit} carries none and a row must not invent one.
 *
 * @param href the unit's resource path within the source container
 * @param order the unit's zero-based position in reading order
 * @param segmentCount how many translatable segments the unit holds; zero for a unit with none
 */
public record StructureRow(String href, int order, int segmentCount) {

    /** Rejects a missing path. */
    public StructureRow {
        Objects.requireNonNull(href, "href");
    }
}
