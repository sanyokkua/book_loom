package ua.bookloom.document.model;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Content;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TreeNode} adapter over a JDOM2 node — the FB2 side of the shared structural walker.
 */
public final class Jdom2TreeNode implements TreeNode {

    /** The wrapper element a translated fragment is parsed inside; never appears in any output. */
    private static final String FRAGMENT_ROOT = "bookloom-fragment";

    /**
     * The line separator every serialization through this adapter uses.
     *
     * <p>Pinned because {@link Format#getRawFormat()} does <strong>not</strong> mean "emit the bytes as they were
     * read": measured on JDOM2 2.0.6.1, its line separator defaults to {@code \r\n}, so a text node holding a
     * single line feed re-serializes with two characters where the source had one. That reaches three places this
     * change makes load-bearing — a segment's {@code sourceInner}, the {@code sourceHash} taken over it, and every
     * atomic protected span's mapped fragment, which the requirement <em>Replace a segment's protected spans with
     * placeholder tokens</em> requires to be the <em>exact</em> source fragment. This is the counterpart of the
     * output settings {@code XhtmlParser} already pins on the jsoup side.
     */
    private static final Format RAW_WITH_SOURCE_LINE_FEEDS = rawFormatWithSourceLineFeeds();

    private static Format rawFormatWithSourceLineFeeds() {
        final Format format = Format.getRawFormat();
        format.setLineSeparator(LineSeparator.NL);
        return format;
    }

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

    /**
     * Tests {@link CDATA} before {@link Text}, because JDOM2's {@code CDATA} is itself a {@code Text} subclass —
     * the reverse order would report a CDATA section as {@link TreeNodeType#TEXT}, and {@link #ownText()} would
     * decode it, which gets re-escaped on restore and destroys the CDATA form (ADR-0031, design.md D2).
     */
    @Override
    public TreeNodeType type() {
        if (content instanceof CDATA) {
            return TreeNodeType.CDATA;
        }
        if (content instanceof Text) {
            return TreeNodeType.TEXT;
        }
        if (content instanceof Element) {
            return TreeNodeType.ELEMENT;
        }
        return TreeNodeType.OTHER;
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
        return new XMLOutputter(RAW_WITH_SOURCE_LINE_FEEDS).outputString(List.of(content));
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
     *
     * <p>{@link #parseFragment} also carries the source document's internal DTD subset into the wrapper, so a
     * restored reference the source itself declared — the common one is the entity named {@code nbsp}, the
     * non-breaking space — survives as a reference instead of failing "the entity was referenced, but not
     * declared"; without it, every FB2 segment carrying one (10,377 of them in the surveyed corpus) would be
     * impossible to write back. A reference the document declares with a {@code SYSTEM}/{@code PUBLIC} identifier
     * still parses under this carried subset — measured, nothing is fetched and the reference is left unresolved in
     * the output — so the offline invariant does not depend on the parse refusing; it holds because
     * {@link SecureXml} never lets an external general entity resolve. A reference the document does not declare at
     * all is still a failure, exactly as before.
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

    /**
     * Composed from {@link Element#getQualifiedName()} (not {@link Element#getName()}, which is the local name and
     * would drop a namespace prefix), the namespaces this element itself introduces, and every attribute — none of
     * which any name-or-namespace call surfaces on its own; omitting the attributes silently drops {@code l:href}
     * from every FB2 footnote anchor.
     */
    @Override
    public String openMarkup() {
        if (!(content instanceof Element element)) {
            throw new IllegalStateException("Only an element has an opening tag");
        }
        final StringBuilder markup = new StringBuilder("<").append(element.getQualifiedName());
        for (final Namespace namespace : element.getNamespacesIntroduced()) {
            appendDeclaration(markup, namespace);
        }
        for (final Attribute attribute : element.getAttributes()) {
            markup.append(' ')
                    .append(attribute.getQualifiedName())
                    .append("=\"")
                    .append(escapeAttributeValue(attribute.getValue()))
                    .append('"');
        }
        return markup.append('>').toString();
    }

    @Override
    public String closeMarkup() {
        if (!(content instanceof Element element)) {
            throw new IllegalStateException("Only an element has a closing tag");
        }
        return "</" + element.getQualifiedName() + ">";
    }

    /**
     * Escapes {@code &}, {@code <}, {@code >} and {@code "} for use inside a double-quoted attribute value, plus
     * the three whitespace characters an XML parser would otherwise normalize away there: without the numeric
     * escapes, a value containing a line feed comes back as a plain space when the composed tag is re-parsed, so
     * the round trip would not be an identity.
     */
    private static String escapeAttributeValue(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "&#xA;")
                .replace("\r", "&#xD;")
                .replace("\t", "&#x9;");
    }

    private static List<Content> parseFragment(Element scope, String replacementMarkup) {
        final String wrapped = internalSubsetDeclaration(scope) + "<" + FRAGMENT_ROOT + namespaceDeclarations(scope)
                + ">" + replacementMarkup + "</" + FRAGMENT_ROOT + ">";
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

    /**
     * The DOCTYPE to prepend to the fragment wrapper, carrying {@code scope}'s owning document's internal DTD
     * subset verbatim, or the empty string when there is none to carry. Every step is guarded because each can
     * legitimately be absent: a detached element has no owning {@link Document}, a document may declare no
     * {@link DocType} at all, and one that does may still carry no internal subset (an external-DTD-only
     * declaration).
     */
    private static String internalSubsetDeclaration(Element scope) {
        final Document owner = scope.getDocument();
        if (owner == null) {
            return "";
        }
        final DocType docType = owner.getDocType();
        if (docType == null) {
            return "";
        }
        final String internalSubset = docType.getInternalSubset();
        if (internalSubset == null || internalSubset.isBlank()) {
            return "";
        }
        return "<!DOCTYPE " + FRAGMENT_ROOT + " [" + internalSubset + "]>";
    }

    private static String namespaceDeclarations(Element scope) {
        final StringBuilder declarations = new StringBuilder();
        for (final Namespace namespace : scope.getNamespacesInScope()) {
            appendDeclaration(declarations, namespace);
        }
        return declarations.toString();
    }

    /**
     * Emits one declaration, including the <strong>undeclaration</strong> {@code xmlns=""} — an element that
     * introduces the empty-prefix, empty-URI namespace is one the source wrote {@code xmlns=""} on, deliberately
     * taking it back out of the enclosing default namespace. Measured before this was allowed through: a
     * {@code <foo xmlns="" bar="1">} inside a FictionBook paragraph captured as {@code <foo bar="1">}, and the
     * fragment wrapper {@link #parseFragment} builds carries the enclosing FictionBook default namespace, so the
     * restored element silently moved <em>into</em> it — a changed element identity that a canonical-XML
     * comparison sees and no prefix-based comparison can.
     *
     * <p>A <em>prefixed</em> namespace with an empty URI is still skipped: undeclaring a prefix ({@code
     * xmlns:p=""}) is XML 1.1 syntax that an XML 1.0 parser rejects, so emitting it would produce a fragment that
     * cannot be parsed back.
     */
    private static void appendDeclaration(StringBuilder declarations, Namespace namespace) {
        if (Namespace.XML_NAMESPACE.equals(namespace)) {
            return;
        }
        final String prefix = namespace.getPrefix();
        if (!prefix.isEmpty() && namespace.getURI().isEmpty()) {
            return;
        }
        declarations
                .append(prefix.isEmpty() ? " xmlns=\"" : " xmlns:" + prefix + "=\"")
                .append(namespace.getURI())
                .append('"');
    }
}
