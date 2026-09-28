package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.Fb2Reader;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.OpenMarkdownRegistry;
import ua.bookloom.document.txt.OpenTxtRegistry;
import ua.bookloom.document.txt.TxtInspection;

/**
 * {@link ua.bookloom.document.epub.EpubInspection#cover} and {@link Fb2Inspection#cover} (task 4.3): the four
 * historical EPUB rules in order, FB2's coverpage-to-binary resolution, and no cover — never a failure — for
 * Markdown, TXT, and a reference that names nothing this archive actually holds.
 *
 * <p>Each scenario opens a real book through its reader — the same read path {@code DocumentPort#open} takes —
 * rather than fabricating parsed state by hand, so what is proven is what a cover lookup sees on an actually
 * opened document, matching how {@code BookInspectorService} will read it once task 4.5 wires {@code profile}.
 */
class BookInspectorServiceCoverTest {

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

    private static final String COVER_XHTML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Cover</title></head>
            <body><img src="art/front.jpg"/></body>
            </html>
            """;

    /** A 1x1 PNG, reused from {@link Fb2Fixtures}'s own cover binary sample. */
    private static final String COVER_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @TempDir
    private Path tempDir;

    private static String opfHeader(String extraMetadata) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Cover Fixture</dc:title>
                %s  </metadata>
                """.formatted(extraMetadata);
    }

    private static Document openEpub(EpubTestFactory.ReaderAndInspection pair, Path source) {
        return pair.reader().read(source);
    }

    @Test
    void cover_epub3CoverImageProperty_resolvesManifestItem() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfHeader("") + """
                                  <manifest>
                                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                    <item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"
                                          properties="cover-image"/>
                                  </manifest>
                                  <spine><itemref idref="c01"/></spine>
                                </package>
                                """)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .binaryEntry("OEBPS/images/cover.jpg", "jpeg-bytes".getBytes(StandardCharsets.UTF_8))
                .writeTo(tempDir.resolve("epub3-cover.epub"));

        final Document document = openEpub(pair, epub);
        final Optional<CoverImage> cover = pair.inspection().cover(document);

        assertThat(cover).isPresent();
        assertThat(cover.get().resourcePath()).isEqualTo("images/cover.jpg");
        assertThat(cover.get().mediaType()).isEqualTo("image/jpeg");
        assertThat(cover.get().bytes()).isEqualTo("jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cover_epub2MetaContentAsManifestId_resolvesThatItemsHref() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfHeader("    <meta name=\"cover\" content=\"cover-img\"/>\n") + """
                                  <manifest>
                                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                    <item id="cover-img" href="cover.png" media-type="image/png"/>
                                  </manifest>
                                  <spine><itemref idref="c01"/></spine>
                                </package>
                                """)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .binaryEntry("OEBPS/cover.png", "png-bytes".getBytes(StandardCharsets.UTF_8))
                .writeTo(tempDir.resolve("epub2-meta-id.epub"));

        final Document document = openEpub(pair, epub);
        final Optional<CoverImage> cover = pair.inspection().cover(document);

        assertThat(cover).isPresent();
        assertThat(cover.get().resourcePath()).isEqualTo("cover.png");
        assertThat(cover.get().mediaType()).isEqualTo("image/png");
    }

    @Test
    void cover_epub2MetaContentAsHref_resolvesDirectly() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry(
                        "OEBPS/content.opf",
                        opfHeader("    <meta name=\"cover\" content=\"images/cover.png\"/>\n") + """
                                  <manifest>
                                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                  </manifest>
                                  <spine><itemref idref="c01"/></spine>
                                </package>
                                """)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .binaryEntry("OEBPS/images/cover.png", "href-bytes".getBytes(StandardCharsets.UTF_8))
                .writeTo(tempDir.resolve("epub2-meta-href.epub"));

        final Document document = openEpub(pair, epub);
        final Optional<CoverImage> cover = pair.inspection().cover(document);

        assertThat(cover).isPresent();
        assertThat(cover.get().resourcePath()).isEqualTo("images/cover.png");
    }

    @Test
    void cover_epub2GuideCoverReference_resolvesFirstImageOfGuideDocument() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfHeader("") + """
                                  <manifest>
                                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                    <item id="cover-doc" href="cover.xhtml" media-type="application/xhtml+xml"/>
                                  </manifest>
                                  <spine><itemref idref="c01"/></spine>
                                  <guide><reference type="cover" href="cover.xhtml" title="Cover"/></guide>
                                </package>
                                """)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .entry("OEBPS/cover.xhtml", COVER_XHTML)
                .binaryEntry("OEBPS/art/front.jpg", "front-bytes".getBytes(StandardCharsets.UTF_8))
                .writeTo(tempDir.resolve("epub2-guide.epub"));

        final Document document = openEpub(pair, epub);
        final Optional<CoverImage> cover = pair.inspection().cover(document);

        assertThat(cover).isPresent();
        assertThat(cover.get().resourcePath()).isEqualTo("art/front.jpg");
    }

    // <meta name="cover" content="missing-id"/> names neither a manifest id nor a resolvable href, and there is no
    // guide — every rule falls through, no cover is reported, and the book still opens normally.
    @Test
    void cover_epub2MetaContentMatchesNothing_noCoverAndBookStillOpens() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final Path epub = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfHeader("    <meta name=\"cover\" content=\"missing-id\"/>\n") + """
                                  <manifest>
                                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                                  </manifest>
                                  <spine><itemref idref="c01"/></spine>
                                </package>
                                """)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(tempDir.resolve("epub2-missing-id.epub"));

        final Document document = openEpub(pair, epub);
        final Optional<CoverImage> cover = pair.inspection().cover(document);

        assertThat(cover).isEmpty();
        final Result<Document> reopened = DocumentServices.newService().open(epub);
        assertThat(reopened.isOk()).isTrue();
    }

    @Test
    void cover_fb2Coverpage_resolvesBinaryToDecodedBytes() {
        final byte[] expectedBytes = Base64.getDecoder().decode(COVER_BASE64);
        final String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0"
                             xmlns:l="http://www.w3.org/1999/xlink">
                  <description>
                    <title-info>
                      <book-title>Sample</book-title>
                      <coverpage><image l:href="#cover.jpg"/></coverpage>
                    </title-info>
                  </description>
                  <body><section><p>Hello world.</p></section></body>
                  <binary id="cover.jpg" content-type="image/jpeg">%s</binary>
                </FictionBook>
                """.formatted(COVER_BASE64);
        final Path fb2 = Fb2Fixtures.writeFb2(tempDir.resolve("coverpage.fb2"), xml, StandardCharsets.UTF_8);
        final OpenFb2Registry registry = new OpenFb2Registry();
        final Fb2Reader reader = new Fb2Reader(registry);
        final Fb2Inspection inspection = new Fb2Inspection(registry);

        final Document document = reader.read(fb2);
        final Optional<CoverImage> cover = inspection.cover(document);

        assertThat(cover).isPresent();
        assertThat(cover.get().mediaType()).isEqualTo("image/jpeg");
        assertThat(cover.get().bytes()).isEqualTo(expectedBytes);
    }

    @Test
    void cover_markdown_alwaysEmpty() {
        final Path markdown = write(tempDir.resolve("notes.md"), "# Title\n\nBody.\n");
        final Document document = okDocument(DocumentServices.newService().open(markdown));

        assertThat(new MarkdownInspection(new OpenMarkdownRegistry()).cover(document))
                .isEmpty();
    }

    @Test
    void cover_txt_alwaysEmpty() {
        final Path notes = write(tempDir.resolve("notes.txt"), "Just some plain notes.\n");
        final Document document = okDocument(DocumentServices.newService().open(notes));

        assertThat(new TxtInspection(new OpenTxtRegistry()).cover(document)).isEmpty();
    }

    private static Document okDocument(Result<Document> result) {
        assertThat(result.isOk()).isTrue();
        return Objects.requireNonNull(result.data(), "data");
    }

    private static Path write(Path destination, String content) {
        try {
            Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
