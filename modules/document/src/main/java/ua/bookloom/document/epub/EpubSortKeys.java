package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;

/**
 * Keeps a translated book from sorting under its source-language title or author: when a {@code dc:title} or
 * {@code dc:creator} was written with a different text, the sort keys that described the old text are removed — the
 * {@code opf:file-as} attribute, a {@code file-as} refinement naming the element, and, for a title, Calibre's
 * {@code calibre:title_sort} meta. Dropping rather than rewriting is deliberate: no key the book carries is derived from
 * the new text, and a reader that finds none sorts by the text itself, which is right in the target language.
 *
 * <p>An element whose text did not change keeps every key, so a book written with no translated title or author stays
 * canonical-equal to its source.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubSortKeys {

    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String FILE_AS = "file-as";
    private static final String TITLE = "title";
    private static final String CREATOR = "creator";
    private static final String TITLE_SORT = "calibre:title_sort";

    private final IdentityHashMap<Element, String> before = new IdentityHashMap<>();

    /**
     * Remembers the text of every title and author, before any segment is written back.
     *
     * @param opf the parsed package; never null
     * @return the snapshot to hand to {@link #dropStale} once the segments are written
     */
    static EpubSortKeys snapshot(final org.jdom2.Document opf) {
        final EpubSortKeys keys = new EpubSortKeys();
        namedElements(opf).forEach(element -> keys.before.put(element, element.getText()));
        return keys;
    }

    /**
     * Removes the sort keys of every title or author whose text changed since the snapshot.
     *
     * @param opf the same package, after its segments were written back
     */
    void dropStale(final org.jdom2.Document opf) {
        final Element metadata = opf.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            return;
        }
        for (final Element element : namedElements(opf)) {
            if (!element.getText().equals(before.get(element))) {
                dropKeysOf(metadata, element);
            }
        }
    }

    private static void dropKeysOf(final Element metadata, final Element changed) {
        log.debug("{} was translated: dropping the sort keys of its old text", changed.getName());
        changed.removeAttribute(FILE_AS, OPF_NS);
        final String id = changed.getAttributeValue("id");
        if (id != null) {
            metadata.getChildren("meta", OPF_NS)
                    .removeIf(meta -> ("#" + id).equals(meta.getAttributeValue("refines"))
                            && FILE_AS.equals(meta.getAttributeValue("property")));
        }
        if (TITLE.equals(changed.getName())) {
            metadata.getChildren("meta", OPF_NS).removeIf(meta -> TITLE_SORT.equals(meta.getAttributeValue("name")));
        }
    }

    private static List<Element> namedElements(final org.jdom2.Document opf) {
        final Element metadata = opf.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            return List.of();
        }
        final List<Element> found = new ArrayList<>(metadata.getChildren(TITLE, DC_NS));
        found.addAll(metadata.getChildren(CREATOR, DC_NS));
        return found;
    }
}
