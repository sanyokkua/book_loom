package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.model.CorruptContainerException;

/**
 * {@code OpfParser}'s manifest/spine/metadata parsing (task 2.5, FR-DOC-EPUB-1, FR-DOC-EPUB-2).
 */
class OpfParserTest {

    private static final String OPF_PATH = "OEBPS/content.opf";

    @Test
    void parse_validOpf_readsManifestSpineAndMetadataInSpineOrder() {
        final ParsedOpf opf = OpfParser.parse(
                opf("""
                                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
                                """, "<itemref idref=\"c02\"/><itemref idref=\"c01\"/>").getBytes(StandardCharsets.UTF_8),
                OPF_PATH);

        assertThat(opf.spineItems()).extracting(SpineItem::href).containsExactly("c02.xhtml", "c01.xhtml");
        assertThat(opf.dcLanguages()).containsExactly("en");
        assertThat(opf.title()).isEqualTo("Test Book");
        assertThat(opf.author()).isEqualTo("A. Author");
    }

    @Test
    void parse_manifestItemWithNoMediaType_defaultsToXhtml() {
        final ParsedOpf opf = OpfParser.parse(
                opf("<item id=\"c01\" href=\"c01.xhtml\"/>", "<itemref idref=\"c01\"/>")
                        .getBytes(StandardCharsets.UTF_8),
                OPF_PATH);

        assertThat(opf.spineItems().get(0).mediaType()).isEqualTo("application/xhtml+xml");
    }

    @Test
    void parse_noMetadataElement_reportsNoLanguageTitleOrAuthor() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """;

        final ParsedOpf opf = OpfParser.parse(xml.getBytes(StandardCharsets.UTF_8), OPF_PATH);

        assertThat(opf.dcLanguages()).isEmpty();
        assertThat(opf.title()).isNull();
        assertThat(opf.author()).isNull();
    }

    // WHERE an EPUB package nests its Dublin Core metadata elements inside a legacy
    // wrapper element rather than placing them directly under the metadata element, the system SHALL read them
    // from that nested location.
    @Test
    void parse_dublinCoreNestedInLegacyDcMetadataWrapper_readsTitleAuthorAndLanguage() {
        // Reproduces aliceDynamic.epub's content.opf verbatim: <package>'s default namespace is OPF and
        // <dc-metadata> is written unprefixed, so the wrapper itself inherits OPF while only its dc:-prefixed
        // children carry the Dublin Core namespace.
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
                  <metadata>
                    <dc-metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>Alice's Adventures in Wonderland</dc:title>
                      <dc:creator>Lewis Carroll</dc:creator>
                      <dc:language>en-GB</dc:language>
                    </dc-metadata>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """;

        final ParsedOpf opf = OpfParser.parse(xml.getBytes(StandardCharsets.UTF_8), OPF_PATH);

        assertThat(opf.dcLanguages()).containsExactly("en-GB");
        assertThat(opf.title()).isEqualTo("Alice's Adventures in Wonderland");
        assertThat(opf.author()).isEqualTo("Lewis Carroll");
    }

    // the system SHALL record the book's title by reading the location each format
    // provides for it, preferring a direct declaration over one nested in a legacy wrapper where both are
    // present.
    @Test
    void parse_directDcTitleAndNestedDcMetadataTitle_directWins() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Direct Title</dc:title>
                    <dc-metadata>
                      <dc:title>Nested Title</dc:title>
                    </dc-metadata>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """;

        final ParsedOpf opf = OpfParser.parse(xml.getBytes(StandardCharsets.UTF_8), OPF_PATH);

        assertThat(opf.title()).isEqualTo("Direct Title");
    }

    @Test
    void parse_missingManifest_isACorruptContainerFailure() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <spine><itemref idref="c01"/></spine>
                </package>
                """;

        assertThatThrownBy(() -> OpfParser.parse(xml.getBytes(StandardCharsets.UTF_8), OPF_PATH))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void parse_manifestItemMissingHref_isACorruptContainerFailure() {
        final String opf = opf("<item id=\"c01\"/>", "<itemref idref=\"c01\"/>");

        assertThatThrownBy(() -> OpfParser.parse(opf.getBytes(StandardCharsets.UTF_8), OPF_PATH))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void parse_missingSpine_isACorruptContainerFailure() {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <manifest><item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/></manifest>
                </package>
                """;

        assertThatThrownBy(() -> OpfParser.parse(xml.getBytes(StandardCharsets.UTF_8), OPF_PATH))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void parse_emptySpine_isACorruptContainerFailure() {
        final String opf = opf("<item id=\"c01\" href=\"c01.xhtml\" media-type=\"application/xhtml+xml\"/>", "");

        assertThatThrownBy(() -> OpfParser.parse(opf.getBytes(StandardCharsets.UTF_8), OPF_PATH))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void parse_spineReferencesUnknownManifestId_isACorruptContainerFailure() {
        final String opf = opf(
                "<item id=\"c01\" href=\"c01.xhtml\" media-type=\"application/xhtml+xml\"/>",
                "<itemref idref=\"missing\"/>");

        assertThatThrownBy(() -> OpfParser.parse(opf.getBytes(StandardCharsets.UTF_8), OPF_PATH))
                .isInstanceOf(CorruptContainerException.class);
    }

    @Test
    void parse_malformedXml_isACorruptContainerFailure() {
        final byte[] bytes = "not xml at all <<<".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> OpfParser.parse(bytes, OPF_PATH)).isInstanceOf(CorruptContainerException.class);
    }

    private static String opf(String manifestItems, String spineItemrefs) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>A. Author</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    %s
                  </manifest>
                  <spine>
                    %s
                  </spine>
                </package>
                """.formatted(manifestItems, spineItemrefs);
    }
}
