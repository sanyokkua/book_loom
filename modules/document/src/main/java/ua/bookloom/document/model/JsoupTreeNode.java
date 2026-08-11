package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TreeNode} adapter over a jsoup node — the EPUB side of the shared structural walker.
 */
public final class JsoupTreeNode implements TreeNode {

    private final Node node;

    private JsoupTreeNode(Node node) {
        this.node = Objects.requireNonNull(node, "node");
    }

    /**
     * Adapts a jsoup node for the shared walker.
     *
     * @param node the jsoup node to adapt
     * @return a {@link TreeNode} view delegating to {@code node}; mutations through it change the real tree
     */
    public static TreeNode of(Node node) {
        return new JsoupTreeNode(node);
    }

    @Override
    public @Nullable String tagName() {
        return node instanceof Element element ? element.tagName().toLowerCase(Locale.ROOT) : null;
    }

    @Override
    public String ownText() {
        return node instanceof TextNode textNode ? textNode.getWholeText() : "";
    }

    @Override
    public List<TreeNode> childNodes() {
        final List<Node> children = node.childNodes();
        final List<TreeNode> adapted = new ArrayList<>(children.size());
        for (final Node child : children) {
            adapted.add(new JsoupTreeNode(child));
        }
        return adapted;
    }

    @Override
    public String markup() {
        return node.outerHtml();
    }

    /**
     * Parses {@code replacementMarkup} as an HTML body fragment and splices the resulting nodes in place of the
     * given child range. The body-fragment parser is used rather than a literal text node precisely because a
     * translated block legitimately contains inline markup — writing it as text is what escaped it into
     * escaped-entity text in the shipped implementation (ADR-0025).
     */
    @Override
    public void replaceChildren(int fromInclusive, int toExclusive, String replacementMarkup) {
        Objects.requireNonNull(replacementMarkup, "replacementMarkup");
        if (!(node instanceof Element element)) {
            throw new IllegalStateException("Only an element has children to replace");
        }
        final List<Node> doomed = new ArrayList<>(element.childNodes().subList(fromInclusive, toExclusive));
        final List<Node> replacement = new ArrayList<>(Jsoup.parseBodyFragment(replacementMarkup, node.baseUri())
                .body()
                .childNodes());
        element.insertChildren(fromInclusive, replacement);
        for (final Node old : doomed) {
            old.remove();
        }
    }
}
