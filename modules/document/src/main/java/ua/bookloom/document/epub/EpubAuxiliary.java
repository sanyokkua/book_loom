package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jsoup.nodes.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.AltImages;
import ua.bookloom.document.model.AuxiliarySlots;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.TreeDialect;
import ua.bookloom.document.model.TreeNode;

/**
 * Finds an EPUB's auxiliary text slots (design.md D12): the package's first {@code dc:title}, each
 * {@code dc:creator} and {@code dc:description}, the navigation document's and NCX's labels, each content document's
 * {@code <head><title>} and each image's {@code alt}. Every slot is named by what it is — never by a running count —
 * so a slot added by a later task cannot renumber another.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubAuxiliary {

    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String TITLE = "title";

    /**
     * Collects every slot the package, the navigation resources and the content documents offer, in the order title,
     * creators, descriptions, navigation and NCX labels, page titles, image descriptions.
     *
     * @param opf the parsed package
     * @param byName every archive entry by its path
     * @param bodyUnits the spine units read, in order
     * @param trees each spine unit's parsed tree by its skeleton handle id
     * @return the auxiliary segments, the table that writes them back and the navigation trees it points into
     */
    static Collected collect(
            ParsedOpf opf, Map<String, RawEntry> byName, List<Unit> bodyUnits, Map<String, Document> trees) {
        final AuxiliarySlots.Builder builder = AuxiliarySlots.builder();
        final Element packageElement = opf.jdomDocument().getRootElement();
        final TreeNode packageNode = Jdom2TreeNode.of(packageElement);
        addPackageSlots(builder, opf.opfPath(), packageElement, packageNode);
        final NavigationResources navigation = EpubNavigationAuxiliary.collect(
                opf, byName, bodyUnits.stream().map(Unit::href).collect(Collectors.toSet()), builder);
        for (final Unit unit : bodyUnits) {
            addHeadTitle(builder, unit, spineTree(trees, unit));
        }
        for (final Unit unit : bodyUnits) {
            final TreeNode body = JsoupTreeNode.of(spineTree(trees, unit).body());
            AltImages.scan(body, unit.segments())
                    .forEach(image -> builder.addAlt(unit.href(), unit.href(), body, image));
        }
        return new Collected(builder.build(), navigation);
    }

    /**
     * What the EPUB reader gets back.
     *
     * @param slots the auxiliary segments and the table that writes them back
     * @param navigation the navigation document and NCX trees those slots point into
     */
    record Collected(AuxiliarySlots.Collected slots, NavigationResources navigation) {}

    private static Document spineTree(Map<String, Document> trees, Unit unit) {
        return Objects.requireNonNull(trees.get(unit.skeleton().opaqueId()), "spine tree");
    }

    private static void addPackageSlots(
            AuxiliarySlots.Builder builder, String opfPath, Element packageElement, TreeNode packageNode) {
        final List<Element> titles = descendantsNamed(packageElement, TITLE);
        if (!titles.isEmpty()) {
            builder.addText(
                    opfPath,
                    "aux:title",
                    SegmentKind.METADATA_TITLE,
                    packageNode,
                    ElementPaths.below(packageElement, titles.get(0)),
                    TreeDialect.FICTION_BOOK);
        }
        addAll(builder, opfPath, packageElement, packageNode, "creator", SegmentKind.METADATA_AUTHOR);
        addAll(builder, opfPath, packageElement, packageNode, "description", SegmentKind.METADATA_DESCRIPTION);
    }

    private static void addAll(
            AuxiliarySlots.Builder builder,
            String opfPath,
            Element packageElement,
            TreeNode packageNode,
            String name,
            SegmentKind kind) {
        final List<Element> elements = descendantsNamed(packageElement, name);
        for (int i = 0; i < elements.size(); i++) {
            builder.addText(
                    opfPath,
                    "aux:" + name + ":" + i,
                    kind,
                    packageNode,
                    ElementPaths.below(packageElement, elements.get(i)),
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
                unit.href(),
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
}
