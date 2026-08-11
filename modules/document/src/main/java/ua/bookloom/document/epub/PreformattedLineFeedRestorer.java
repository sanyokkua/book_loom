package ua.bookloom.document.epub;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Restores the one leading U+000A the HTML parser discards immediately after a preformatted block's start tag —
 * the serialization-time counterpart the HTML serialization spec requires and jsoup 1.23.1 does not implement
 * (task 6.1, DD-49; the measurement is recorded in {@code docs/implementation_plan/notes-corpus-verification.md},
 * finding F2). Left unrestored, a
 * {@code pre}/{@code textarea}/{@code listing} block whose content begins with two or more line breaks loses one
 * every time the document is written, silently deleting a line from inside a poem or verse block on every export.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PreformattedLineFeedRestorer {

    /** The HTML serialization spec's newline-suppressing element names, verbatim (task 6.1). */
    private static final String RESTORABLE_TAG_SELECTOR = "pre, textarea, listing";

    /**
     * Returns a tree fit to serialize. {@code tree} may be the live object a caller's registry holds — it is
     * never mutated here; a qualifying block's restore always happens on a fresh {@link Document#clone()}, so
     * repeated calls against the same {@code tree} each restore exactly once rather than compounding.
     *
     * @param tree the parsed spine content document to serialize
     * @return {@code tree} itself, unchanged, if no block needs restoring; otherwise a deep clone carrying its
     *     pinned {@link Document#outputSettings()} with each qualifying block's first child text node given back
     *     its discarded leading line feed
     */
    static Document forSerialization(Document tree) {
        if (tree.select(RESTORABLE_TAG_SELECTOR).stream().noneMatch(PreformattedLineFeedRestorer::needsRestore)) {
            return tree;
        }
        final Document clone = tree.clone();
        clone.outputSettings(tree.outputSettings());
        clone.select(RESTORABLE_TAG_SELECTOR).stream()
                .filter(PreformattedLineFeedRestorer::needsRestore)
                .forEach(PreformattedLineFeedRestorer::restore);
        return clone;
    }

    /**
     * @param element a {@code pre}/{@code textarea}/{@code listing} element
     * @return {@code true} if it has a first child that is a text node whose content begins with a bare
     *     U+000A — the exact character the parser discards. A block with no first child at all (an empty
     *     {@code <pre></pre>}) is never reached into, and a block beginning with a carriage-return-line-feed
     *     pair loses nothing on parse (measured), so it must not gain a line break it never lost.
     */
    private static boolean needsRestore(Element element) {
        final List<Node> children = element.childNodes();
        return !children.isEmpty()
                && children.get(0) instanceof TextNode text
                && text.getWholeText().startsWith("\n");
    }

    private static void restore(Element element) {
        final TextNode text = (TextNode) element.childNodes().get(0);
        text.text("\n" + text.getWholeText());
    }
}
