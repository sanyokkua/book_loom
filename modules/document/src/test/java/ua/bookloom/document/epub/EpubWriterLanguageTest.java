package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jsoup.Jsoup;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;

/**
 * {@code EpubWriter}'s language-rewriting scenarios (task 4.6, design.md D14 §1) — split out of
 * {@link EpubWriterTest} to keep that class under the project's file-length limit, against the same tiny ad-hoc
 * EPUB fixtures built with {@link EpubZipBuilder}.
 */
class EpubWriterLanguageTest {

    @TempDir
    private Path tempDir;

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final Namespace OPF_NAMESPACE = Namespace.getNamespace("http://www.idpf.org/2007/opf");

    // a declaration nested in the legacy dc-metadata wrapper is replaced, not duplicated.
    @Test
    void write_dcMetadataNestedLanguage_replacesNestedElementOnlyWithNoSecondElement() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithLegacyDcMetadataLanguage("en-GB"))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(dcLanguagesOf(output)).containsExactly("uk");
        assertThat(languageElementOccurrences(output)).isEqualTo(1);
    }

    // the dcterms:language meta follows the first dc:language when it carries the source language.
    @Test
    void write_dctermsLanguageMeta_followsSourceLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithDctermsLanguageMeta("en"))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(dctermsLanguageMetaOf(output)).isEqualTo("uk");
    }

    // the package element's own xml:lang attribute follows the source language.
    @Test
    void write_packageXmlLangAttribute_followsSourceLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithPackageLang("en", List.of("en")))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(packageXmlLangOf(output)).isEqualTo("uk");
    }

    // a chapter's html root and body attributes follow the run's source language.
    @Test
    void write_chapterRootAndBodyLanguageAttributes_followSourceLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en-US"), 1))
                .entry("OEBPS/c01.xhtml", chapterWithLangAttrs("en", "en", "en"))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "en", "uk");

        final org.jsoup.nodes.Document parsed = contentDocumentOf(output);
        assertThat(htmlOf(parsed).attr("xml:lang")).isEqualTo("uk");
        assertThat(htmlOf(parsed).attr("lang")).isEqualTo("uk");
        assertThat(parsed.body().attr("lang")).isEqualTo("uk");
    }

    // a language attribute naming another (uncatalogued) language is left untouched.
    @Test
    void write_chapterLanguageAttributeNamingAnotherLanguage_isKept() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapterWithLangAttrs("la", null, null))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(htmlOf(contentDocumentOf(output)).attr("xml:lang")).isEqualTo("la");
    }

    // the run's source language, not the package's, decides which attributes follow: a chapter carrying the
    // run's source language is rewritten even though the package declares something else, and the package's own
    // mismatched value is left alone.
    @Test
    void write_runSourceLanguageDecidesWhichAttributesFollow() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opfWithPackageLang("en", List.of("en")))
                .entry("OEBPS/c01.xhtml", chapterWithLangAttrs("uk", null, null))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk", "de");

        assertThat(htmlOf(contentDocumentOf(output)).attr("xml:lang")).isEqualTo("de");
        assertThat(packageXmlLangOf(output)).isEqualTo("en");
    }

    // without a source language the package's own declaration is used as the effective source.
    @Test
    void write_noSourceLanguage_usesPackagesDeclaredLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapterWithLangAttrs(null, "en", null))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(htmlOf(contentDocumentOf(output)).attr("lang")).isEqualTo("uk");
    }

    // a language set inside the body is kept: only the two root elements of a content document are ever rewritten.
    @Test
    void write_languageSetInsideTheBody_isKept() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>Chapter</title></head>
                        <body lang="en"><div lang="en"><p>Text.</p></div></body>
                        </html>
                        """)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "en", "uk");

        final org.jsoup.nodes.Document parsed = contentDocumentOf(output);
        assertThat(parsed.body().attr("lang")).isEqualTo("uk");
        assertThat(Objects.requireNonNull(parsed.selectFirst("div"), "div").attr("lang"))
                .isEqualTo("en");
    }

    private static byte[] contentOf(Path zip, String entryName) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if (entryName.equals(entry.getName())) {
                    return in.readAllBytes();
                }
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError("No entry named " + entryName + " in " + zip);
    }

    private static List<String> dcLanguagesOf(Path zip) {
        return OpfParser.parse(contentOf(zip, "OEBPS/content.opf"), "OEBPS/content.opf")
                .dcLanguages();
    }

    private static String rawOpfTextOf(Path zip) {
        return new String(contentOf(zip, "OEBPS/content.opf"), StandardCharsets.UTF_8);
    }

    /**
     * Counts every {@code <language>}/{@code <dc:language>} start tag, wherever it is nested, so a test can prove
     * exactly one language element exists — a check {@link #dcLanguagesOf(Path)} alone cannot make, since it
     * reports only the winning declaration under {@code OpfParser}'s direct-then-legacy precedence.
     */
    private static long languageElementOccurrences(Path zip) {
        final Matcher matcher = Pattern.compile("<(?:dc:)?language[ >]").matcher(rawOpfTextOf(zip));
        return matcher.results().count();
    }

    private static String dctermsLanguageMetaOf(Path zip) {
        final org.jdom2.Document opf = OpfParser.parse(contentOf(zip, "OEBPS/content.opf"), "OEBPS/content.opf")
                .jdomDocument();
        final Element metadata =
                Objects.requireNonNull(opf.getRootElement().getChild("metadata", OPF_NAMESPACE), "metadata");
        for (final Element meta : metadata.getChildren("meta", OPF_NAMESPACE)) {
            if ("dcterms:language".equals(meta.getAttributeValue("property"))) {
                return meta.getText();
            }
        }
        throw new AssertionError("no dcterms:language meta in " + zip);
    }

    private static String packageXmlLangOf(Path zip) {
        final org.jdom2.Document opf = OpfParser.parse(contentOf(zip, "OEBPS/content.opf"), "OEBPS/content.opf")
                .jdomDocument();
        return opf.getRootElement().getAttributeValue("lang", Namespace.XML_NAMESPACE);
    }

    private static org.jsoup.nodes.Document contentDocumentOf(Path zip) {
        return Jsoup.parse(new String(contentOf(zip, "OEBPS/c01.xhtml"), StandardCharsets.UTF_8));
    }

    private static org.jsoup.nodes.Element htmlOf(org.jsoup.nodes.Document document) {
        return Objects.requireNonNull(document.selectFirst("html"), "html");
    }

    private static String opf(List<String> dcLanguages, int chapterCount) {
        final StringBuilder languages = new StringBuilder();
        for (final String lang : dcLanguages) {
            languages.append("<dc:language>").append(lang).append("</dc:language>\n");
        }
        final StringBuilder manifest = new StringBuilder();
        final StringBuilder spine = new StringBuilder();
        for (int i = 1; i <= chapterCount; i++) {
            manifest.append(
                    "<item id=\"c0%d\" href=\"c0%d.xhtml\" media-type=\"application/xhtml+xml\"/>\n".formatted(i, i));
            spine.append("<itemref idref=\"c0%d\"/>\n".formatted(i));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>A. Author</dc:creator>
                    %s
                  </metadata>
                  <manifest>
                    %s
                  </manifest>
                  <spine>
                    %s
                  </spine>
                </package>
                """.formatted(languages, manifest, spine);
    }

    private static String opfWithLegacyDcMetadataLanguage(String language) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc-metadata>
                      <dc:language>%s</dc:language>
                    </dc-metadata>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """.formatted(language);
    }

    private static String opfWithDctermsLanguageMeta(String language) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:language>%1$s</dc:language>
                    <meta property="dcterms:language">%1$s</meta>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """.formatted(language);
    }

    private static String opfWithPackageLang(String packageLang, List<String> dcLanguages) {
        final StringBuilder languages = new StringBuilder();
        for (final String lang : dcLanguages) {
            languages.append("<dc:language>").append(lang).append("</dc:language>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid" xml:lang="%s">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    %s
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """.formatted(packageLang, languages);
    }

    /**
     * A one-paragraph chapter carrying the three language attributes the spec names on the content document's
     * root elements, each individually omittable ({@code null}) so a test states only what it asserts on.
     */
    private static String chapterWithLangAttrs(
            @Nullable String htmlXmlLang, @Nullable String htmlLang, @Nullable String bodyLang) {
        final String htmlAttrs = (htmlXmlLang == null ? "" : " xml:lang=\"" + htmlXmlLang + "\"")
                + (htmlLang == null ? "" : " lang=\"" + htmlLang + "\"");
        final String bodyAttrs = bodyLang == null ? "" : " lang=\"" + bodyLang + "\"";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml"%s>
                <head><title>Chapter</title></head>
                <body%s>
                <p>Paragraph 0.</p>
                </body>
                </html>
                """.formatted(htmlAttrs, bodyAttrs);
    }

    private static String chapter(int paragraphCount) {
        final StringBuilder body = new StringBuilder();
        for (int i = 0; i < paragraphCount; i++) {
            body.append("<p>Paragraph ").append(i).append(".</p>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>
                %s
                </body>
                </html>
                """.formatted(body);
    }
}
