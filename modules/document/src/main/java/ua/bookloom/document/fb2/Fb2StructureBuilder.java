package ua.bookloom.document.fb2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Element;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.AttributeAnchor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.Unit;

/**
 * Builds an FB2 book's structure tree from its own bodies (task 4.4): each body's nested {@code section} elements,
 * content of the main body outside every section as an untitled leading node, and each further body (notes,
 * comments, …) as a single top-level node.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Fb2StructureBuilder {

    private static final String BODY_ELEMENT = "body";
    private static final String SECTION_ELEMENT = "section";
    private static final String NAME_ATTRIBUTE = "name";

    /**
     * Builds the top-level structure nodes for every body of {@code parsed}, in body order.
     *
     * @param parsed the open FB2's parsed state
     * @param document the same document, whose units carry the real segments to count
     * @return the top-level nodes, in body order
     */
    static List<StructureNode> build(ParsedFb2 parsed, Document document) {
        Objects.requireNonNull(parsed, "parsed");
        Objects.requireNonNull(document, "document");
        final Map<String, Unit> unitsById = unitsById(document);
        final List<Element> bodies = childrenNamed(parsed.document().getRootElement(), BODY_ELEMENT);
        final List<StructureNode> nodes = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) {
            addBodyNodes(bodies.get(i), unitsById.get(parsed.sourceName() + "#" + i), nodes);
        }
        return nodes;
    }

    private static void addBodyNodes(Element body, @Nullable Unit unit, List<StructureNode> nodes) {
        if (unit == null) {
            return;
        }
        final String name = body.getAttributeValue(NAME_ATTRIBUTE);
        if (name == null) {
            nodes.addAll(mainBodyNodes(body, unit));
        } else {
            nodes.add(namedBodyNode(body, unit, name));
        }
    }

    private static Map<String, Unit> unitsById(Document document) {
        final Map<String, Unit> byId = new LinkedHashMap<>();
        for (final Unit unit : document.units()) {
            byId.put(unit.id(), unit);
        }
        return byId;
    }

    private static List<StructureNode> mainBodyNodes(Element body, Unit unit) {
        final IdentityHashMap<Element, Integer> sectionCounts = new IdentityHashMap<>();
        int leadingCount = 0;
        for (final Segment segment : unit.segments()) {
            final Element leaf = resolveLeaf(body, segment.anchor());
            final List<Element> chain = sectionChainOf(leaf, body);
            if (chain.isEmpty()) {
                leadingCount++;
            } else {
                for (final Element section : chain) {
                    sectionCounts.merge(section, 1, Integer::sum);
                }
            }
        }
        final List<StructureNode> nodes = new ArrayList<>();
        if (leadingCount > 0) {
            nodes.add(new StructureNode("", null, leadingCount, List.of()));
        }
        for (final Element top : childrenNamed(body, SECTION_ELEMENT)) {
            nodes.add(buildSectionNode(top, sectionCounts));
        }
        return nodes;
    }

    private static StructureNode buildSectionNode(Element section, IdentityHashMap<Element, Integer> counts) {
        final List<StructureNode> children = childrenNamed(section, SECTION_ELEMENT).stream()
                .map(child -> buildSectionNode(child, counts))
                .toList();
        return new StructureNode(titleTextOf(section), null, counts.getOrDefault(section, 0), children);
    }

    private static StructureNode namedBodyNode(Element body, Unit unit, String name) {
        final String title = titleTextOf(body);
        return new StructureNode(
                title.isEmpty() ? name : title, null, unit.segments().size(), List.of());
    }

    /**
     * A {@code title} element's text: FB2 always wraps a title's lines in {@code p} children, and JDOM's own
     * {@code getTextNormalize} reads only an element's <em>direct</em> text — never a descendant's — so its
     * {@code p} children's text is joined instead; a title with no {@code p} children falls back to its own
     * direct text.
     */
    private static String titleTextOf(Element element) {
        final Element title = Fb2Metadata.childOf(element, "title");
        if (title == null) {
            return "";
        }
        final List<Element> paragraphs = childrenNamed(title, "p");
        if (paragraphs.isEmpty()) {
            return title.getTextNormalize();
        }
        final StringBuilder joined = new StringBuilder();
        for (final Element paragraph : paragraphs) {
            if (!joined.isEmpty()) {
                joined.append(' ');
            }
            joined.append(paragraph.getTextNormalize());
        }
        return joined.toString();
    }

    /** Resolves a segment's leaf element the same way {@code BlockSegmentWalker} indexed it: by element ordinal. */
    private static Element resolveLeaf(Element root, SkeletonAnchor anchor) {
        return switch (anchor) {
            case NodeAnchor nodeAnchor -> resolvePath(root, nodeAnchor.nodePath());
            case ByteSpanAnchor ignored ->
                throw new IllegalStateException("An FB2 segment must be anchored by a node path");
            case AttributeAnchor ignored ->
                throw new IllegalStateException("An FB2 body segment must be anchored by a node path");
        };
    }

    private static Element resolvePath(Element root, List<Integer> path) {
        Element current = root;
        for (final int index : path) {
            current = current.getChildren().get(index);
        }
        return current;
    }

    /** The chain of {@code section} ancestors enclosing {@code leaf}, outermost first; empty outside every section. */
    // Deliberate reference equality: this stops the walk at the exact `body` instance the tree owns, not at
    // some other element that merely equals it in content.
    @SuppressWarnings("ReferenceEquality")
    private static List<Element> sectionChainOf(Element leaf, Element body) {
        final List<Element> chain = new ArrayList<>();
        Element current = leaf.getParentElement();
        while (current != null && current != body) {
            if (SECTION_ELEMENT.equals(current.getName())) {
                chain.add(current);
            }
            current = current.getParentElement();
        }
        Collections.reverse(chain);
        return chain;
    }

    private static List<Element> childrenNamed(Element parent, String localName) {
        final List<Element> children = new ArrayList<>();
        for (final Element child : parent.getChildren()) {
            if (localName.equals(child.getName())) {
                children.add(child);
            }
        }
        return children;
    }
}
