package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.fixture.RefusalFixtures;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.OpenMarkdownRegistry;
import ua.bookloom.document.txt.OpenTxtRegistry;
import ua.bookloom.document.txt.TxtInspection;

/**
 * {@code BookInspectorService.inspect} (task 4.2): the façade over the four per-format inspections, plus the
 * unsupported-file detection PDF/DOCX/MOBI/leading bytes give when no format resolves at all.
 */
class BookInspectorServiceInspectTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter One</title></head>
            <body><p>Prose.</p></body>
            </html>
            """;

    private static final String ENCRYPTION_XML_ADEPT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#"
                        xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                        xmlns:adept="http://ns.adobe.com/adept">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                <ds:KeyInfo><adept:resource>urn:uuid:example</adept:resource></ds:KeyInfo>
                <enc:CipherData><enc:CipherReference URI="OEBPS/c01.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
            """;

    private static final String ENCRYPTION_XML_UNKNOWN_SCHEME = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="urn:example:some-drm"/>
                <enc:CipherData><enc:CipherReference URI="OEBPS/c01.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
            """;

    @TempDir
    private Path tempDir;

    private static String opf(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="%s" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Inspection Fixture</dc:title>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """.formatted(version);
    }

    private static Path epubWithVersion(Path destination, String version) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(version))
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(destination);
    }

    private static Path epubWithEncryption(Path destination, String encryptionXml) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("META-INF/encryption.xml", encryptionXml)
                .entry("OEBPS/content.opf", opf("2.0"))
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(destination);
    }

    private static BookInspectorService newService() {
        return new BookInspectorService(
                EpubTestFactory.newInspection(),
                new Fb2Inspection(new OpenFb2Registry()),
                new MarkdownInspection(new OpenMarkdownRegistry()),
                new TxtInspection(new OpenTxtRegistry()));
    }

    private static BookInspection inspectionOf(Path source) {
        final Result<BookInspection> result = newService().inspect(source);
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "data");
    }

    private static Result<Document> openWithDocumentService(Path source) {
        return DocumentServices.newService().open(source);
    }

    @Test
    void inspect_epubVersion2_readableNamedEpub2() {
        final BookInspection inspection = inspectionOf(epubWithVersion(tempDir.resolve("epub2.epub"), "2.0"));

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.READABLE);
        assertThat(inspection.formatVersion()).isEqualTo("EPUB 2.0");
    }

    @Test
    void inspect_epubVersion3_readableNamedEpub3() {
        final BookInspection inspection = inspectionOf(epubWithVersion(tempDir.resolve("epub3.epub"), "3.0"));

        assertThat(inspection.formatVersion()).isEqualTo("EPUB 3.0");
    }

    // an EPUB whose encryption.xml encrypts its content under an ADEPT KeyInfo is
    // reported protected and named by scheme, and opening it still fails as a validation error.
    @Test
    void inspect_adeptEncryptedContent_drmProtectedNamedAdept() {
        final Path epub = epubWithEncryption(tempDir.resolve("adept.epub"), ENCRYPTION_XML_ADEPT);

        final BookInspection inspection = inspectionOf(epub);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.DRM_PROTECTED);
        assertThat(inspection.encryptionScheme()).isEqualTo("Adobe ADEPT");
        assertThat(Objects.requireNonNull(openWithDocumentService(epub).error()).code())
                .isEqualTo(ErrorCode.validation);
    }

    // content encrypted under an algorithm this system does not recognize, with none of the
    // rights/license/sinf marker files present, is still reported protected — with no scheme named.
    @Test
    void inspect_unrecognizedEncryptedContent_drmProtectedWithNoScheme() {
        final BookInspection inspection =
                inspectionOf(epubWithEncryption(tempDir.resolve("unknown.epub"), ENCRYPTION_XML_UNKNOWN_SCHEME));

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.DRM_PROTECTED);
        assertThat(inspection.encryptionScheme()).isNull();
    }

    // an EPUB whose encryption.xml declares only font obfuscation is processed as an
    // ordinary readable book, never as protected.
    @Test
    void inspect_fontObfuscationOnly_readable() {
        final BookInspection inspection =
                inspectionOf(RefusalFixtures.fontObfuscationOnly(tempDir.resolve("fonts.epub")));

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.READABLE);
    }

    // a zipped FB2 whose member carries the zip encryption flag is reported protected and
    // named "ZIP encryption", without the raw file ever going through the throwing unpack path.
    @Test
    void inspect_zipEncryptedFb2_drmProtectedNamedZipEncryption() {
        final Path fb2Zip = Fb2Fixtures.writeEncryptedFb2Zip(tempDir.resolve("book.fb2.zip"));

        final BookInspection inspection = inspectionOf(fb2Zip);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.DRM_PROTECTED);
        assertThat(inspection.encryptionScheme()).isEqualTo("ZIP encryption");
        assertThat(Objects.requireNonNull(openWithDocumentService(fb2Zip).error())
                        .code())
                .isEqualTo(ErrorCode.validation);
    }

    // a file beginning with the PDF magic bytes is reported unsupported and named PDF,
    // and opening it still fails as a validation error.
    @Test
    void inspect_pdfFile_unsupportedNamedPdf() throws Exception {
        final Path pdf = tempDir.resolve("book.pdf");
        Files.write(pdf, "%PDF-1.7\n%rest of the file".getBytes(StandardCharsets.UTF_8));

        final BookInspection inspection = inspectionOf(pdf);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.UNSUPPORTED);
        assertThat(inspection.detectedType()).isEqualTo("PDF");
        assertThat(Objects.requireNonNull(openWithDocumentService(pdf).error()).code())
                .isEqualTo(ErrorCode.validation);
    }

    // a zip archive holding [Content_Types].xml is reported unsupported and named DOCX.
    @Test
    void inspect_docxFile_unsupportedNamedDocx() {
        final Path docx = new EpubZipBuilder()
                .entry("[Content_Types].xml", "<Types/>")
                .entry("word/document.xml", "<document/>")
                .writeTo(tempDir.resolve("report.docx"));

        final BookInspection inspection = inspectionOf(docx);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.UNSUPPORTED);
        assertThat(inspection.detectedType()).isEqualTo("DOCX");
    }

    // BOOKMOBI at byte 60 is reported unsupported and named MOBI.
    @Test
    void inspect_mobiFile_unsupportedNamedMobi() throws Exception {
        final byte[] bytes = new byte[68];
        Arrays.fill(bytes, (byte) ' ');
        System.arraycopy("BOOKMOBI".getBytes(StandardCharsets.US_ASCII), 0, bytes, 60, 8);
        final Path mobi = tempDir.resolve("book.mobi");
        Files.write(mobi, bytes);

        final BookInspection inspection = inspectionOf(mobi);

        assertThat(inspection.detectedType()).isEqualTo("MOBI");
    }

    // bytes matching none of the known magics are reported unsupported and named Unknown.
    @Test
    void inspect_unrecognizedBinaryFile_unsupportedNamedUnknown() throws Exception {
        final Path bin = tempDir.resolve("mystery.bin");
        final byte[] bytes = new byte[2048];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + 7);
        }
        Files.write(bin, bytes);

        final BookInspection inspection = inspectionOf(bin);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.UNSUPPORTED);
        assertThat(inspection.detectedType()).isEqualTo("Unknown");
    }

    // an EPUB truncated to half its length cannot even be inflated, so inspection answers
    // readable with no version rather than failing outright — open() is left to give the real reason.
    @Test
    void inspect_truncatedEpub_readableWithNoVersion() throws Exception {
        final byte[] fullBytes = Files.readAllBytes(epubWithVersion(tempDir.resolve("source.epub"), "3.0"));
        final Path broken = tempDir.resolve("broken.epub");
        Files.write(broken, Arrays.copyOf(fullBytes, fullBytes.length / 2));

        final BookInspection inspection = inspectionOf(broken);

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.READABLE);
        assertThat(inspection.formatVersion()).isNull();
        assertThat(Objects.requireNonNull(openWithDocumentService(broken).error())
                        .code())
                .isEqualTo(ErrorCode.validation);
    }

    @Test
    void inspect_fb2Fixture_readableNamedFb2() {
        final BookInspection inspection = inspectionOf(Fb2Fixtures.primary(tempDir.resolve("book.fb2")));

        assertThat(inspection.verdict()).isEqualTo(InspectionVerdict.READABLE);
        assertThat(inspection.detectedType()).isEqualTo("FB2");
    }
}
