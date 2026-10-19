package ua.bookloom.document.epub;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a translated book from sorting under its source-language title or author: when a {@code dc:title} or
 * {@code dc:creator} was written with a different text, the sort keys that described the old text are removed — the
 * {@code opf:file-as} attribute, a {@code file-as} refinement naming the element, and, for a title, Calibre's
 * {@code calibre:title_sort} meta. A title's keys are dropped: a reader that finds none sorts by the text itself, which
 * is right in the target language. A creator's key is rewritten instead when it can be derived from the translated name
 * ({@link FileAsDerivation}), so the book still shelves under the author's surname.
 *
 * <p>An element whose text did not change keeps every key, so a book written with no translated title or author stays
 * canonical-equal to its source.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubSortKeys {

    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String FILE_AS = "file-as";
    private static final String TITLE = "title";
    private static final String CREATOR = "creator";
    private static final String TITLE_SORT = "calibre:title_sort";

    private final List<String> before;

    /**
     * Remembers the text of every title and author as the source wrote it. The package an open book keeps is changed
     * by every export, so the source's own bytes are parsed afresh for this: comparing against the kept tree would
     * take an earlier export's translated title for the original.
     *
     * @param sourceOpf the package parsed from the source's bytes; never null
     * @return the snapshot to hand to {@link #withoutStale} once the segments are written
     */
    static EpubSortKeys snapshot(final org.jdom2.Document sourceOpf) {
        return new EpubSortKeys(
                namedElements(sourceOpf).stream().map(Element::getText).toList());
    }

    /**
     * Copies the package and removes from the copy the sort keys of every title or author whose text differs from the
     * source's. The kept package is never touched, so a later export that writes the original title again still has
     * every key.
     *
     * @param opf the kept package, after its segments were written back
     * @return the package to serialize
     */
    org.jdom2.Document withoutStale(final org.jdom2.Document opf) {
        final org.jdom2.Document copy = opf.clone();
        final Element metadata = copy.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            return copy;
        }
        final List<Element> named = namedElements(copy);
        for (int i = 0; i < named.size() && i < before.size(); i++) {
            if (!named.get(i).getText().equals(before.get(i))) {
                dropKeysOf(metadata, named.get(i));
            }
        }
        return copy;
    }

    private static void dropKeysOf(final Element metadata, final Element changed) {
        log.debug("{} was translated: replacing the sort keys of its old text", changed.getName());
        final String id = changed.getAttributeValue("id");
        final List<Element> refinements = id == null ? List.of() : fileAsRefinements(metadata, id);
        final String derived = CREATOR.equals(changed.getName()) ? derivedKey(changed, refinements) : null;
        if (derived == null) {
            changed.removeAttribute(FILE_AS, OPF_NS);
            metadata.getChildren("meta", OPF_NS).removeAll(refinements);
        } else {
            keepDerived(changed, refinements, derived);
        }
        if (TITLE.equals(changed.getName())) {
            metadata.getChildren("meta", OPF_NS).removeIf(meta -> TITLE_SORT.equals(meta.getAttributeValue("name")));
        }
    }

    private static List<Element> fileAsRefinements(final Element metadata, final String id) {
        return metadata.getChildren("meta", OPF_NS).stream()
                .filter(meta -> ("#" + id).equals(meta.getAttributeValue("refines"))
                        && FILE_AS.equals(meta.getAttributeValue("property")))
                .toList();
    }

    private static @Nullable String derivedKey(final Element creator, final List<Element> refinements) {
        final String attribute = creator.getAttributeValue(FILE_AS, OPF_NS);
        final String old = attribute != null
                ? attribute
                : refinements.stream().map(Element::getText).findFirst().orElse(null);
        return old == null ? null : FileAsDerivation.derive(old, creator.getText());
    }

    private static void keepDerived(final Element creator, final List<Element> refinements, final String derived) {
        if (creator.getAttribute(FILE_AS, OPF_NS) != null) {
            creator.setAttribute(FILE_AS, derived, OPF_NS);
        }
        refinements.forEach(refinement -> refinement.setText(derived));
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
