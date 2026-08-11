package ua.bookloom.document.epub;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jsoup.nodes.Document;
import ua.bookloom.document.model.RawEntry;

/**
 * Everything a later change's {@code write()} needs to find again, keyed by the opaque ids that cross the
 * {@code :api} boundary as {@code SkeletonHandle}s (design.md D1) — the parsed tree itself never leaves
 * {@code :document}, so this is where it actually lives while a document is open.
 *
 * <p>Registered in {@link OpenEpubRegistry} under the parsed {@code Document}'s own id; that registry is
 * in-memory only for this change — an open document does not survive a process restart. Making that durable is a
 * future change, not this one.
 *
 * @param rawEntries every zip entry captured at read time, in physical stream order (task 2.3) — carried through
 *     unchanged on export for every entry {@code :document} never parses
 * @param opfPath the OPF's own path within the archive, so export can tell the OPF's raw entry apart from every
 *     other passthrough entry when repackaging (task 3.3)
 * @param opfDocument the OPF's own parsed tree, kept so export can replace {@code dc:language} in place
 * @param spineTreesByHandleId each spine unit's parsed jsoup tree, keyed by its {@code SkeletonHandle}'s opaque id
 */
record ParsedEpub(
        List<RawEntry> rawEntries,
        String opfPath,
        org.jdom2.Document opfDocument,
        Map<String, Document> spineTreesByHandleId) {

    ParsedEpub {
        Objects.requireNonNull(rawEntries, "rawEntries");
        Objects.requireNonNull(opfPath, "opfPath");
        Objects.requireNonNull(opfDocument, "opfDocument");
        Objects.requireNonNull(spineTreesByHandleId, "spineTreesByHandleId");
        rawEntries = List.copyOf(rawEntries);
        spineTreesByHandleId = Map.copyOf(spineTreesByHandleId);
    }
}
