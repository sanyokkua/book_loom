package ua.bookloom.document.epub;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.AuxiliarySlots;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DocumentNotOpenException;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.SkeletonAnchors;

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

        final EpubSortKeys sortKeys = EpubSortKeys.snapshot(parsed.opfDocument());
        final Set<String> changed = new HashSet<>(writeSegmentsBack(document, parsed));
        sortKeys.dropStale(parsed.opfDocument());
        EpubLanguageRewriter.rewrite(parsed, document, sourceLanguage, targetLanguage, changed);
        repackage(parsed, document, destination, changed);
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
    private static Set<String> writeSegmentsBack(Document document, ParsedEpub parsed) {
        final List<Unit> bodyUnits =
                document.units().stream().filter(unit -> !unit.isAuxiliary()).toList();
        final Set<String> resources = new HashSet<>();
        final Map<String, String> bodyTargets = new HashMap<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary()) {
                final AuxiliarySlots.Outcome outcome = parsed.slotsOf(unit).writeBack(unit, bodyUnits);
                resources.addAll(outcome.resources());
                bodyTargets.putAll(outcome.bodyTargets());
            }
        }
        for (final Unit unit : bodyUnits) {
            SkeletonAnchors.writeBackAll(
                    JsoupTreeNode.of(treeFor(parsed, unit).body()), pendingWrites(unit, bodyTargets));
        }
        return resources;
    }

    /** A run whose image descriptions were substituted is written from that target rather than its own. */
    private static List<SkeletonAnchors.PendingWrite> pendingWrites(Unit unit, Map<String, String> bodyTargets) {
        final List<SkeletonAnchors.PendingWrite> writes = new ArrayList<>();
        for (final Segment segment : unit.segments()) {
            final String targetInner = bodyTargets.getOrDefault(segment.id(), segment.targetInner());
            if (targetInner != null) {
                writes.add(new SkeletonAnchors.PendingWrite(segment.anchor(), targetInner));
            }
        }
        return writes;
    }

    static org.jsoup.nodes.Document treeFor(ParsedEpub parsed, Unit unit) {
        final org.jsoup.nodes.Document tree =
                parsed.spineTreesByHandleId().get(unit.skeleton().opaqueId());
        return Objects.requireNonNull(tree, () -> "No parsed tree registered for unit " + unit.href());
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
    private static void repackage(ParsedEpub parsed, Document document, Path destination, Set<String> changed) {
        try (OutputStream out = Files.newOutputStream(destination);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            writeStored(zip, mimetypeEntry(parsed));
            writeRemainingEntries(zip, parsed, document, changed);
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

    private static void writeRemainingEntries(
            ZipOutputStream zip, ParsedEpub parsed, Document document, Set<String> changed) throws IOException {
        final Map<String, org.jsoup.nodes.Document> treesByHref = treesByHref(parsed, document);
        for (final RawEntry entry : parsed.rawEntries()) {
            if (!MIMETYPE_ENTRY_NAME.equals(entry.name())) {
                writeWithOriginalMethod(zip, entry, payloadFor(entry, parsed, treesByHref, changed));
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
            RawEntry entry, ParsedEpub parsed, Map<String, org.jsoup.nodes.Document> treesByHref, Set<String> changed) {
        if (entry.name().equals(parsed.opfPath())) {
            return XmlDocumentSerializer.serialize(parsed.opfDocument(), entry.content());
        }
        final org.jsoup.nodes.Document tree = treesByHref.get(entry.name());
        if (tree != null) {
            return serializeContentDocument(tree, entry.content());
        }
        return parsed.navigation().payload(entry, changed).orElse(entry.content());
    }

    /**
     * Serializes a spine content document, restoring the leading line feed the HTML parser discards immediately
     * after a preformatted block's start tag (task 6.1, DD-49) before calling {@code outerHtml()}. {@code tree}
     * is the live object {@link ParsedEpub#spineTreesByHandleId()} holds (F9), so
     * {@link PreformattedLineFeedRestorer#forSerialization} restores on a clone rather than {@code tree} itself
     * — mutating {@code tree} in place would compound on the next {@code write()} of the same open document.
     */
    static byte[] serializeContentDocument(org.jsoup.nodes.Document tree, byte[] sourceBytes) {
        final org.jsoup.nodes.Document serializable = PreformattedLineFeedRestorer.forSerialization(tree);
        final Charset charset = tree.outputSettings().charset();
        return XhtmlProlog.serialize(serializable, new String(sourceBytes, charset))
                .getBytes(charset);
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
