package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Text;
import org.jdom2.input.SAXBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Tag;

/**
 * The mask-then-restore identity comparator for the two tree-shaped formats — task 9.2's whole point.
 *
 * <p><strong>Why not string equality.</strong> A segment's {@code sourceInner} is composed by concatenating each
 * run node's own serialization ({@code TreeNode#markup()}), while a restored fragment is composed by splicing the
 * placeholder map's captured {@code openMarkup()}/{@code closeMarkup()} tokens back around the (identical) inner
 * text. Measured on FB2: JDOM2's {@code XMLOutputter} stamps every namespace in scope onto an element serialized
 * on its own (so a fragment carrying {@code <a l:href="…">} re-parses with an inline {@code xmlns:l} declaration
 * that was never there when the same element's opening tag was composed via {@code getNamespacesIntroduced()}
 * alone), so the two strings differ while denoting the same document. The fix is what {@code Jdom2TreeNode
 * #parseFragment} already does for a real write: wrap the fragment in a synthetic element carrying the same
 * namespace declarations, parse it with the same parser the format uses, and compare trees rather than text.
 *
 * <p>The FB2 wrapper declares the FictionBook namespace always, plus every prefix the fragment itself uses. A
 * hard-coded {@code xmlns:l} was tried first and is not enough: measured against the 213-book corpus, real
 * FictionBook files declare the XLink prefix as either {@code l} or {@code xlink}, and one book's note anchors use
 * {@code xlink:href}, which made this comparator fail to parse a fragment production restores correctly.
 *
 * <p>EPUB carries no such namespace-stamping hazard — jsoup's HTML parser is not namespace-aware at all — but the
 * same re-parse-and-compare shape is used uniformly, both because the frozen requirement asks for canonical-equal
 * on both tree formats and because jsoup's own self-close bookkeeping ({@code Tag#SeenSelfClose}) is normalized
 * here the same defensive way {@code EpubCanonicalAssert} normalizes it for the whole-document comparison.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MaskRestoreCanonicalAssert {

    private static final String FICTION_BOOK_NAMESPACE = "http://www.gribuser.ru/xml/fictionbook/2.0";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";
    private static final String FRAGMENT_WRAPPER = "bookloom-fragment";

    /**
     * Asserts that {@code actual} — a restored EPUB fragment — is canonical-XHTML-equal to {@code expected}, its
     * segment's own {@code sourceInner}.
     *
     * @param expected the segment's source content
     * @param actual the content {@link ua.bookloom.document.DocumentService#unmask} restored
     * @param context identifies which fixture and segment failed, if one does
     */
    static void assertEpubFragmentCanonicalEqual(String expected, String actual, String context) {
        assertThat(canonicalXhtmlFragment(actual)).as(context).isEqualTo(canonicalXhtmlFragment(expected));
    }

    /**
     * Asserts that {@code actual} — a restored FB2 fragment — is canonical-XML-equal to {@code expected}, its
     * segment's own {@code sourceInner}, once both are parsed inside the same synthetic namespace scope.
     *
     * @param expected the segment's source content
     * @param actual the content {@link ua.bookloom.document.DocumentService#unmask} restored
     * @param context identifies which fixture and segment failed, if one does
     */
    static void assertFb2FragmentCanonicalEqual(String expected, String actual, String context) {
        assertThat(canonicalFb2Fragment(actual)).as(context).isEqualTo(canonicalFb2Fragment(expected));
    }

    private static String canonicalXhtmlFragment(String fragment) {
        final Document parsed = Jsoup.parseBodyFragment(fragment);
        clearSeenSelfCloseOnNonVoidElements(parsed);
        parsed.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml);
        return parsed.body().html();
    }

    /**
     * Duplicate of {@code XhtmlParser}'s identically-named method (design.md D2, and already duplicated once for
     * the same reason in {@code EpubCanonicalAssert}): clears {@code Tag#SeenSelfClose} on every non-void
     * element's tag so this comparator's own fresh parse never picks up self-close contagion from an unrelated
     * element sharing a tag name within the same parse.
     */
    private static void clearSeenSelfCloseOnNonVoidElements(Document doc) {
        for (final org.jsoup.nodes.Element element : doc.getAllElements()) {
            final Tag tag = element.tag();
            if (!tag.isEmpty()) {
                tag.clear(Tag.SeenSelfClose);
            }
        }
    }

    private static String canonicalFb2Fragment(String fragment) {
        final Element wrapper = parseFb2Fragment(fragment);
        final StringBuilder canonical = new StringBuilder();
        for (final Content child : wrapper.getContent()) {
            appendCanonicalChild(child, canonical, 0);
        }
        return canonical.toString();
    }

    /** A tag region, within which a {@code prefix:} may legitimately appear on the element or on an attribute. */
    private static final Pattern TAG_REGION = Pattern.compile("<[^>]*>");

    /** A namespace prefix in use, on an element name or on an attribute name. */
    private static final Pattern PREFIX_IN_USE = Pattern.compile("(?:^|[\\s</])([A-Za-z_][\\w.-]*):[A-Za-z_]");

    private static Element parseFb2Fragment(String fragment) {
        final String wrapped = "<" + FRAGMENT_WRAPPER + namespaceDeclarations(fragment) + ">" + fragment + "</"
                + FRAGMENT_WRAPPER + ">";
        try {
            final SAXBuilder builder = new SAXBuilder();
            builder.setExpandEntities(false);
            builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
            builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return builder.build(new StringReader(wrapped)).getRootElement();
        } catch (JDOMException | IOException e) {
            throw new AssertionError("restored fragment is not well-formed XML: " + fragment, e);
        }
    }

    /**
     * Declares the FictionBook default namespace plus every prefix {@code fragment} actually uses.
     *
     * <p>A hard-coded {@code xmlns:l} was not enough. Measured against the 213-book corpus, real FictionBook files
     * declare the XLink prefix as either {@code l} or {@code xlink} — {@code Jdom2TreeNode}'s own Javadoc says so —
     * and one book's note anchors use {@code xlink:href}, which made this comparator fail to parse a fragment
     * production restores correctly. Production's wrapper takes {@code getNamespacesInScope()} and never had the
     * problem; this is the harness catching up with it.
     *
     * <p>Every discovered prefix is bound to the same URI because the canonical form below ignores namespace
     * declarations entirely — all that matters here is that the parse succeeds. {@code xmlns} is filtered out
     * because declaring {@code xmlns:xmlns} is illegal.
     *
     * @param fragment the restored fragment about to be wrapped and parsed
     * @return the declarations to place on the wrapper element; never null
     */
    private static String namespaceDeclarations(String fragment) {
        final StringBuilder declarations = new StringBuilder(" xmlns=\"" + FICTION_BOOK_NAMESPACE + "\"");
        TAG_REGION
                .matcher(fragment)
                .results()
                .flatMap(tag -> PREFIX_IN_USE.matcher(tag.group()).results())
                .map(prefix -> prefix.group(1))
                .filter(prefix -> !"xmlns".equals(prefix))
                .distinct()
                .forEach(prefix -> declarations
                        .append(" xmlns:")
                        .append(prefix)
                        .append("=\"")
                        .append(XLINK_NAMESPACE)
                        .append('"'));
        return declarations.toString();
    }

    /**
     * A deterministic serialization of the tree — element qualified name, attributes in sorted order, then
     * children in document order, with text whitespace-collapsed — deliberately duplicated from {@code
     * Fb2CanonicalAssert}'s identically-shaped, privately-scoped canonicalizer rather than widened for reuse
     * across two comparators that serve different comparisons (a whole re-parsed document there, a single
     * namespace-wrapped fragment here).
     */
    private static void appendCanonical(Element element, StringBuilder canonical, int depth) {
        canonical.append("  ".repeat(depth)).append('<').append(element.getQualifiedName());
        element.getAttributes().stream()
                .map(a -> " " + a.getQualifiedName() + "=" + a.getValue())
                .sorted()
                .forEach(canonical::append);
        canonical.append(">\n");
        for (final Content child : element.getContent()) {
            appendCanonicalChild(child, canonical, depth);
        }
    }

    private static void appendCanonicalChild(Content child, StringBuilder canonical, int depth) {
        if (child instanceof Element childElement) {
            appendCanonical(childElement, canonical, depth + 1);
            return;
        }
        if (child instanceof Text text) {
            appendTextIfPresent(canonical, depth, text.getText());
            return;
        }
        if (child instanceof org.jdom2.Comment comment) {
            canonical
                    .append("  ".repeat(depth + 1))
                    .append("<!--")
                    .append(comment.getText())
                    .append("-->\n");
            return;
        }
        if (child instanceof org.jdom2.EntityRef entity) {
            canonical
                    .append("  ".repeat(depth + 1))
                    .append('&')
                    .append(entity.getName())
                    .append(";\n");
        }
    }

    /**
     * Appends a text node's characters <strong>exactly</strong> — no whitespace collapsing.
     *
     * <p>This comparator deliberately does not normalize whitespace, unlike {@code Fb2CanonicalAssert}, which does.
     * design.md's own risk list names the reason: the FB2 golden cannot catch a masking whitespace defect precisely
     * because it collapses first, and the mitigation it promises is that <em>this</em> check compares segment
     * content directly instead. A collapsing comparator here would inherit the very blind spot it exists to cover —
     * and not hypothetically: the {@code \r\n}-for-{@code \n} defect this change fixed in {@code
     * Jdom2TreeNode#markup} is a whitespace-only difference that a collapse would have waved straight through.
     *
     * <p>Every normalization a comparator applies is a class of defect it can no longer see, which is the lesson
     * ADR-0028 already recorded once for the golden comparison.
     */
    private static void appendTextIfPresent(StringBuilder canonical, int depth, String raw) {
        if (!raw.isEmpty()) {
            canonical.append("  ".repeat(depth + 1)).append(raw).append('\n');
        }
    }
}
