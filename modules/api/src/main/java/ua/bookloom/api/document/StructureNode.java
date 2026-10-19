package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One node of a book's structure tree, derived from its own navigation ({@code specs/document-round-trip/spec.md}
 * "Derive the book's structure tree from its own navigation") — the structure screen shows chapter titles and
 * counts the parsed {@link Document} does not itself carry.
 *
 * @param title the node's title, shown as {@code "Untitled"} by the structure screen when empty
 * @param unitId the id of the {@link Unit} this node points into, or {@code null} when the node has no unit of its
 *     own (a purely organizational grouping, for example)
 * @param segmentCount the number of segments this node accounts for, or {@code null} when this node is an entry
 *     into a unit whose segments are already counted by another node
 * @param children this node's child nodes, in navigation order, defensively copied and unmodifiable
 */
public record StructureNode(
        String title, @Nullable String unitId, @Nullable Integer segmentCount, List<StructureNode> children) {

    /**
     * Validates the non-nullable components and defensively copies {@code children} so a caller-held mutable list
     * cannot corrupt this record after construction.
     */
    public StructureNode {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
