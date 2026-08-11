package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * {@code XhtmlParser}'s charset detection and pinned output settings (task 2.6, FR-DOC-EPUB-5).
 */
class XhtmlParserTest {

    @Test
    void parse_explicitEncodingDeclaration_isHonoured() {
        final String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><html><body><p>Text.</p></body></html>";

        final Document doc = XhtmlParser.parse(xml.getBytes(StandardCharsets.ISO_8859_1), "unit.xhtml");

        assertThat(doc.outputSettings().charset()).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void parse_noEncodingDeclaration_defaultsToUtf8() {
        final String xml = "<html><body><p>Text.</p></body></html>";

        final Document doc = XhtmlParser.parse(xml.getBytes(StandardCharsets.UTF_8), "unit.xhtml");

        assertThat(doc.outputSettings().charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void parse_unsupportedEncodingDeclaration_fallsBackToUtf8() {
        final String xml =
                "<?xml version=\"1.0\" encoding=\"not-a-real-charset\"?><html><body><p>Text.</p></body></html>";

        final Document doc = XhtmlParser.parse(xml.getBytes(StandardCharsets.UTF_8), "unit.xhtml");

        assertThat(doc.outputSettings().charset()).isEqualTo(StandardCharsets.UTF_8);
    }

    // Covers: FR-DOC-EPUB-5 — output settings are pinned so whitespace is not normalized on serialization.
    @Test
    void parse_outputSettings_arePinnedForAFaithfulRoundTrip() {
        final String xml = "<html><body><p>Text.</p></body></html>";

        final Document doc = XhtmlParser.parse(xml.getBytes(StandardCharsets.UTF_8), "unit.xhtml");

        assertThat(doc.outputSettings().prettyPrint()).isFalse();
        assertThat(doc.outputSettings().syntax()).isEqualTo(Document.OutputSettings.Syntax.xml);
    }
}
