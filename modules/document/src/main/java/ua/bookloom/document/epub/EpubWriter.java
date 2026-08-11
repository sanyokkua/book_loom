package ua.bookloom.document.epub;

import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SkeletonAnchors;

/**
 * Reassembles an EPUB previously opened by {@link EpubReader} and repackages it — the write side of seam F1 for
 * EPUB (task group 3). Writes accepted segments' target text back into the exact skeleton node each was parsed
 * from ({@link ua.bookloom.document.model.SkeletonAnchors#writeBack}), sets the target language, and re-zips with
 * {@code mimetype} first and STORED (FR-DOC-EPUB-3, FR-DOC-06, FR-DOC-EPUB-6).
 *
 * <p>Throws rather than returning a {@code Result}, matching {@link EpubReader}: a later change's
 * {@code DocumentService} is the port boundary that catches these and builds the typed envelope.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EpubWriter {

    private static final String MIMETYPE_ENTRY_NAME = "mimetype";
    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    private static final Namespace DC_NS = Namespace.getNamespace("http://purl.org/dc/elements/1.1/");

    private final OpenEpubRegistry registry;

    /**
     * Reassembles {@code document} and writes it to {@code destination} in EPUB, setting the target language.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage the language to declare in the written book (for example an ISO 639-1 code)
     * @return {@code destination}
     * @throws DocumentNotOpenException if {@code document}'s id was never registered by {@link EpubReader#read}
     * @throws CorruptContainerException if the registered state has no {@code mimetype} entry or the OPF has no
     *     {@code <metadata>} element to set the language in
     */
    public Path write(Document document, Path destination, String targetLanguage) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        final ParsedEpub parsed =
                registry.find(document.id()).orElseThrow(() -> new DocumentNotOpenException(document.id()));

        writeSegmentsBack(document, parsed);
        setTargetLanguage(parsed.opfDocument(), targetLanguage);
        repackage(parsed, document, destination);
        return destination;
    }

    /**
     * Writes every accepted segment's target text back into its own unit's tree before anything is serialized.
     * All writes happen before repackaging starts so that an earlier write's changed text length can never
     * perturb resolving a later segment's anchor (task 3.2, DD-07, design.md D3).
     */
    private static void writeSegmentsBack(Document document, ParsedEpub parsed) {
        for (final Unit unit : document.units()) {
            final org.jsoup.nodes.Document tree = treeFor(parsed, unit);
            for (final Segment segment : unit.segments()) {
                final String targetInner = segment.targetInner();
                if (targetInner != null) {
                    SkeletonAnchors.writeBack(JsoupTreeNode.of(tree.body()), segment.anchor(), targetInner);
                }
            }
        }
    }

    private static org.jsoup.nodes.Document treeFor(ParsedEpub parsed, Unit unit) {
        final org.jsoup.nodes.Document tree =
                parsed.spineTreesByHandleId().get(unit.skeleton().opaqueId());
        return Objects.requireNonNull(tree, () -> "No parsed tree registered for unit " + unit.href());
    }

    /**
     * Replaces the first {@code dc:language} in the OPF's metadata with {@code targetLanguage}, adding one when
     * none is present, and leaves any further {@code dc:language} entries untouched (task 3.4, FR-DOC-EPUB-6).
     */
    private static void setTargetLanguage(org.jdom2.Document opfDocument, String targetLanguage) {
        final Element metadata = opfDocument.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            throw new CorruptContainerException("OPF has no <metadata> element");
        }
        final List<Element> languages = metadata.getChildren("language", DC_NS);
        if (languages.isEmpty()) {
            metadata.addContent(new Element("language", DC_NS).setText(targetLanguage));
        } else {
            languages.get(0).setText(targetLanguage);
        }
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
            writeStored(zip, requireMimetype(parsed));
            writeRemainingEntries(zip, parsed, document);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write EPUB output", e);
        }
    }

    private static RawEntry requireMimetype(ParsedEpub parsed) {
        for (final RawEntry entry : parsed.rawEntries()) {
            if (MIMETYPE_ENTRY_NAME.equals(entry.name())) {
                return entry;
            }
        }
        throw new CorruptContainerException("EPUB has no mimetype entry");
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
            return serializeOpf(parsed.opfDocument());
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
        return serializable.outerHtml().getBytes(tree.outputSettings().charset());
    }

    private static byte[] serializeOpf(org.jdom2.Document opfDocument) {
        final XMLOutputter outputter = new XMLOutputter(Format.getRawFormat());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            outputter.output(opfDocument, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to serialize OPF package document", e);
        }
        return out.toByteArray();
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
