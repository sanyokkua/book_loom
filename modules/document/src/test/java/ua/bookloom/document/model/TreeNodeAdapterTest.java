package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.Objects;
import org.jdom2.Comment;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.junit.jupiter.api.Test;

/**
 * {@link TreeNode#openMarkup()} and {@link TreeNode#closeMarkup()} on both adapters — the two methods masking uses
 * to split an inline element into its paired placeholder group (design.md D3). Both adapters are exercised here, in
 * one file, for the same reason {@link Jdom2TreeNodeTest}'s class Javadoc gives for the walker rule: the
 * abstraction exists so the composition rule is written exactly once, and two implementations that quietly
 * disagree defeat the point of having it.
 */
class TreeNodeAdapterTest {

    private static final String ROOT_DECLARED_NAMESPACE_XML = "<root xmlns=\"http://x\" xmlns:l=\"http://l\">"
            + "<p>Div <a l:href=\"#n1\" type=\"note\">1</a> tut</p>"
            + "</root>";

    private static final String LOCALLY_REDECLARED_NAMESPACE_XML =
            "<root xmlns=\"http://r\">" + "<p>Слово <z:mark xmlns:z=\"http://z\">слово</z:mark> тут.</p>" + "</root>";

    private static Element parseRoot(String xml) {
        try {
            return Objects.requireNonNull(
                    SecureXml.builder().build(new StringReader(xml)).getRootElement());
        } catch (Exception e) {
            throw new IllegalStateException("fixture is not parseable", e);
        }
    }

