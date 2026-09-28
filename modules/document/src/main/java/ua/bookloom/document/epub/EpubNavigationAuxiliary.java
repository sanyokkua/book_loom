package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.epub.NavigationParser.NavEntry;
import ua.bookloom.document.model.AuxiliarySlots;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.Jdom2TreeNode;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.TreeDialect;
import ua.bookloom.document.model.TreeNode;

/**
 * Reads the navigation document's link texts and the NCX's labels as auxiliary slots, reusing task 4.4's
 * {@link NavigationParser} for the entries. An NCX label that reads exactly like a navigation label is no segment of
 * its own: it is recorded as written from that navigation segment, so a reviewer's edit reaches both and the model is
 * asked once.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubNavigationAuxiliary {

    /**
     * Adds the navigation and NCX slots to {@code builder}.
     *
     * @param opf the parsed package
     * @param byName every archive entry by its path
     * @param spineHrefs the archive paths of the spine documents, which are body units already
     * @param builder the slot collection being built
     * @return the live trees the writer needs to write those slots back
     */
    static NavigationResources collect(
            ParsedOpf opf, Map<String, RawEntry> byName, Set<String> spineHrefs, AuxiliarySlots.Builder builder) {
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        final RawEntry navEntry = NavigationParser.findNavEntry(opf, byName, opfDir);
        final RawEntry ncxEntry = NavigationParser.findNcxEntry(opf, byName, opfDir);
        final Map<String, String> navSegmentByLabel = new HashMap<>();
        org.jsoup.nodes.Document navTree = null;
        String navPath = null;
        if (navEntry != null && !spineHrefs.contains(navEntry.name())) {
            navPath = navEntry.name();
            navTree = XhtmlParser.parse(navEntry.content(), navPath);
            addNavigationLabels(builder, navPath, navTree, navSegmentByLabel);
        } else if (navEntry != null) {
            log.debug("navigation document {} is in the spine: its links are body segments", navEntry.name());
        }
        final org.jdom2.Document ncxTree = ncxEntry == null ? null : addNcxLabels(builder, ncxEntry, navSegmentByLabel);
        return new NavigationResources(
                navPath, navTree, ncxEntry != null && ncxTree != null ? ncxEntry.name() : null, ncxTree);
    }

    private static void addNavigationLabels(
            AuxiliarySlots.Builder builder,
            String navPath,
            org.jsoup.nodes.Document tree,
            Map<String, String> navSegmentByLabel) {
        final TreeNode root = JsoupTreeNode.of(tree.body());
        for (final NavEntry entry : flatten(NavigationParser.parseNav(tree, navPath))) {
            final String id = "aux:nav:" + entry.entryPath();
            builder.addMarkup(navPath, id, SegmentKind.NAV_LABEL, root, entry.anchorPath(), TreeDialect.XHTML);
            if (builder.isProduced(id)) {
                navSegmentByLabel.putIfAbsent(entry.label(), id);
            }
        }
        log.debug("navigation document {} label segments={}", navPath, navSegmentByLabel.size());
    }

    private static org.jdom2.@Nullable Document addNcxLabels(
            AuxiliarySlots.Builder builder, RawEntry ncxEntry, Map<String, String> navSegmentByLabel) {
        final org.jdom2.Document tree;
        try {
            tree = NavigationParser.parseXml(ncxEntry.content());
        } catch (CorruptContainerException e) {
            log.warn("NCX {} is not well-formed; its labels are left as they are", ncxEntry.name());
            log.debug("NCX read failure", e);
            return null;
        }
        final TreeNode root = Jdom2TreeNode.of(tree.getRootElement());
        final Set<String> written = new HashSet<>();
        for (final NavEntry entry : flatten(NavigationParser.parseNcx(tree, ncxEntry.name()))) {
            if (!entry.label().isBlank() && !entry.anchorPath().isEmpty()) {
                addNcxLabel(builder, ncxEntry.name(), root, entry, navSegmentByLabel, written);
            }
        }
        log.debug("NCX {} own label segments={}", ncxEntry.name(), written.size());
        return tree;
    }

    private static void addNcxLabel(
            AuxiliarySlots.Builder builder,
            String ncxPath,
            TreeNode root,
            NavEntry entry,
            Map<String, String> navSegmentByLabel,
            Set<String> ownSegmentIds) {
        final String navSegmentId = navSegmentByLabel.get(entry.label());
        if (navSegmentId != null) {
            builder.addAlias(ncxPath, root, entry.anchorPath(), navSegmentId);
            log.debug("NCX label {} is written from {}", entry.navPointId(), navSegmentId);
            return;
        }
        final String id = "aux:ncx:" + (entry.navPointId() != null ? entry.navPointId() : entry.entryPath());
        builder.addText(ncxPath, id, SegmentKind.NAV_LABEL, root, entry.anchorPath(), TreeDialect.FICTION_BOOK);
        ownSegmentIds.add(id);
    }

    private static List<NavEntry> flatten(List<NavEntry> entries) {
        final List<NavEntry> flat = new ArrayList<>();
        for (final NavEntry entry : entries) {
            flat.add(entry);
            flat.addAll(flatten(entry.children()));
        }
        return flat;
    }
}
