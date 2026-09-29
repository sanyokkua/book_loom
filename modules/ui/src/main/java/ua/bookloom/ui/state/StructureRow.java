package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * One top-level node of the book's structure as the structure screen lists it: the title the book's own navigation
 * gives it and the segments it accounts for.
 *
 * @param title the node's title; empty when the book gives it none
 * @param segmentCount how many translatable segments the node holds; zero for a node with none
 */
public record StructureRow(String title, int segmentCount) {

    /** Rejects a missing title. */
    public StructureRow {
        Objects.requireNonNull(title, "title");
    }
}