    private static Element childElement(Element parent, String localName) {
        return parent.getChildren().stream()
                .filter(child -> localName.equals(child.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no child element named " + localName));
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jsoupElementWithQuotedAttributes_normalizesQuotingAndAttributeNameCase() {
        final var body = XhtmlTrees.body("<p>See <a href='ch2.xhtml#top' ID='x1'>chapter two</a>.</p>");
        final var anchor = Objects.requireNonNull(body.selectFirst("a"));

        // The source spells single-quoted attributes and an upper-case ID; jsoup has already normalized both
        // before openMarkup() is ever called, so this is "as the parser records it", never byte-identical to the
        // source (design.md D3) — intentional, not a defect to fix.
        assertThat(JsoupTreeNode.of(anchor).openMarkup()).isEqualTo("<a href=\"ch2.xhtml#top\" id=\"x1\">");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jsoupElementWithQuotedAttributes_composesClosingTag() {
        final var body = XhtmlTrees.body("<p>See <a href='ch2.xhtml#top' ID='x1'>chapter two</a>.</p>");
        final var anchor = Objects.requireNonNull(body.selectFirst("a"));

        assertThat(JsoupTreeNode.of(anchor).closeMarkup()).isEqualTo("</a>");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jsoupElementWithNoAttributes_composesBareOpeningTag() {
        final var body = XhtmlTrees.body("<p>Емфаза: <em>слово</em>.</p>");
        final var emphasis = Objects.requireNonNull(body.selectFirst("em"));

        assertThat(JsoupTreeNode.of(emphasis).openMarkup()).isEqualTo("<em>");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jsoupElementWithNoAttributes_composesBareClosingTag() {
        final var body = XhtmlTrees.body("<p>Емфаза: <em>слово</em>.</p>");
        final var emphasis = Objects.requireNonNull(body.selectFirst("em"));

        assertThat(JsoupTreeNode.of(emphasis).closeMarkup()).isEqualTo("</em>");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jsoupTextNode_throwsIllegalStateException() {
        final var body = XhtmlTrees.body("<p>Text<!-- c --></p>");
        final var paragraph = Objects.requireNonNull(body.selectFirst("p"));
        final var textNode = paragraph.childNode(0);

        assertThatThrownBy(() -> JsoupTreeNode.of(textNode).openMarkup()).isInstanceOf(IllegalStateException.class);
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jsoupCommentNode_throwsIllegalStateException() {
        final var body = XhtmlTrees.body("<p>Text<!-- c --></p>");
        final var paragraph = Objects.requireNonNull(body.selectFirst("p"));
        final var commentNode = paragraph.childNode(1);

        assertThatThrownBy(() -> JsoupTreeNode.of(commentNode).closeMarkup()).isInstanceOf(IllegalStateException.class);
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jdom2NamespacedAttributeDeclaredOnRoot_omitsXmlnsDeclaration() {
        final Element root = parseRoot(ROOT_DECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element anchor = childElement(paragraph, "a");

        // The "l" prefix is declared on <root>, not on <a> itself, so getNamespacesIntroduced() is empty here
        // (design.md D3) and the composed opening tag carries no xmlns declaration of its own.
        assertThat(Jdom2TreeNode.of(anchor).openMarkup()).isEqualTo("<a l:href=\"#n1\" type=\"note\">");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jdom2NamespacedAttributeElement_composesClosingTag() {
        final Element root = parseRoot(ROOT_DECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element anchor = childElement(paragraph, "a");

        assertThat(Jdom2TreeNode.of(anchor).closeMarkup()).isEqualTo("</a>");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jdom2PrefixedElementRedeclaringNamespaceLocally_includesXmlnsDeclarationAndPrefix() {
        final Element root = parseRoot(LOCALLY_REDECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element mark = childElement(paragraph, "mark");

        // Declaring xmlns:z on <mark> itself puts it in getNamespacesIntroduced() (design.md D3), unlike the
        // root-declared case above, and the prefix comes from getQualifiedName(), not getName().
        assertThat(Jdom2TreeNode.of(mark).openMarkup()).isEqualTo("<z:mark xmlns:z=\"http://z\">");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jdom2PrefixedElement_keepsPrefixInQualifiedName() {
        final Element root = parseRoot(LOCALLY_REDECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element mark = childElement(paragraph, "mark");

        assertThat(Jdom2TreeNode.of(mark).closeMarkup()).isEqualTo("</z:mark>");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void openMarkup_jdom2TextNode_throwsIllegalStateException() {
        final TreeNode text = Jdom2TreeNode.of(new Text("x"));

        assertThatThrownBy(text::openMarkup).isInstanceOf(IllegalStateException.class);
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void closeMarkup_jdom2CommentNode_throwsIllegalStateException() {
        final TreeNode comment = Jdom2TreeNode.of(new Comment("c"));

        assertThatThrownBy(comment::closeMarkup).isInstanceOf(IllegalStateException.class);
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void replaceChildren_composedMarkupRootDeclaredNamespace_reparsesWithPrefixAndAttributeIntact() {
        final Element root = parseRoot(ROOT_DECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element anchor = childElement(paragraph, "a");
        final TreeNode anchorNode = Jdom2TreeNode.of(anchor);
        final String composed = anchorNode.openMarkup() + "one" + anchorNode.closeMarkup();
        final int index = paragraph.getContent().indexOf(anchor);

        // parseFragment's synthetic wrapper carries getNamespacesInScope() of the replacement site, so the "l"
        // prefix resolves on re-parse even though the composed token above carries no xmlns declaration of its
        // own (design.md D3) — this is what proves the root-declared case actually round-trips, not merely that
        // openMarkup() looks right in isolation.
        Jdom2TreeNode.of(paragraph).replaceChildren(index, index + 1, composed);

        final Element reparsed = childElement(paragraph, "a");
        assertThat(reparsed.getQualifiedName()).isEqualTo("a");
        assertThat(reparsed.getAttributeValue("href", Namespace.getNamespace("http://l")))
                .isEqualTo("#n1");
        assertThat(reparsed.getAttributeValue("type")).isEqualTo("note");
        assertThat(reparsed.getText()).isEqualTo("one");
    }

    // WHEN an element inside a segment's content encloses content, the system
    // SHALL emit its opening tag with every attribute and namespace declaration its parser records, and its
    // closing tag, as the fragments of a paired placeholder group.
    @Test
    void replaceChildren_composedMarkupLocallyRedeclaredNamespace_reparsesWithPrefixAndDeclarationIntact() {
        final Element root = parseRoot(LOCALLY_REDECLARED_NAMESPACE_XML);
        final Element paragraph = childElement(root, "p");
        final Element mark = childElement(paragraph, "mark");
        final TreeNode markNode = Jdom2TreeNode.of(mark);
        final String composed = markNode.openMarkup() + "переклад" + markNode.closeMarkup();
        final int index = paragraph.getContent().indexOf(mark);

        // Here the composed token carries its own xmlns:z declaration (the locally-redeclared case), so this
        // round-trips even though "z" is not part of the replacement site's ambient scope.
        Jdom2TreeNode.of(paragraph).replaceChildren(index, index + 1, composed);

        final Element reparsed = childElement(paragraph, "mark");
        assertThat(reparsed.getQualifiedName()).isEqualTo("z:mark");
        assertThat(reparsed.getNamespace().getURI()).isEqualTo("http://z");
        assertThat(reparsed.getText()).isEqualTo("переклад");
    }
}
