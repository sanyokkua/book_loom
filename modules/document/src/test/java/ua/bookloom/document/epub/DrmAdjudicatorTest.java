package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.document.model.DrmRefusedException;
import ua.bookloom.document.model.RawEntry;

/**
 * {@code DrmAdjudicator} deciding from the encrypted resource rather than from the algorithm URI (ADR-0026).
 *
 * <p>The corpus contains no genuinely DRM-protected book, so the refusal path has no natural example and is
 * provable only against hand-authored input like this.
 */
class DrmAdjudicatorTest {

    /** An OPF at {@code OPS/content.opf}, so manifest hrefs resolve against {@code OPS/} and cipher URIs do not. */
    private static ParsedOpf opfWith(ManifestItem... items) {
        return new ParsedOpf(
                "OPS/content.opf",
                List.of("en"),
                "Title",
                "Author",
                List.of(items),
                List.of(new SpineItem("c01", "c01.xhtml", "application/xhtml+xml")),
                new org.jdom2.Document(new org.jdom2.Element("package")));
    }

    private static RawEntry encryptionOf(String algorithm, String... cipherUris) {
        final StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                            xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                """);
        for (final String uri : cipherUris) {
            xml.append("""
                      <enc:EncryptedData>
                        <enc:EncryptionMethod Algorithm="%s"/>
                        <enc:CipherData><enc:CipherReference URI="%s"/></enc:CipherData>
                      </enc:EncryptedData>
                    """.formatted(algorithm, uri));
        }
        xml.append("</encryption>\n");
        return new RawEntry("META-INF/encryption.xml", 0, 0, xml.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void adjudicate_noEncryptionEntry_isANoOp() {
        assertThatCode(() -> DrmAdjudicator.adjudicate(null, opfWith())).doesNotThrowAnyException();
    }

    // Covers: FR-DOC-EPUB-7 — IF every encrypted resource resolves to a manifest item with a font media type,
    // THEN the book is processed normally whatever encryption algorithm is named.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://www.idpf.org/2008/embedding",
                "http://ns.adobe.com/pdf/enc#RC4",
                "http://ns.adobe.com/pdf/enc#RC",
                "urn:example:some-future-obfuscation"
            })
    void adjudicate_fontOnlyManifest_isAllowedWhateverTheAlgorithmIsCalled(String algorithm) {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/Charter-Roman.ttf", "application/vnd.ms-opentype"));

        assertThatCode(() -> DrmAdjudicator.adjudicate(encryptionOf(algorithm, "OPS/fonts/Charter-Roman.ttf"), opf))
                .doesNotThrowAnyException();
    }

    // Covers: EC-FONT-1 — a font declared under an alternative media type is still recognised as a font, because
    // the corpus's fonts split across three distinct media types and no single one is sufficient.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "application/vnd.ms-opentype",
                "application/x-font-otf",
                "application/x-font-ttf",
                "font/otf",
                "application/font-sfnt"
            })
    void adjudicate_fontUnderAnyDeclaredFontMediaType_isAllowed(String mediaType) {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/00111.otf", mediaType));

        assertThatCode(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://ns.adobe.com/pdf/enc#RC", "OPS/fonts/00111.otf"), opf))
                .doesNotThrowAnyException();
    }

    // Covers: EC-EPUB-1 — IF the encryption manifest declares an encrypted resource that is not a font, THEN the
    // whole book is refused as protected and no part of it is imported.
    @Test
    void adjudicate_contentDocumentEncrypted_isRefused() {
        final ParsedOpf opf = opfWith(new ManifestItem("chapter01.xhtml", "application/xhtml+xml"));

        assertThatThrownBy(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://www.idpf.org/2008/embedding", "OPS/chapter01.xhtml"), opf))
                .isInstanceOf(DrmRefusedException.class)
                .hasMessageContaining("content rather than a font");
    }

    // Covers: EC-EPUB-1 — IF an encrypted resource is absent from the package manifest, THEN the book is refused
    // rather than partially imported, because an unresolvable cipher reference is not something to guess about.
    @Test
    void adjudicate_encryptedResourceMissingFromTheManifest_isRefused() {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/Charter-Roman.ttf", "application/vnd.ms-opentype"));

        assertThatThrownBy(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://www.idpf.org/2008/embedding", "OPS/fonts/ghost.ttf"), opf))
                .isInstanceOf(DrmRefusedException.class)
                .hasMessageContaining("not declared in the package manifest");
    }

    /**
     * The two-path-base rule. The cipher URI is container-root-relative and the manifest href is OPF-relative;
     * 102 of 194 corpus books have a non-root OPF, so resolving both the same way mis-resolves the majority.
     */
    // Covers: EC-EPUB-1 — WHEN a cipher reference names a container-root path and the manifest declares the same
    // resource relative to a non-root OPF, THEN the two resolve to the same resource and the book is allowed.
    @Test
    void adjudicate_nonRootOpf_resolvesCipherAgainstTheContainerRootAndHrefAgainstTheOpfDirectory() {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/Charter-Roman.ttf", "application/vnd.ms-opentype"));

        assertThatCode(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://www.idpf.org/2008/embedding", "OPS/fonts/Charter-Roman.ttf"), opf))
                .doesNotThrowAnyException();
    }

    /** Resolving the cipher URI OPF-relatively would produce {@code OPS/OPS/fonts/…} and refuse a clean book. */
    @Test
    void adjudicate_cipherUriResolvedAgainstTheOpfDirectory_wouldNotMatch() {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/Charter-Roman.ttf", "application/vnd.ms-opentype"));

        assertThatThrownBy(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://www.idpf.org/2008/embedding", "fonts/Charter-Roman.ttf"), opf))
                .isInstanceOf(DrmRefusedException.class);
    }

    @Test
    void adjudicate_percentEscapedCipherUri_matchesItsManifestItem() {
        final ParsedOpf opf = opfWith(new ManifestItem("fonts/Charter Roman.ttf", "application/vnd.ms-opentype"));

        assertThatCode(() -> DrmAdjudicator.adjudicate(
                        encryptionOf("http://www.idpf.org/2008/embedding", "OPS/fonts/Charter%20Roman.ttf"), opf))
                .doesNotThrowAnyException();
    }

    @Test
    void adjudicate_encryptedDataWithNoCipherReference_isRefused() {
        final RawEntry entry = new RawEntry("META-INF/encryption.xml", 0, 0, """
                <?xml version="1.0" encoding="UTF-8"?>
                <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <enc:EncryptedData xmlns:enc="http://www.w3.org/2001/04/xmlenc#"/>
                </encryption>
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> DrmAdjudicator.adjudicate(entry, opfWith())).isInstanceOf(DrmRefusedException.class);
    }

    /**
     * A manifest we cannot read is a manifest that says something is encrypted and refuses to say what, so the
     * check fails closed rather than reporting a corrupt container (ADR-0026).
     */
    @Test
    void adjudicate_malformedEncryptionXml_failsClosedAsProtected() {
        final RawEntry entry =
                new RawEntry("META-INF/encryption.xml", 0, 0, "not xml <<<".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> DrmAdjudicator.adjudicate(entry, opfWith())).isInstanceOf(DrmRefusedException.class);
    }
}
