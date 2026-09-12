package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.mask.RestoredContent;
import ua.bookloom.document.mask.Unmasker;

/**
 * Inline masking's FB2 half — the same shared masker as {@link TreeMaskingEpubTest}, driven over a JDOM2 tree
 * through {@link BlockSegmentWalker}, exercising the shapes only FictionBook can produce: a CDATA section, a
 * declared entity reference, and a namespaced attribute.
 */
class TreeMaskingFb2Test {

    private static final Namespace FB2 = Namespace.getNamespace("http://www.gribuser.ru/xml/fictionbook/2.0");

    /**
     * One book carrying every paragraph this file's tests need, declaring the {@code l} and {@code x} prefixes on
     * its root (so a fixture's opening token carries no {@code xmlns} declaration of its own, per design.md D3)
     * and the {@code nbsp} entity FB2 books commonly declare.
     */
    private static final String BOOK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE FictionBook [<!ENTITY nbsp "&#160;">]>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                         xmlns:l="http://www.w3.org/1999/xlink"
                         xmlns:x="http://example.com/x">
              <body>
                <section>
                  <p>Порівняй <![CDATA[a < b]]> тут</p>
                  <p>Розділ&nbsp;1</p>
                  <p>Дивись <a l:href="#n1" type="note">1</a> тут.</p>
                  <p>Це <x:mark>слово</x:mark> перекладне.</p>
                  <p>Він відчинив <emphasis>старі</emphasis> двері.</p>
                  <p>до <custom-run>тексту</custom-run> тут</p>
                  <p>Якщо x &lt; y, то</p>
                  <p>Виклич <code>List.of()</code> спочатку.</p>
                </section>
              </body>
            </FictionBook>
            """;

    private static Element body() {
        try {
            return Objects.requireNonNull(SecureXml.builder()
                    .build(new StringReader(BOOK))
                    .getRootElement()
                    .getChild("body", FB2));
        } catch (Exception e) {
            throw new IllegalStateException("fixture is not parseable", e);
        }
    }

    private static List<Segment> walk() {
        return BlockSegmentWalker.walk(Jdom2TreeNode.of(body()), "book.fb2#0", TreeDialect.FICTION_BOOK);
    }

    private static Segment segmentContaining(String needle) {
        return walk().stream()
                .filter(s -> s.sourceInner().contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no segment whose sourceInner contains " + needle));
    }

    // --- 3.6: an FB2 CDATA section and an FB2 entity reference are each one atomic token -------------------------

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_fb2CdataSection_mapCarriesItsCompleteFragment() {
        final Segment segment = segmentContaining("Порівняй");

        assertThat(segment.placeholders()).containsEntry("g0", "<![CDATA[a < b]]>");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void mask_fb2CdataSection_interiorNeverExposedInMaskedForm() {
        final Segment segment = segmentContaining("Порівняй");

        assertThat(segment.masked()).isEqualTo("Порівняй ⟦g0⟧ тут").doesNotContain("a < b");
    }

    // WHEN the book declares an entity in its header and uses it in a paragraph, THEN the reference is expanded
    // into character data before masking: nothing is protected, and the masked form carries the character itself.
    @Test
    void mask_fb2EntityReference_isExpandedIntoCharacterDataAndNeverMasked() {
        final Segment segment = segmentContaining("Розділ");

        assertThat(segment.placeholders()).isEmpty();
        assertThat(segment.masked()).isEqualTo("Розділ\u00A01");
        assertThat(segment.sourceInner()).isEqualTo("Розділ\u00A01");
    }

    // WHEN a node inside a segment's content is a protected
    // span, the system SHALL emit exactly one token whose fragment is its complete serialized form and SHALL
    // expose no part of its interior.
    @Test
    void unmask_fb2CdataSection_survivesMaskThenRestoreCycleAsCdataNotEscapedText() {
        final Segment segment = segmentContaining("Порівняй");

        final RestoredContent restored = Unmasker.restore(BookFormat.FB2, segment, segment.masked());

        assertThat(restored.text()).contains("<![CDATA[a < b]]>").doesNotContain("a &lt; b");
    }

    // --- 3.7: an FB2 element with children is a paired opening/closing token --------------------------------------

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_fb2NoteAnchor_namespacedAttributeCarriedInOpeningToken() {
        final Segment segment = segmentContaining("Дивись");

        assertThat(segment.placeholders()).containsEntry("g0", "<a l:href=\"#n1\" type=\"note\">");
    }

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_fb2PrefixedElement_keepsPrefixInBothTokens() {
        final Segment segment = segmentContaining("слово");

        assertThat(segment.masked()).isEqualTo("Це ⟦g0⟧слово⟦g1⟧ перекладне.");
        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "<x:mark>", "g1", "</x:mark>"));
    }

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_fb2InlineEmphasis_isPairedTheSameWayAsEpub() {
        final Segment segment = segmentContaining("Він відчинив");

        assertThat(segment.masked()).isEqualTo("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");
    }

    // --- B1: FictionBook's <code> is a prose style, not a code element, so it is paired rather than atomic ---------

    // WHEN an element inside a segment's content has children and is not a
    // protected span, the system SHALL emit a paired opening and closing token with its children masked between
    // them.
    @Test
    void mask_fb2InlineCodeSpanInsideProse_isPairedNotAtomic() {
        final Segment segment = segmentContaining("Виклич");

        assertThat(segment.masked()).isEqualTo("Виклич ⟦g0⟧List.of()⟦g1⟧ спочатку.");
        assertThat(segment.placeholders()).isEqualTo(Map.of("g0", "<code>", "g1", "</code>"));
    }

    // --- 3.8: an FB2 element name unknown to the FictionBook schema is still masked --------------------------------

    // WHEN a node inside a segment's content is not character data, the system SHALL mask it
    // whatever element name it carries.
    @Test
    void mask_unknownFb2ElementName_isStillMasked() {
        final Segment segment = segmentContaining("тексту");

        assertThat(segment.masked()).isEqualTo("до ⟦g0⟧тексту⟦g1⟧ тут");
    }

    // --- 3.10: FB2's character data reaches the model decoded, not as escaped XML syntax ----------------------------

    // IF a segment's format expresses inline structure as markup, THEN the system SHALL
    // present its character data decoded, with no entity or character-reference syntax.
    @Test
    void mask_fb2EscapedLessThanSign_reachesTheModelAsAPlainLessThanSign() {
        final Segment segment = segmentContaining("Якщо");

        assertThat(segment.masked()).isEqualTo("Якщо x < y, то");
    }
}
