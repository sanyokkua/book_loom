package ua.bookloom.document.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One node of a parsed tree, adapted so that the structural block rule (ADR-0027) and run splitting (ADR-0025)
 * are implemented exactly once and serve both tree-shaped formats — EPUB through jsoup and FB2 through JDOM2.
 *
 * <p><strong>Why an adapter rather than two walkers.</strong> ADR-0027 replaced a tag whitelist that had drifted
 * out of agreement with real books; a rule written twice drifts the same way, silently, and the corpus survey
 * showed that a segmentation defect is invisible to every fidelity assertion. The adapter is deliberately
 * minimal — the walker needs to ask only what a node owns, what it contains, and how it spells itself — so the
 * cost of a second implementation is a few dozen lines, not a parallel hierarchy.
 */
public interface TreeNode {

    /**
     * What kind of node this is, tested before its name or its children — the classification order the masking
     * walk ({@link TreeMasker}) depends on (ADR-0031, design.md D2). Both adapters return {@code List.of()} from
     * {@link #childNodes()} for every non-element, so "has no children" is true of a comment and a text node too;
     * only {@link #type()} tells them apart from an empty element.
     *
     * @return this node's {@link TreeNodeType}; never null
     */
    TreeNodeType type();

    /**
     * This node's element name, lower-cased.
     *
     * @return the lower-cased element name, or {@code null} when this node is not an element (character data, a
     *     comment, a processing instruction)
     */
    @Nullable
    String tagName();

    /**
     * The character data this node carries in its own right — never a descendant's.
     *
     * @return this node's own character data, or the empty string when it is an element or carries none; never
     *     null
     */
    String ownText();

    /**
     * This node's children in document order.
     *
     * @return this node's children in document order; never null, and empty for a node that cannot have children
     */
    List<TreeNode> childNodes();

    /**
     * This node serialized as markup, as it stands in the tree right now.
     *
     * @return this node's markup, inline descendants included; never null
     */
    String markup();

    /**
     * Replaces the child nodes in {@code [fromInclusive, toExclusive)} with the nodes parsed from
     * {@code replacementMarkup}, leaving every other child — including the line-break elements that bound a run —
     * exactly where it was.
     *
     * <p>The range is computed by shared code from run boundaries; this method exists on the adapter only because
     * parsing a translated fragment back into markup is the one part that genuinely differs between jsoup and
     * JDOM2 (namespace scope in particular).
     *
     * @param fromInclusive the first child index to replace; never negative
     * @param toExclusive one past the last child index to replace; never below {@code fromInclusive}
     * @param replacementMarkup the translated inner content, parsed as markup rather than inserted as literal
     *     text so that restored inline elements stay elements
     */
    void replaceChildren(int fromInclusive, int toExclusive, String replacementMarkup);

    /**
     * This element's composed opening tag — as the underlying parser records it, not necessarily as the source
     * spelled it (attribute name case and quoting are normalized by the parser before this is ever called).
     *
     * @return the opening tag, e.g. {@code "<a href=\"ch2.xhtml#top\" id=\"x1\">"}; never null
     * @throws IllegalStateException if this node is not an element
     */
    String openMarkup();

    /**
     * This element's closing tag.
     *
     * @return the closing tag, e.g. {@code "</a>"}; never null
     * @throws IllegalStateException if this node is not an element
     */
    String closeMarkup();
}
