package ua.bookloom.document.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Segment;

/** An FB2 paragraph a reader sees as empty is no segment, the same rule the XHTML walk keeps. */
class InvisibleTextFb2SegmentationTest {

    private static final Namespace FB2 = Namespace.getNamespace("http://www.gribuser.ru/xml/fictionbook/2.0");

    private static List<Segment> walk(final String invisible) {
        final String book = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE FictionBook [<!ENTITY nbsp "&#160;">]>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <body><section><p>%s</p><p>Два.</p><empty-line/></section></body>
                </FictionBook>
                """.formatted(invisible);
        try {
            final Element body = Objects.requireNonNull(SecureXml.builder()
                    .build(new StringReader(book))
                    .getRootElement()
                    .getChild("body", FB2));
            return BlockSegmentWalker.walk(Jdom2TreeNode.of(body), "book.fb2#0", TreeDialect.FICTION_BOOK);
        } catch (Exception e) {
            throw new IllegalStateException("fixture is not parseable", e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"&nbsp;", "&#8203;", "&#65279;", "&#173;", "&nbsp; &#8203;"})
    void walk_paragraphOfInvisibleCharactersOnly_yieldsNoSegment(final String invisible) {
        final List<Segment> segments = walk(invisible);

        assertThat(segments).extracting(Segment::sourceInner).containsExactly("Два.");
        assertThat(segments).extracting(Segment::id).containsExactly("book.fb2#0:0");
    }
}
