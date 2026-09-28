package ua.bookloom.document.epub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.SecureXml;

/**
 * Parses the OPF package document with JDOM2: the manifest, the spine (establishing reading order), and the
 * {@code dc:language}/title/author metadata (task 2.5, FR-DOC-EPUB-1, FR-DOC-EPUB-2). Supports both the EPUB 2
 * ({@code <spine toc="ncx">}) and EPUB 3 shapes without treating them differently — this change carries nav/NCX
 * verbatim either way (design.md, Non-Goals).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpfParser {

    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    private static final Namespace DC_NS = Namespace.getNamespace("http://purl.org/dc/elements/1.1/");
    private static final String DEFAULT_MEDIA_TYPE = "application/xhtml+xml";

    static ParsedOpf parse(byte[] content, String opfPath) {
        final Document doc = parseXml(content);
        final Element root = doc.getRootElement();
        final Map<String, ManifestItem> manifest = readManifest(root);
        final List<SpineItem> spine = readSpine(root, manifest);
        return new ParsedOpf(
                opfPath,
                readTexts(root, "language"),
                readFirstText(root, "title"),
                readFirstText(root, "creator"),
                List.copyOf(manifest.values()),
                spine,
                doc,
                root.getAttributeValue("version"),
                coverMetaContentOf(root),
                guideReferencesOf(root),
                spineTocOf(root));
    }

    private static Document parseXml(byte[] content) {
        try {
            return SecureXml.builder().build(new ByteArrayInputStream(content));
        } catch (JDOMException | IOException e) {
            throw new CorruptContainerException("Malformed OPF package document", e);
        }
    }

    private static Map<String, ManifestItem> readManifest(Element root) {
        final Element manifestEl = root.getChild("manifest", OPF_NS);
        if (manifestEl == null) {
            throw new CorruptContainerException("OPF has no <manifest>");
        }
        final Map<String, ManifestItem> items = new LinkedHashMap<>();
        for (final Element item : manifestEl.getChildren("item", OPF_NS)) {
            addManifestItem(item, items);
        }
        return items;
    }

    private static void addManifestItem(Element item, Map<String, ManifestItem> items) {
        final String id = item.getAttributeValue("id");
        final String href = item.getAttributeValue("href");
        if (id == null || href == null) {
            throw new CorruptContainerException("OPF <manifest> item is missing id or href");
        }
        final String mediaType = item.getAttributeValue("media-type");
        items.put(
                id,
                new ManifestItem(
                        id,
                        href,
                        mediaType == null ? DEFAULT_MEDIA_TYPE : mediaType,
                        propertiesOf(item.getAttributeValue("properties"))));
    }

    /**
     * Splits a manifest item's {@code properties} attribute on whitespace — {@code "nav scripted"} names two
     * properties, {@code "cover-image"} names one, and a missing or blank attribute names none (task 4.2).
     */
    private static Set<String> propertiesOf(@Nullable String rawProperties) {
        if (rawProperties == null || rawProperties.isBlank()) {
            return Set.of();
        }
        return Set.of(rawProperties.trim().split("\\s+"));
    }

    /**
     * The EPUB 2 {@code <meta name="cover" content="…">} element's {@code content} value — a manifest id or, in
     * some packages, an href directly ({@link EpubInspection}'s second cover rule tries both, task 4.3).
     */
    private static @Nullable String coverMetaContentOf(Element root) {
        final Element metadata = root.getChild("metadata", OPF_NS);
        if (metadata == null) {
            return null;
        }
        for (final Element meta : metadata.getChildren("meta", OPF_NS)) {
            if ("cover".equals(meta.getAttributeValue("name"))) {
                return meta.getAttributeValue("content");
            }
        }
        return null;
    }

    private static List<GuideReference> guideReferencesOf(Element root) {
        final Element guide = root.getChild("guide", OPF_NS);
        if (guide == null) {
            return List.of();
        }
        final List<GuideReference> references = new ArrayList<>();
        for (final Element reference : guide.getChildren("reference", OPF_NS)) {
            addGuideReference(reference, references);
        }
        return references;
    }

    private static void addGuideReference(Element reference, List<GuideReference> references) {
        final String type = reference.getAttributeValue("type");
        final String href = reference.getAttributeValue("href");
        if (type != null && href != null) {
            references.add(new GuideReference(type, href));
        }
    }

    /** The EPUB 2 spine's own {@code toc} attribute — the manifest id of the NCX. */
    private static @Nullable String spineTocOf(Element root) {
        final Element spineEl = root.getChild("spine", OPF_NS);
        return spineEl == null ? null : spineEl.getAttributeValue("toc");
    }

    private static List<SpineItem> readSpine(Element root, Map<String, ManifestItem> manifest) {
        final Element spineEl = root.getChild("spine", OPF_NS);
        if (spineEl == null) {
            throw new CorruptContainerException("OPF has no <spine>");
        }
        final List<Element> itemrefs = spineEl.getChildren("itemref", OPF_NS);
        if (itemrefs.isEmpty()) {
            throw new CorruptContainerException("OPF <spine> has no itemref entries");
        }
        final List<SpineItem> spine = new ArrayList<>(itemrefs.size());
        for (final Element itemref : itemrefs) {
            spine.add(resolveSpineItem(itemref, manifest));
        }
        return spine;
    }

    private static SpineItem resolveSpineItem(Element itemref, Map<String, ManifestItem> manifest) {
        final String idref = itemref.getAttributeValue("idref");
        final ManifestItem item = idref == null ? null : manifest.get(idref);
        if (item == null) {
            throw new CorruptContainerException("Spine itemref references unknown manifest id: " + idref);
        }
        return new SpineItem(idref, item.href(), item.mediaType());
    }

    /**
     * Reads every {@code dc:<dcLocalName>} value declared for the package, checking both the current OPF layout
     * (direct children of {@code <metadata>}) and the older OEBPS-1.2-era layout that nests Dublin Core elements
     * one level deeper inside an unprefixed {@code <dc-metadata>} wrapper. The wrapper itself carries no {@code dc:}
     * prefix, so it inherits the package's default OPF namespace rather than the Dublin Core one — it must be
     * looked up with {@link #OPF_NS}, not {@link #DC_NS}, or it is never found.
     *
     * <p>Where both layouts declare the element, the direct children win outright and the nested wrapper is not
     * consulted at all — for a repeatable element such as {@code dc:language} this means "prefer the direct
     * declaration's whole value set", not a per-value merge, since a book that declares the current layout has
     * no reason to also honour a legacy fallback for the same field.
     *
     * @param dcLocalName the Dublin Core element's local name (e.g. {@code "title"}, {@code "language"})
     * @return every declared value in document order; empty if the source declares none
     */
    private static List<String> readTexts(Element root, String dcLocalName) {
        final Element metadata = root.getChild("metadata", OPF_NS);
        if (metadata == null) {
            return List.of();
        }
        final List<String> direct = textsOf(metadata, dcLocalName);
        if (!direct.isEmpty()) {
            return direct;
        }
        final Element legacyWrapper = metadata.getChild("dc-metadata", OPF_NS);
        return legacyWrapper == null ? List.of() : textsOf(legacyWrapper, dcLocalName);
    }

    private static List<String> textsOf(Element container, String dcLocalName) {
        final List<String> values = new ArrayList<>();
        for (final Element el : container.getChildren(dcLocalName, DC_NS)) {
            values.add(el.getTextTrim());
        }
        return values;
    }

    private static @Nullable String readFirstText(Element root, String dcLocalName) {
        final List<String> texts = readTexts(root, dcLocalName);
        return texts.isEmpty() ? null : texts.get(0);
    }
}
