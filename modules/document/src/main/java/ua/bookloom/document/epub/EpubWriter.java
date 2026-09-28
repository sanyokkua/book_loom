package ua.bookloom.document.epub;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SkeletonAnchors;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Reassembles an EPUB previously opened by {@link EpubReader} and repackages it — the write side of seam F1 for
 * EPUB (task group 3). Writes accepted segments' target text back into the exact skeleton node each was parsed
 * from ({@link ua.bookloom.document.model.SkeletonAnchors#writeBackAll}), sets the target language, and re-zips with
 * {@code mimetype} first and STORED (FR-DOC-EPUB-3, FR-DOC-06, FR-DOC-EPUB-6).
 *
 * <p>Throws rather than returning a {@code Result}, matching {@link EpubReader}: a later change's
 * {@code DocumentService} is the port boundary that catches these and builds the typed envelope.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EpubWriter {

    private static final String MIMETYPE_ENTRY_NAME = "mimetype";
    private static final String MIMETYPE_CONTENT = "application/epub+zip";
    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    // A prefixed namespace, not Namespace.getNamespace(uri) alone (which chooses no-prefix/default): an appended
    // <language> element must serialize as <dc:language>, not <language xmlns="…">, however Namespace.equals
    // (URI-only) still matches an existing dc:-prefixed element when reading (task 4.6, the reported bug).
    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String LANGUAGE_ELEMENT_NAME = "language";
    private static final String LEGACY_DC_METADATA_ELEMENT_NAME = "dc-metadata";
    private static final String DCTERMS_LANGUAGE_META = "dcterms:language";

    private final OpenEpubRegistry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination} in EPUB, setting the target language, with
     * no source language — the writer falls back to the package's own declared language (task 4.6).
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage the language to declare in the written book (for example an ISO 639-1 code)
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by {@link EpubReader#read}
     * @throws CorruptContainerException if the OPF has no {@code <metadata>} element to set the language in
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        return write(document, destination, null, targetLanguage);
    }

    /**
     * Reassembles {@code document} and writes it to {@code destination} in EPUB, rewriting every language
     * attribute that carries {@code sourceLanguage} — or, when {@code sourceLanguage} is {@code null}, the
     * package's own first declared {@code dc:language} — to {@code targetLanguage} (task 4.6, design.md D14 §1).
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param sourceLanguage the language the run translated from, or {@code null} to use the package's declared
     *     language instead
     * @param targetLanguage the language to declare in the written book (for example an ISO 639-1 code)
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by {@link EpubReader#read}
     * @throws CorruptContainerException if the OPF has no {@code <metadata>} element to set the language in
     */
    public Path write(Document document, Path destination, @Nullable String sourceLanguage, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final ParsedEpub parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        writeSegmentsBack(document, parsed);
        rewriteLanguages(parsed, document, sourceLanguage, targetLanguage);
        repackage(parsed, document, destination);
        return destination;
    }

    /**
     * Writes every accepted segment's target text back into its own unit's tree before anything is serialized.
     * All writes happen before repackaging starts so that an earlier write's changed text length can never
     * perturb resolving a later segment's anchor (task 3.2, DD-07, design.md D3).
     *
     * <p>A unit's writes are handed over as one batch rather than applied one at a time, because a translation
     * that legitimately reorders inline markup can move a line break to a block's top level and change how that
     * block splits into runs — see {@link SkeletonAnchors} for the measured case.
     */
    private static void writeSegmentsBack(Document document, ParsedEpub parsed) {
        for (final Unit unit : document.units()) {
            final org.jsoup.nodes.Document tree = treeFor(parsed, unit);
            SkeletonAnchors.writeBackAll(JsoupTreeNode.of(tree.body()), pendingWrites(unit));
        }
    }

    private static List<SkeletonAnchors.PendingWrite> pendingWrites(Unit unit) {
        final List<SkeletonAnchors.PendingWrite> writes = new ArrayList<>();
        for (final Segment segment : unit.segments()) {
            final String targetInner = segment.targetInner();
            if (targetInner != null) {
                writes.add(new SkeletonAnchors.PendingWrite(segment.anchor(), targetInner));
            }
        }
        return writes;
    }

    private static org.jsoup.nodes.Document treeFor(ParsedEpub parsed, Unit unit) {
        final org.jsoup.nodes.Document tree =
                parsed.spineTreesByHandleId().get(unit.skeleton().opaqueId());
        return Objects.requireNonNull(tree, () -> "No parsed tree registered for unit " + unit.href());
    }

    /**
     * Replaces the first {@code dc:language} in the OPF's metadata with {@code targetLanguage} — found directly
     * under {@code metadata} or, failing that, nested inside a legacy {@code dc-metadata} wrapper — adding one
     * (with its {@code dc:} prefix) when none is present anywhere, then rewrites every other language-carrying
     * attribute the spec names wherever its value equals the effective source language (task 4.6, design.md D14
     * §1): the {@code dcterms:language} meta, the package's own {@code xml:lang}, and each XHTML spine content
     * document's {@code html}/{@code body} {@code xml:lang}/{@code lang}. The navigation document is out of this
     * task's scope (task 6.3 continues it). The effective source language is {@code sourceLanguage} when given,
     * else the package's own {@code dc:language} value read <strong>before</strong> the replacement above.
     */
    private static void rewriteLanguages(
            ParsedEpub parsed, Document document, @Nullable String sourceLanguage, String targetLanguage) {
        final org.jdom2.Document opfDocument = parsed.opfDocument();
        final Element metadata = opfDocument.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            throw new CorruptContainerException("OPF has no <metadata> element");
        }
        final Optional<Element> languageElement = findLanguageElement(metadata);
        final String declaredLanguage = languageElement.map(Element::getText).orElse(null);
        final String effectiveSource = sourceLanguage != null ? sourceLanguage : declaredLanguage;
        log.debug(
                "Rewriting EPUB languages passedSource={} declaredSource={} effectiveSource={} target={}",
                sourceLanguage,
                declaredLanguage,
                effectiveSource,
                targetLanguage);

        replaceOrAppendLanguageElement(metadata, languageElement, targetLanguage);
        rewriteDctermsLanguageMeta(metadata, effectiveSource, targetLanguage);
        rewritePackageLangAttribute(opfDocument.getRootElement(), effectiveSource, targetLanguage);
        rewriteContentDocumentLanguages(parsed, document, effectiveSource, targetLanguage);
    }

    private static Optional<Element> findLanguageElement(Element metadata) {
        final List<Element> direct = metadata.getChildren(LANGUAGE_ELEMENT_NAME, DC_NS);
        if (!direct.isEmpty()) {
            return Optional.of(direct.get(0));
        }
        for (final Element child : metadata.getChildren()) {
            if (LEGACY_DC_METADATA_ELEMENT_NAME.equals(child.getName())) {
                final List<Element> nested = child.getChildren(LANGUAGE_ELEMENT_NAME, DC_NS);
                if (!nested.isEmpty()) {
                    return Optional.of(nested.get(0));
                }
            }
        }
        return Optional.empty();
    }

    private static void replaceOrAppendLanguageElement(
            Element metadata, Optional<Element> languageElement, String targetLanguage) {
        if (languageElement.isPresent()) {
            languageElement.get().setText(targetLanguage);
            log.debug("Replaced existing dc:language nested={}", isNestedInLegacyWrapper(languageElement.get()));
            return;
        }
        metadata.addContent(new Element(LANGUAGE_ELEMENT_NAME, DC_NS).setText(targetLanguage));
        log.debug("Appended a missing dc:language element target={}", targetLanguage);
    }

    private static boolean isNestedInLegacyWrapper(Element languageElement) {
        final Element parent = languageElement.getParentElement();
        return parent != null && LEGACY_DC_METADATA_ELEMENT_NAME.equals(parent.getName());
    }

    private static void rewriteDctermsLanguageMeta(Element metadata, @Nullable String effectiveSource, String target) {
        for (final Element meta : metadata.getChildren("meta", OPF_NS)) {
            if (DCTERMS_LANGUAGE_META.equals(meta.getAttributeValue("property"))
                    && sameLanguage(effectiveSource, meta.getText())) {
                log.debug("Rewrote dcterms:language meta from={} to={}", meta.getText(), target);
                meta.setText(target);
            }
        }
    }

    private static void rewritePackageLangAttribute(
            Element packageElement, @Nullable String effectiveSource, String target) {
        final org.jdom2.Attribute langAttribute = packageElement.getAttribute("lang", Namespace.XML_NAMESPACE);
        if (langAttribute != null && sameLanguage(effectiveSource, langAttribute.getValue())) {
            log.debug("Rewrote package xml:lang from={} to={}", langAttribute.getValue(), target);
            langAttribute.setValue(target);
        }
    }

    private static void rewriteContentDocumentLanguages(
            ParsedEpub parsed, Document document, @Nullable String effectiveSource, String target) {
        for (final Unit unit : document.units()) {
            final org.jsoup.nodes.Document tree = treeFor(parsed, unit);
            final org.jsoup.nodes.Element html = tree.selectFirst("html");
            if (html == null) {
                continue;
            }
            rewriteJsoupLangAttributes(html, effectiveSource, target, "html");
            rewriteJsoupLangAttributes(tree.body(), effectiveSource, target, "body");
        }
    }

    private static void rewriteJsoupLangAttributes(
            org.jsoup.nodes.Element element, @Nullable String effectiveSource, String target, String elementName) {
        rewriteJsoupAttribute(element, "xml:lang", effectiveSource, target, elementName);
        rewriteJsoupAttribute(element, "lang", effectiveSource, target, elementName);
    }

    private static void rewriteJsoupAttribute(
            org.jsoup.nodes.Element element,
            String attributeName,
            @Nullable String effectiveSource,
            String target,
            String elementName) {
        if (element.hasAttr(attributeName) && sameLanguage(effectiveSource, element.attr(attributeName))) {
            log.debug("Rewrote {} {} from={} to={}", elementName, attributeName, element.attr(attributeName), target);
            element.attr(attributeName, target);
        }
    }

    /**
     * Compares a language value against the effective source language after {@link LanguageTags#normalize}, so
     * {@code en-US} and {@code en} match; falls back to a direct case-insensitive comparison of the raw values when
     * either side names no catalogued language (e.g. {@code la}), so two uncatalogued tags are never spuriously
     * treated as equal to each other or to the source merely because both fail to normalize.
     */
    private static boolean sameLanguage(@Nullable String effectiveSource, @Nullable String candidate) {
        if (effectiveSource == null || candidate == null) {
            return false;
        }
        final Optional<String> normalizedSource = LanguageTags.normalize(effectiveSource);
        final Optional<String> normalizedCandidate = LanguageTags.normalize(candidate);
        if (normalizedSource.isPresent() && normalizedCandidate.isPresent()) {
            return normalizedSource.get().equals(normalizedCandidate.get());
        }
        return effectiveSource.equalsIgnoreCase(candidate);
    }

    /**
     * Re-zips the archive with {@code mimetype} first and STORED, then every remaining entry — in its original
     * relative order and with <strong>its own original compression method</strong>: the mutated OPF, each spine
     * unit's serialized tree, or the entry's original captured bytes unchanged.
     *
     * <p>Preserving the method matters because 26 corpus books deliberately STORE 2,578 already-compressed
     * entries — one stores 1,950 — and DD-43 licenses re-compressing an entry that was <em>already</em>
     * compressed, not converting a stored one into a compressed one.
     */
    private static void repackage(ParsedEpub parsed, Document document, Path destination) {
        try (OutputStream out = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            writeStored(zip, mimetypeEntry(parsed));
            writeRemainingEntries(zip, parsed, document);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write EPUB output", e);
        }
    }

    /**
     * The source's own {@code mimetype} entry, reused verbatim — or, when the container never had one, the entry
     * OCF fixes completely (name, content, position, STORED), so nothing is invented and a readable book that
     * some readers reject is exported as one they accept (ADR-0030).
     */
    private static RawEntry mimetypeEntry(ParsedEpub parsed) {
        for (final RawEntry entry : parsed.rawEntries()) {
            if (MIMETYPE_ENTRY_NAME.equals(entry.name())) {
                return entry;
            }
        }
        return new RawEntry(
                MIMETYPE_ENTRY_NAME, 0, ZipEntry.STORED, MIMETYPE_CONTENT.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeStored(ZipOutputStream zip, RawEntry entry) throws IOException {
        final byte[] content = entry.content();
        final ZipEntry zipEntry = new ZipEntry(entry.name());
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setSize(content.length);
        zipEntry.setCompressedSize(content.length);
        final CRC32 crc = new CRC32();
        crc.update(content);
        zipEntry.setCrc(crc.getValue());
        zip.putNextEntry(zipEntry);
        zip.write(content);
        zip.closeEntry();
    }

    private static void writeRemainingEntries(ZipOutputStream zip, ParsedEpub parsed, Document document)
            throws IOException {
        final Map<String, org.jsoup.nodes.Document> treesByHref = treesByHref(parsed, document);
        for (final RawEntry entry : parsed.rawEntries()) {
            if (!MIMETYPE_ENTRY_NAME.equals(entry.name())) {
                writeWithOriginalMethod(zip, entry, payloadFor(entry, parsed, treesByHref));
            }
        }
    }

    private static Map<String, org.jsoup.nodes.Document> treesByHref(ParsedEpub parsed, Document document) {
        final Map<String, org.jsoup.nodes.Document> byHref = new LinkedHashMap<>();
        for (final Unit unit : document.units()) {
            final org.jsoup.nodes.Document tree =
                    parsed.spineTreesByHandleId().get(unit.skeleton().opaqueId());
            if (tree != null) {
                byHref.put(unit.href(), tree);
            }
        }
        return byHref;
    }

    private static byte[] payloadFor(
            RawEntry entry, ParsedEpub parsed, Map<String, org.jsoup.nodes.Document> treesByHref) {
        if (entry.name().equals(parsed.opfPath())) {
            return XmlDocumentSerializer.serialize(parsed.opfDocument(), entry.content());
        }
        final org.jsoup.nodes.Document tree = treesByHref.get(entry.name());
        return tree == null ? entry.content() : serializeContentDocument(tree);
    }

    /**
     * Serializes a spine content document, restoring the leading line feed the HTML parser discards immediately
     * after a preformatted block's start tag (task 6.1, DD-49) before calling {@code outerHtml()}. {@code tree}
     * is the live object {@link ParsedEpub#spineTreesByHandleId()} holds (F9), so
     * {@link PreformattedLineFeedRestorer#forSerialization} restores on a clone rather than {@code tree} itself
     * — mutating {@code tree} in place would compound on the next {@code write()} of the same open document.
     */
    private static byte[] serializeContentDocument(org.jsoup.nodes.Document tree) {
        final org.jsoup.nodes.Document serializable = PreformattedLineFeedRestorer.forSerialization(tree);
        return AttributeLineFeedEscaper.outerHtml(serializable)
                .getBytes(tree.outputSettings().charset());
    }

    /**
     * Writes one entry back under the compression method it arrived with. A STORED entry needs its size and CRC
     * declared up front — {@link ZipOutputStream} cannot compute them for an uncompressed entry the way it does
     * for a deflated one — which is the whole reason this is not a one-line method.
     */
    private static void writeWithOriginalMethod(ZipOutputStream zip, RawEntry entry, byte[] content)
            throws IOException {
        if (entry.method() == ZipEntry.STORED) {
            writeStored(zip, new RawEntry(entry.name(), entry.order(), ZipEntry.STORED, content));
            return;
        }
        final ZipEntry zipEntry = new ZipEntry(entry.name());
        zipEntry.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(zipEntry);
        zip.write(content);
        zip.closeEntry();
    }
}
