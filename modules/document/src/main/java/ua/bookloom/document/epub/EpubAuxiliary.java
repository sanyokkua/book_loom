package ua.bookloom.document.epub;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jsoup.nodes.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.AuxiliarySlots;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.TreeDialect;
import ua.bookloom.document.model.TreeNode;

/**
 * Finds an EPUB's auxiliary text slots (design.md D12): the package's first {@code dc:title}, each
 * {@code dc:creator} and {@code dc:description}, and each content document's {@code <head><title>}. Every slot is
 * named by what it is — never by a running count — so a slot added by a later task cannot renumber another.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubAuxiliary {

    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String TITLE = "title";

    /**
     * Collects every slot the package and the content documents offer, in the order title, creators, descriptions,
     * page titles.
     *
     * @param opf the parsed package document
     * @param bodyUnits the spine units read, in order
     * @param trees each spine unit's parsed tree by its skeleton handle id
     * @return the auxiliary segments and the table that writes them back
     */
    static AuxiliarySlots.Collected collect(org.jdom2.Document opf, List<Unit> bodyUnits, Map<String, Document> trees) {
        final AuxiliarySlots.Builder builder = AuxiliarySlots.builder();
        final Element packageElement = opf.getRootElement();
        final TreeNode packageNode = Jdom2TreeNode.of(packageElement);
        addPackageSlots(builder, packageElement, packageNode);
        for (final Unit unit : bodyUnits) {
            addHeadTitle(
                    builder,
                    unit,
                    Objects.requireNonNull(trees.get(unit.skeleton().opaqueId()), "spine tree"));
        }
        return builder.build();
    }

    private static void addPackageSlots(AuxiliarySlots.Builder builder, Element packageElement, TreeNode packageNode) {
        final List<Element> titles = descendantsNamed(packageElement, TITLE);
        if (!titles.isEmpty()) {
            builder.addText(
                    "aux:title",
                    SegmentKind.METADATA_TITLE,
                    packageNode,
                    pathBelow(packageElement, titles.get(0)),
                    TreeDialect.FICTION_BOOK);
        }
        addAll(builder, packageElement, packageNode, "creator", SegmentKind.METADATA_AUTHOR);
        addAll(builder, packageElement, packageNode, "description", SegmentKind.METADATA_DESCRIPTION);
    }

    private static void addAll(
            AuxiliarySlots.Builder builder,
            Element packageElement,
            TreeNode packageNode,
            String name,
            SegmentKind kind) {
        final List<Element> elements = descendantsNamed(packageElement, name);
        for (int i = 0; i < elements.size(); i++) {
            builder.addText(
                    "aux:" + name + ":" + i,
                    kind,
                    packageNode,
                    pathBelow(packageElement, elements.get(i)),
                    TreeDialect.FICTION_BOOK);
        }
    }

    private static void addHeadTitle(AuxiliarySlots.Builder builder, Unit unit, Document tree) {
        final org.jsoup.nodes.Element head = tree.head();
        final org.jsoup.nodes.Element title = head.children().stream()
                .filter(child -> TITLE.equals(child.normalName()))
                .findFirst()
                .orElse(null);
        if (title == null) {
            log.debug("no head title in {}", unit.href());
            return;
        }
        builder.addText(
                "aux:head-title:" + unit.href(),
                SegmentKind.TITLE,
                JsoupTreeNode.of(head),
                List.of(title.elementSiblingIndex()),
                TreeDialect.XHTML);
    }

    private static List<Element> descendantsNamed(Element packageElement, String name) {
        final List<Element> found = new ArrayList<>();
        packageElement.getDescendants(Filters.element(name, DC_NS)).forEach(found::add);
        return found;
    }

    /** The element-sibling path from {@code root} down to {@code target}, outermost step first. */
    private static List<Integer> pathBelow(Element root, Element target) {
        final Deque<Integer> path = new ArrayDeque<>();
        Element current = target;
        while (!current.equals(root)) {
            final Element parent = Objects.requireNonNull(current.getParentElement(), "parent");
            path.addFirst(parent.getChildren().indexOf(current));
            current = parent;
        }
        return List.copyOf(path);
    }
}
