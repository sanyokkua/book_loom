package ua.bookloom.document.epub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.ElementPaths;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SecureXml;

/**
 * Parses an EPUB's own navigation into a general tree of labelled entries — the EPUB 3 nav document's
 * {@code nav epub:type="toc"} list, or the EPUB 2 NCX's {@code navMap} where there is no nav document (task 4.4).
 *
 * <p>Deliberately answers a format-agnostic entry tree rather than anything shaped around
 * {@link EpubInspection#structure}: task 6.3 reuses this exact parser to produce translatable navigation-label
 * segments, a different consumer with different needs from the same entries.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NavigationParser {

    private static final String NAV_PROPERTY = "nav";
    private static final String NCX_MEDIA_TYPE = "application/x-dtbncx+xml";
    private static final String TOC_TYPE = "toc";
    private static final String EPUB_TYPE_ATTRIBUTE = "epub:type";
    private static final String NAV_TAG = "nav";
    private static final String OL_TAG = "ol";
    private static final String LI_TAG = "li";
    private static final String A_TAG = "a";
    private static final String HREF_ATTRIBUTE = "href";
    private static final String NAV_MAP_ELEMENT = "navMap";
    private static final String NAV_POINT_ELEMENT = "navPoint";
    private static final String NAV_LABEL_ELEMENT = "navLabel";
    private static final String TEXT_ELEMENT = "text";
    private static final String CONTENT_ELEMENT = "content";
    private static final String SRC_ATTRIBUTE = "src";
    private static final String ID_ATTRIBUTE = "id";

    /** Which navigation source, if any, {@link #parse} read the entries from. */
    enum Source {
        NAV,
        NCX,
        NONE
    }

    /**
     * One navigation entry, general enough for both the nav document and the NCX, and for any consumer (task 4.4,
     * task 6.3).
     *
     * @param label the entry's link text, or {@code navLabel/text} for an NCX entry
     * @param href the entry's link target, resolved against the navigation file's own directory, with the
     *     fragment split off
     * @param fragment the link target's fragment, or {@code null} when it names none
     * @param entryPath this entry's 1-based position among its siblings, dotted with its ancestors' (e.g. {@code
     *     "1.3.2"})
     * @param navPointId the NCX entry element's own {@code id} attribute, or {@code null} for a nav-document entry
     * @param anchorPath the element-sibling path to the element holding the label — the link below the navigation
     *     document's {@code body}, the {@code navLabel/text} below the NCX's root — so a consumer can write to it
     * @param children this entry's nested entries, in document order
     */
    record NavEntry(
            String label,
            String href,
            @Nullable String fragment,
            String entryPath,
            @Nullable String navPointId,
            List<Integer> anchorPath,
            List<NavEntry> children) {

        NavEntry {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(href, "href");
            Objects.requireNonNull(entryPath, "entryPath");
            Objects.requireNonNull(anchorPath, "anchorPath");
            Objects.requireNonNull(children, "children");
            anchorPath = List.copyOf(anchorPath);
            children = List.copyOf(children);
        }
    }

    /**
     * The outcome of looking for a book's navigation.
     *
     * @param source which navigation source was read, if any
     * @param entries the top-level entries, in document order; empty when {@code source} is {@link Source#NONE}
     */
    record Result(Source source, List<NavEntry> entries) {

        Result {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
        }
    }

    /**
     * Finds and parses a book's own navigation: the nav document named by the manifest's {@code nav} property,
     * else the NCX named by the spine's {@code toc} attribute or, failing that, by its media type.
     *
     * @param opf the book's parsed OPF
     * @param byName every archive entry, keyed by its archive-absolute path
     * @return the navigation entries, or {@link Source#NONE} with no entries when the book declares neither
     */
    static Result parse(ParsedOpf opf, Map<String, RawEntry> byName) {
        Objects.requireNonNull(opf, "opf");
        Objects.requireNonNull(byName, "byName");
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        final RawEntry navEntry = findNavEntry(opf, byName, opfDir);
        if (navEntry != null) {
            return new Result(Source.NAV, parseNav(navEntry));
        }
        final RawEntry ncxEntry = findNcxEntry(opf, byName, opfDir);
        if (ncxEntry != null) {
            return new Result(Source.NCX, parseNcx(ncxEntry));
        }
        return new Result(Source.NONE, List.of());
    }

    static @Nullable RawEntry findNavEntry(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.properties().contains(NAV_PROPERTY)) {
                return byName.get(OpfPaths.resolve(opfDir, item.href()));
            }
        }
        return null;
    }

    static @Nullable RawEntry findNcxEntry(ParsedOpf opf, Map<String, RawEntry> byName, String opfDir) {
        final String tocId = opf.spineToc();
        for (final ManifestItem item : opf.manifestItems()) {
            if (item.id().equals(tocId) || NCX_MEDIA_TYPE.equals(item.mediaType())) {
                final RawEntry entry = byName.get(OpfPaths.resolve(opfDir, item.href()));
                if (entry != null) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** {@code navEntry.name()} is already the navigation document's own archive-absolute path. */
    private static List<NavEntry> parseNav(RawEntry navEntry) {
        return parseNav(XhtmlParser.parse(navEntry.content(), navEntry.name()), navEntry.name());
    }

    /**
     * Reads the entries of an already parsed navigation document, so a caller that keeps the tree to write into
     * does not parse it twice.
     *
     * @param doc the parsed navigation document
     * @param navName the document's archive-absolute path, which link targets resolve against
     * @return the top-level entries; empty when the document has no {@code toc} list
     */
    static List<NavEntry> parseNav(org.jsoup.nodes.Document doc, String navName) {
        final org.jsoup.nodes.Element tocNav = findTocNav(doc);
        if (tocNav == null) {
            return List.of();
        }
        final org.jsoup.nodes.Element ol = firstChildTag(tocNav, OL_TAG);
        if (ol == null) {
            return List.of();
        }
        return parseOl(ol, doc.body(), OpfPaths.parentOf(navName), "");
    }

    private static org.jsoup.nodes.@Nullable Element findTocNav(org.jsoup.nodes.Document doc) {
        for (final org.jsoup.nodes.Element nav : doc.select(NAV_TAG)) {
            if (TOC_TYPE.equals(nav.attr(EPUB_TYPE_ATTRIBUTE))) {
                return nav;
            }
        }
        return null;
    }

    private static org.jsoup.nodes.@Nullable Element firstChildTag(org.jsoup.nodes.Element parent, String tag) {
        for (final org.jsoup.nodes.Element child : parent.children()) {
            if (tag.equals(child.tagName())) {
                return child;
            }
        }
        return null;
    }

    private static List<NavEntry> parseOl(
            org.jsoup.nodes.Element ol, org.jsoup.nodes.Element body, String navDir, String parentPath) {
        final List<NavEntry> entries = new ArrayList<>();
        int index = 1;
        for (final org.jsoup.nodes.Element li : ol.children()) {
            if (LI_TAG.equals(li.tagName())) {
                addNavEntry(li, body, navDir, parentPath, index, entries);
                index++;
            }
        }
        return entries;
    }

    private static void addNavEntry(
            org.jsoup.nodes.Element li,
            org.jsoup.nodes.Element body,
            String navDir,
            String parentPath,
            int index,
            List<NavEntry> entries) {
        final org.jsoup.nodes.Element anchor = firstChildTag(li, A_TAG);
        if (anchor == null) {
            return;
        }
        final String entryPath = childPath(parentPath, index);
        final HrefSplit split = HrefSplit.of(anchor.attr(HREF_ATTRIBUTE));
        final org.jsoup.nodes.Element nestedOl = firstChildTag(li, OL_TAG);
        final List<NavEntry> children = nestedOl == null ? List.of() : parseOl(nestedOl, body, navDir, entryPath);
        entries.add(new NavEntry(
                anchor.text(),
                OpfPaths.resolve(navDir, split.path()),
                split.fragment(),
                entryPath,
                null,
                ElementPaths.below(body, anchor),
                children));
    }

    /** {@code ncxEntry.name()} is already the NCX's own archive-absolute path. */
    private static List<NavEntry> parseNcx(RawEntry ncxEntry) {
        return parseNcx(parseXml(ncxEntry.content()), ncxEntry.name());
    }

    /**
     * Reads the entries of an already parsed NCX, so a caller that keeps the tree to write into does not parse it
     * twice.
     *
     * @param tree the parsed NCX
     * @param ncxName the NCX's archive-absolute path, which link targets resolve against
     * @return the top-level entries; empty when the NCX has no {@code navMap}
     */
    static List<NavEntry> parseNcx(org.jdom2.Document tree, String ncxName) {
        final Element navMap = childByLocalName(tree.getRootElement(), NAV_MAP_ELEMENT);
        if (navMap == null) {
            return List.of();
        }
        return parseNavPoints(navMap.getChildren(), tree.getRootElement(), OpfPaths.parentOf(ncxName), "");
    }

    /**
     * Parses NCX bytes strictly.
     *
     * @param content the NCX file's bytes
     * @return the parsed tree
     * @throws CorruptContainerException if the bytes are not well-formed XML
     */
    static org.jdom2.Document parseXml(byte[] content) {
        try {
            return SecureXml.builder().build(new ByteArrayInputStream(content));
        } catch (JDOMException | IOException e) {
            throw new CorruptContainerException("Malformed EPUB navigation document", e);
        }
    }

    private static List<NavEntry> parseNavPoints(
            List<Element> siblings, Element root, String ncxDir, String parentPath) {
        final List<NavEntry> entries = new ArrayList<>();
        int index = 1;
        for (final Element element : siblings) {
            if (NAV_POINT_ELEMENT.equals(element.getName())) {
                entries.add(navPointEntry(element, root, ncxDir, parentPath, index));
                index++;
            }
        }
        return entries;
    }

    private static NavEntry navPointEntry(Element navPoint, Element root, String ncxDir, String parentPath, int index) {
        final String entryPath = childPath(parentPath, index);
        final Element text = navPointText(navPoint);
        final HrefSplit split = HrefSplit.of(navPointSrc(navPoint));
        final List<NavEntry> children = parseNavPoints(navPoint.getChildren(), root, ncxDir, entryPath);
        return new NavEntry(
                text == null ? "" : text.getTextNormalize(),
                OpfPaths.resolve(ncxDir, split.path()),
                split.fragment(),
                entryPath,
                navPoint.getAttributeValue(ID_ATTRIBUTE),
                text == null ? List.of() : ElementPaths.below(root, text),
                children);
    }

    private static @Nullable Element navPointText(Element navPoint) {
        return childByLocalName(childByLocalName(navPoint, NAV_LABEL_ELEMENT), TEXT_ELEMENT);
    }

    private static String navPointSrc(Element navPoint) {
        final Element content = childByLocalName(navPoint, CONTENT_ELEMENT);
        final String src = content == null ? null : content.getAttributeValue(SRC_ATTRIBUTE);
        return src == null ? "" : src;
    }

    /** Matches an NCX child by local name, ignoring namespace — real books declare the NCX namespace inconsistently. */
    private static @Nullable Element childByLocalName(@Nullable Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (final Element child : parent.getChildren()) {
            if (localName.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private static String childPath(String parentPath, int index) {
        return parentPath.isEmpty() ? String.valueOf(index) : parentPath + "." + index;
    }

    /** A link target split into its path and its fragment, if any. */
    private record HrefSplit(String path, @Nullable String fragment) {

        static HrefSplit of(String href) {
            final int hash = href.indexOf('#');
            return hash < 0
                    ? new HrefSplit(href, null)
                    : new HrefSplit(href.substring(0, hash), href.substring(hash + 1));
        }
    }
}
