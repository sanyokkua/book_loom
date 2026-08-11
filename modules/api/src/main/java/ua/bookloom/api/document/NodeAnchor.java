package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;

/**
 * Addresses a segment inside a tree-shaped skeleton — an EPUB spine document or an FB2 XML document — as a
 * root-to-block index path plus the index of the line-break-delimited run the segment covers within that block
 * (ADR-0025).
 *
 * <p><strong>Why an index path and not an element id.</strong> EC-EPUB-4 requires a duplicate {@code id}
 * attribute to be preserved rather than corrected, so ids in the source are not guaranteed unique and cannot
 * address a node — two elements sharing {@code id="note1"} would resolve both segments to the same one. An index
 * path is stable under the only mutation this system performs — replacing a run's inner content — because that
 * changes no element's position among its siblings.
 *
 * <p><strong>Why a run index and not a child index.</strong> Some converters write a whole chapter as one element
 * whose paragraphs are separated only by {@code <br/>}, so several segments legitimately share one block and a
 * node path alone no longer identifies a segment. {@code runIndex} is {@code 0} for every block containing no line
 * break, which is the overwhelming majority.
 *
 * @param nodePath the root-to-element index path, outermost first; never negative entries
 * @param runIndex the index of this segment's line-break-delimited run within that block; never negative, and
 *     {@code 0} for a block containing no line break
 */
public record NodeAnchor(List<Integer> nodePath, int runIndex) implements SkeletonAnchor {

    /**
     * Validates the path and defensively copies {@code nodePath} so a caller-held mutable list cannot corrupt this
     * record after construction.
     */
    public NodeAnchor {
        Objects.requireNonNull(nodePath, "nodePath");
        if (runIndex < 0) {
            throw new IllegalArgumentException("runIndex must be >= 0, but was " + runIndex);
        }
        for (final Integer step : nodePath) {
            Objects.requireNonNull(step, "nodePath entry");
            if (step < 0) {
                throw new IllegalArgumentException("nodePath entries must be >= 0, but found " + step);
            }
        }
        nodePath = List.copyOf(nodePath);
    }
}
