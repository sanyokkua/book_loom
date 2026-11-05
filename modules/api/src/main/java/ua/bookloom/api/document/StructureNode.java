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
 * @param firstSegment the position in {@code unitId}'s segments of the first segment this node accounts for, or
 *     {@code null} when the node stands for its unit whole; a single-unit format (Markdown, FB2) cuts its one unit into
 *     chapters this way, each node's segments running from here for {@code segmentCount}, its descendants included
 * @param listed {@code true} if the book's own navigation names this node, {@code false} if the structure added it so
 *     every segment is shown (an EPUB spine file no entry reaches, the text before a Markdown book's first heading or
 *     outside an FB2 book's sections, a plain-text book's one unit); the chapter rule cuts only at listed nodes
 */
public record StructureNode(
        String title,
        @Nullable String unitId,
        @Nullable Integer segmentCount,
        List<StructureNode> children,
        @Nullable Integer firstSegment,
        boolean listed) {

    /**
     * Validates the non-nullable components and defensively copies {@code children} so a caller-held mutable list
     * cannot corrupt this record after construction.
     */
    public StructureNode {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /** A listed node that starts at a given segment of its unit. */
    public StructureNode(
            final String title,
            final @Nullable String unitId,
            final @Nullable Integer segmentCount,
            final List<StructureNode> children,
            final @Nullable Integer firstSegment) {
        this(title, unitId, segmentCount, children, firstSegment, true);
    }

    /** A listed node that stands for its unit whole, or for none. */
    public StructureNode(
            final String title,
            final @Nullable String unitId,
            final @Nullable Integer segmentCount,
            final List<StructureNode> children) {
        this(title, unitId, segmentCount, children, null, true);
    }
}
