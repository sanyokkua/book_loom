package ua.bookloom.document.model;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TreeNode} adapter over a JDOM2 node — the FB2 side of the shared structural walker.
 */
public final class Jdom2TreeNode implements TreeNode {

    /** The wrapper element a translated fragment is parsed inside; never appears in any output. */
    private static final String FRAGMENT_ROOT = "bookloom-fragment";

    private final Content content;

    private Jdom2TreeNode(Content content) {
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * Adapts a JDOM2 node for the shared walker.
     *
     * @param content the JDOM2 node to adapt
     * @return a {@link TreeNode} view delegating to {@code content}; mutations through it change the real tree
     */
    public static TreeNode of(Content content) {
        return new Jdom2TreeNode(content);
    }

    @Override
    public @Nullable String tagName() {
        return content instanceof Element element ? element.getName().toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Character data only. An {@code EntityRef} — the non-breaking space is the common one, 10,377 of them in the
     * surveyed corpus — deliberately reports no text: a block whose only content is a non-breaking space is a
     * spacer with nothing to translate, and it is re-emitted from the skeleton either way.
     */
    @Override
    public String ownText() {
        return content instanceof Text text ? text.getText() : "";
    }

    @Override
    public List<TreeNode> childNodes() {
        if (!(content instanceof Element element)) {
            return List.of();
        }
        final List<Content> children = element.getContent();
        final List<TreeNode> adapted = new ArrayList<>(children.size());
        for (final Content child : children) {
            adapted.add(new Jdom2TreeNode(child));
        }
        return adapted;
    }

    @Override
    public String markup() {
        return new XMLOutputter(Format.getRawFormat()).outputString(List.of(content));
    }

    /**
     * Parses {@code replacementMarkup} inside a wrapper carrying every namespace in scope at this element, then
     * splices the parsed nodes in place of the given child range. The namespaces have to be re-declared because a
     * fragment is parsed on its own: without them, an inline {@code <emphasis>} would land in no namespace and an
     * {@code l:href} would not parse at all — and real books declare that prefix as either {@code l} or
     * {@code xlink}, sometimes redeclared below the root.
     *
     * <p>A fragment carrying an undeclared entity reference fails loudly rather than degrading to literal text.
     * Silently writing markup as text is the exact defect ADR-0025 exists to remove, and no code path in this
     * change produces such a fragment — nothing is translated here, and masking replaces inline constructs with
     * placeholders before a model ever sees them.
     */
    @Override
    public void replaceChildren(int fromInclusive, int toExclusive, String replacementMarkup) {
        Objects.requireNonNull(replacementMarkup, "replacementMarkup");
        if (!(content instanceof Element element)) {
            throw new IllegalStateException("Only an element has children to replace");
        }
        final List<Content> parsed = parseFragment(element, replacementMarkup);
        final List<Content> doomed = new ArrayList<>(element.getContent().subList(fromInclusive, toExclusive));
        for (final Content old : doomed) {
            old.detach();
        }
        element.addContent(fromInclusive, parsed);
    }

    private static List<Content> parseFragment(Element scope, String replacementMarkup) {
        final String wrapped = "<" + FRAGMENT_ROOT + namespaceDeclarations(scope) + ">" + replacementMarkup + "</"
                + FRAGMENT_ROOT + ">";
        final Element root;
        try {
            root = SecureXml.builder().build(new StringReader(wrapped)).getRootElement();
        } catch (JDOMException | IOException e) {
            throw new MalformedFragmentException("Translated content is not well-formed markup", e);
        }
        final List<Content> detached = new ArrayList<>(root.getContent());
        for (final Content child : detached) {
            child.detach();
        }
        return detached;
    }

    private static String namespaceDeclarations(Element scope) {
        final StringBuilder declarations = new StringBuilder();
        for (final Namespace namespace : scope.getNamespacesInScope()) {
            appendDeclaration(declarations, namespace);
        }
        return declarations.toString();
    }

    private static void appendDeclaration(StringBuilder declarations, Namespace namespace) {
        if (Namespace.XML_NAMESPACE.equals(namespace) || namespace.getURI().isEmpty()) {
            return;
        }
        final String prefix = namespace.getPrefix();
        declarations
                .append(prefix.isEmpty() ? " xmlns=\"" : " xmlns:" + prefix + "=\"")
                .append(namespace.getURI())
                .append('"');
    }
}
