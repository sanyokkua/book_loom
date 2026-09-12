package ua.bookloom.document.epub;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.BlockSegmentWalker;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.JsoupTreeNode;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.TreeDialect;
import ua.bookloom.document.model.ZipEntryReader;
import ua.bookloom.util.hash.HashUtil;

/**
 * Opens an EPUB file into the {@code :api} document model — the read side of seam F1 for EPUB, with DRM
 * adjudicated before any other parsing (design.md D7).
 *
 * <p>Throws {@link CorruptContainerException} / {@link DrmRefusedException} on failure rather than returning a
 * {@code Result}: a later change's {@code DocumentService} is the port boundary that catches these and builds the
 * typed {@code Result<Document>} envelope (per {@code error-envelope.md}'s "classify at the point of
 * recognition"), which lets this class's own tests assert the thrown exception directly rather than unwrapping a
 * {@code Result} that has no real {@code AppError} classification yet.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EpubReader {

    private static final String CONTAINER_ENTRY = "META-INF/container.xml";
    private static final String ENCRYPTION_ENTRY = "META-INF/encryption.xml";

    private final OpenEpubRegistry registry;

    /**
     * Opens an EPUB file into the {@code :api} document model.
     *
     * @param source the {@code .epub} file to open
     * @return the parsed document
     * @throws CorruptContainerException if the container, OPF, or spine is missing or malformed
     * @throws DrmRefusedException if the book declares DRM this system does not allow
     */
    public Document read(Path source) {
        Objects.requireNonNull(source, "source");
        final byte[] fileBytes = readAllBytes(source);
        final List<RawEntry> entries = ZipEntryReader.readAll(fileBytes);
        final Map<String, RawEntry> byName = index(entries);

        final String opfPath = ContainerReader.locateOpf(byName.get(CONTAINER_ENTRY));
        final RawEntry opfEntry = requireEntry(byName, opfPath, "OPF package document");
        final ParsedOpf opf = OpfParser.parse(opfEntry.content(), opfPath);

        // Adjudication needs the manifest, so it can no longer be the very first thing this reader does
        // (ADR-0026). "No partial import" survives because it still runs before any content document is read
        // and before any Document, Unit or Segment exists — an OPF parsed into memory is not an import.
        DrmAdjudicator.adjudicate(byName.get(ENCRYPTION_ENTRY), opf);

        final UnitsResult unitsResult = readUnits(opf, byName);
        final String documentId = UUID.randomUUID().toString();
        registry.put(documentId, new ParsedEpub(entries, opfPath, opf.jdomDocument(), unitsResult.jsoupTrees()));

        return new Document(
                documentId,
                BookFormat.EPUB,
                opf.dcLanguages().isEmpty() ? null : opf.dcLanguages().get(0),
                null,
                // A container format records no document-level encoding and no byte-order-mark flag: each spine
                // document declares its own charset, so the container as a whole has no answer to give (design.md D5).
                null,
                null,
                HashUtil.sha256Hex(fileBytes),
                metadata(opf),
                unitsResult.units());
    }

    private static byte[] readAllBytes(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new CorruptContainerException("Unable to read EPUB file", e);
        }
    }

    private static Map<String, RawEntry> index(List<RawEntry> entries) {
        final Map<String, RawEntry> byName = new LinkedHashMap<>();
        for (final RawEntry entry : entries) {
            byName.put(entry.name(), entry);
        }
        return byName;
    }

    private static RawEntry requireEntry(Map<String, RawEntry> byName, String name, String description) {
        final RawEntry entry = byName.get(name);
        if (entry == null) {
            throw new CorruptContainerException("Missing " + description + ": " + name);
        }
        return entry;
    }

    /**
     * Reads the spine documents that exist, in spine order. A spine item whose file is absent from the archive is
     * skipped rather than refused: authoring tools leave dangling itemrefs behind (one real book lists twenty
     * pages and ships nine), and every reader shows what is there. Unit orders stay dense over the units built.
     * Only a spine with <em>no</em> present document refuses — there is nothing to translate.
     */
    private static UnitsResult readUnits(ParsedOpf opf, Map<String, RawEntry> byName) {
        final List<Unit> units = new ArrayList<>();
        final Map<String, org.jsoup.nodes.Document> trees = new LinkedHashMap<>();
        final String opfDir = OpfPaths.parentOf(opf.opfPath());
        int skipped = 0;
        for (final SpineItem item : opf.spineItems()) {
            final String href = OpfPaths.resolve(opfDir, item.href());
            final RawEntry entry = byName.get(href);
            if (entry == null) {
                skipped++;
                continue;
            }
            addUnit(entry, href, item, units.size(), units, trees);
        }
        if (units.isEmpty()) {
            throw new CorruptContainerException("No spine document is present in the archive");
        }
        if (skipped > 0) {
            log.warn(
                    "Skipped {} spine item(s) whose file is missing from the archive; reading the {} present",
                    skipped,
                    units.size());
        }
        return new UnitsResult(units, trees);
    }

    private static void addUnit(
            RawEntry entry,
            String href,
            SpineItem item,
            int order,
            List<Unit> units,
            Map<String, org.jsoup.nodes.Document> trees) {
        final org.jsoup.nodes.Document doc = XhtmlParser.parse(entry.content(), href);
        final String handleId = UUID.randomUUID().toString();
        trees.put(handleId, doc);
        final Element body = doc.body();
        final List<Segment> segments = BlockSegmentWalker.walk(JsoupTreeNode.of(body), href, TreeDialect.XHTML);
        units.add(new Unit(href, order, href, item.mediaType(), new SkeletonHandle(handleId), segments));
    }

    private static Map<String, String> metadata(ParsedOpf opf) {
        final Map<String, String> metadata = new LinkedHashMap<>();
        if (opf.title() != null) {
            metadata.put("title", opf.title());
        }
        if (opf.author() != null) {
            metadata.put("author", opf.author());
        }
        return metadata;
    }

    private record UnitsResult(List<Unit> units, Map<String, org.jsoup.nodes.Document> jsoupTrees) {}
}
