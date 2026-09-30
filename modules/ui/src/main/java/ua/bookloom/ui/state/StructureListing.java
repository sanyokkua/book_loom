package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.StructureNode;

/**
 * What the structure screen shows for an opened book: the top-level nodes of its structure tree, in navigation order,
 * and the sum of their segment counts. It is built here, away from the scene graph, so the numbers a person reads can
 * be checked without a window and cannot drift from the profile.
 *
 * @param roots the top-level nodes in navigation order, unmodifiable; each carries its own children
 */
@Slf4j
public record StructureListing(List<StructureNode> roots) {

    /** Copies the roots so a caller's later change cannot alter what the screen shows. */
    public StructureListing {
        Objects.requireNonNull(roots, "roots");
        roots = List.copyOf(roots);
    }

    /**
     * Lists the structure of a book.
     *
     * @param profile the opened book's profile; its structure is taken in the order it holds it
     * @return the tree and its total; no roots and a zero total when the book has no structure
     */
    public static StructureListing of(final BookProfile profile) {
        Objects.requireNonNull(profile, "profile");
        final StructureListing listing = new StructureListing(profile.structure());
        log.debug(
                "structure listing built with {} top-level node(s), {} node(s) in all, {} segment(s)",
                listing.roots.size(),
                listing.nodeCount(),
                listing.totalSegments());
        return listing;
    }

    /**
     * Sums the top-level counts rather than storing a figure, so the total cannot disagree with them; a node's count
     * already includes its children, and a node that only points into a unit another node counts adds nothing.
     *
     * @return the segments across every top-level node; zero when there are none
     */
    public int totalSegments() {
        return roots.stream()
                .map(StructureNode::segmentCount)
                .mapToInt(count -> count == null ? 0 : count)
                .sum();
    }

    /**
     * Counts every node of the tree at any depth.
     *
     * @return the number of nodes; zero when the book has no structure
     */
    public int nodeCount() {
        return roots.stream().mapToInt(StructureListing::sizeOf).sum();
    }

    private static int sizeOf(final StructureNode node) {
        return 1 + node.children().stream().mapToInt(StructureListing::sizeOf).sum();
    }
}
