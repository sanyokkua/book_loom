package ua.bookloom.document.epub;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The element-sibling path an anchor records: the index of each element among its parent's element children, outermost
 * first — the same addressing {@code SkeletonAnchors.nodeAt} resolves, which steps over text and comments.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ElementPaths {

    /**
     * The path from {@code root} down to {@code target} in a jsoup tree.
     *
     * @param root an ancestor of {@code target}
     * @param target the element to address
     * @return the element-sibling indices, outermost step first
     */
    static List<Integer> below(org.jsoup.nodes.Element root, org.jsoup.nodes.Element target) {
        final Deque<Integer> path = new ArrayDeque<>();
        org.jsoup.nodes.Element current = target;
        while (!current.equals(root)) {
            path.addFirst(current.elementSiblingIndex());
            current = Objects.requireNonNull(current.parent(), "parent");
        }
        return List.copyOf(path);
    }

    /**
     * The path from {@code root} down to {@code target} in a JDOM tree.
     *
     * @param root an ancestor of {@code target}
     * @param target the element to address
     * @return the element-sibling indices, outermost step first
     */
    static List<Integer> below(org.jdom2.Element root, org.jdom2.Element target) {
        final Deque<Integer> path = new ArrayDeque<>();
        org.jdom2.Element current = target;
        while (!current.equals(root)) {
            final org.jdom2.Element parent = Objects.requireNonNull(current.getParentElement(), "parent");
            path.addFirst(parent.getChildren().indexOf(current));
            current = parent;
        }
        return List.copyOf(path);
    }
}
