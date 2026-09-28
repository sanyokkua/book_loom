package ua.bookloom.document.epub;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.document.model.RawEntry;

/**
 * The navigation document and the NCX an open EPUB keeps as live trees, because their table-of-contents labels are
 * auxiliary text the writer puts back (design.md D12). Every other resource outside the spine stays verbatim.
 *
 * <p>A tree is kept only for a resource the writer may change: a navigation document outside the spine (one inside
 * it is a body unit already), and the NCX. Either is {@code null} when the book has none, or — for the NCX — when it
 * could not be read.
 *
 * @param navPath the navigation document's archive path, or {@code null}
 * @param navTree its parsed tree, or {@code null}
 * @param ncxPath the NCX's archive path, or {@code null}
 * @param ncxTree its parsed tree, or {@code null}
 */
@Slf4j
record NavigationResources(
        @Nullable String navPath,
        org.jsoup.nodes.@Nullable Document navTree,
        @Nullable String ncxPath,
        org.jdom2.@Nullable Document ncxTree) {

    static NavigationResources none() {
        return new NavigationResources(null, null, null, null);
    }

    /**
     * The bytes to write for {@code entry} when it is a navigation resource that changed; the writer copies every
     * other entry, and an unchanged one, byte for byte.
     *
     * @param entry the archive entry being written
     * @param changed the archive paths of the resources a label or a language attribute was written into
     * @return the re-serialized bytes, or empty when the entry is copied as it is
     */
    Optional<byte[]> payload(RawEntry entry, Set<String> changed) {
        final org.jsoup.nodes.Document nav = navTree;
        final org.jdom2.Document ncx = ncxTree;
        if (nav != null && entry.name().equals(navPath)) {
            return reserialized(entry, changed, () -> EpubWriter.serializeContentDocument(nav, entry.content()));
        }
        if (ncx != null && entry.name().equals(ncxPath)) {
            return reserialized(entry, changed, () -> XmlDocumentSerializer.serialize(ncx, entry.content()));
        }
        return Optional.empty();
    }

    private static Optional<byte[]> reserialized(RawEntry entry, Set<String> changed, Supplier<byte[]> serialize) {
        if (!changed.contains(entry.name())) {
            log.debug("navigation resource {} copied: no label or language attribute changed", entry.name());
            return Optional.empty();
        }
        log.debug("navigation resource {} re-serialized: a label or language attribute changed", entry.name());
        return Optional.of(serialize.get());
    }
}
