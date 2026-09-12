package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.DocumentNotOpenException;

/**
 * {@code EpubWriter}'s write path — writing accepted segments back, replacing {@code dc:language}, and
 * repackaging (task group 3), against tiny ad-hoc EPUB fixtures built in {@link EpubZipBuilder}.
 */
class EpubWriterTest {

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

    // on export the system SHALL write mimetype first and STORED, and SHALL preserve the
    // order of the remaining entries.
    @Test
    void write_zeroEditRoundTrip_mimetypeIsFirstAndStored_andEntryOrderIsPreserved() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                // Deliberately DEFLATED on input — proves the writer normalizes mimetype to STORED regardless of
                // how the source archive happened to compress it.
                .entry("mimetype", "application/epub+zip", ZipEntry.DEFLATED)
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        final List<ZipRecord> written = readZip(output);
        assertThat(written)
                .extracting(ZipRecord::name)
                .containsExactly("mimetype", "META-INF/container.xml", "OEBPS/content.opf", "OEBPS/c01.xhtml");
        assertThat(written.get(0).method()).isEqualTo(ZipEntry.STORED);
        assertThat(new String(written.get(0).content(), StandardCharsets.UTF_8)).isEqualTo("application/epub+zip");
    }

    /**
     * Publishers deliberately STORE already-compressed images: 26 corpus books do it for 2,578 entries, one for
     * 1,950. DD-43 licenses re-compressing an entry that was <em>already</em> compressed, not converting a stored
     * one into a compressed one, and the shipped writer DEFLATEd everything.
     */
    // WHEN an EPUB is reassembled, THEN each entry keeps its own original compression
    // method: a STORED image stays STORED and a DEFLATED stylesheet stays DEFLATED.
    @Test
    void write_zeroEditRoundTrip_preservesEachEntrysOriginalCompressionMethod() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .entry("OEBPS/images/cover.jpg", "pretend-jpeg-bytes", ZipEntry.STORED)
                .entry("OEBPS/styles.css", "body { font-family: serif; }", ZipEntry.DEFLATED)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        final List<ZipRecord> written = readZip(output);
        assertThat(methodOf(written, "OEBPS/images/cover.jpg")).isEqualTo(ZipEntry.STORED);
        assertThat(methodOf(written, "OEBPS/styles.css")).isEqualTo(ZipEntry.DEFLATED);
        assertThat(contentOf(written, "OEBPS/images/cover.jpg")).isEqualTo("pretend-jpeg-bytes");
    }

    private static int methodOf(List<ZipRecord> written, String name) {
        return recordOf(written, name).method();
    }

    private static String contentOf(List<ZipRecord> written, String name) {
        return new String(recordOf(written, name).content(), StandardCharsets.UTF_8);
    }

    private static ZipRecord recordOf(List<ZipRecord> written, String name) {
        return written.stream()
                .filter(r -> name.equals(r.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry named " + name));
    }

    // a duplicate id survives a zero-edit round trip rather than being corrected.
    @Test
    void write_zeroEditRoundTrip_duplicateIdSurvivesUnrewritten() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapterWithDuplicateId())
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        final byte[] chapterBytes = contentOf(output, "OEBPS/c01.xhtml");
        final org.jsoup.nodes.Document parsed = Jsoup.parse(new String(chapterBytes, StandardCharsets.UTF_8));
        assertThat(parsed.select("[id=note1]")).hasSize(2);
    }

    // an embedded font is carried through byte-for-byte on the write side.
    @Test
    void write_zeroEditRoundTrip_embeddedFontRoundTripsByteForByte() {
        final Path epub = tempDir.resolve("book.epub");
        final byte[] fontBytes = binaryPayload();
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .binaryEntry("OEBPS/fonts/serif.otf", fontBytes)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(contentOf(output, "OEBPS/fonts/serif.otf")).isEqualTo(fontBytes);
    }

    // an out-of-spine resource (here, a stylesheet) is carried through unchanged on write.
    @Test
    void write_zeroEditRoundTrip_outOfSpineStylesheetSurvivesByteForByte() {
        final Path epub = tempDir.resolve("book.epub");
        final String css = "body { color: black; }";
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .entry("OEBPS/styles.css", css)
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(contentOf(output, "OEBPS/styles.css")).isEqualTo(css.getBytes(StandardCharsets.UTF_8));
    }

    // on export the first dc:language is replaced and any further entry is left alone.
    @Test
    void write_twoDeclaredLanguages_replacesOnlyTheFirst() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en", "la"), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(dcLanguagesOf(output)).containsExactly("uk", "la");
    }

    // a missing dc:language declaration is added with the target language on export.
    @Test
    void write_noDeclaredLanguage_addsExactlyOneWithTargetLanguage() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of(), 1))
                .entry("OEBPS/c01.xhtml", chapter(1))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "uk");

        assertThat(dcLanguagesOf(output)).containsExactly("uk");
    }

    // writing back an earlier segment does not invalidate a later segment's anchor: no anchor is
    // recomputed between the two writes.
    @Test
    void write_earlierSegmentGrowsLonger_laterSegmentStillLandsInItsOwnElement() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(List.of("en"), 1))
                .entry("OEBPS/c01.xhtml", chapter(6))
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document parsed = new EpubReader(registry).read(epub);
        final String longerTarget = "A translated paragraph zero, made deliberately much longer than its source.";
        final String laterTarget = "Translated paragraph five.";
        final Document edited = withSegmentTargets(parsed, Map.of(0, longerTarget, 5, laterTarget));

        final Path output = new EpubWriter(registry).write(edited, tempDir.resolve("out.epub"), "uk");

        final List<String> paragraphs = paragraphTextsOf(output);
        assertThat(paragraphs.get(0)).isEqualTo(longerTarget);
        assertThat(paragraphs.get(5)).isEqualTo(laterTarget);
        assertThat(paragraphs.get(1)).isEqualTo("Paragraph 1.");
        assertThat(paragraphs.get(4)).isEqualTo("Paragraph 4.");
    }

    @Test
    void write_documentNeverOpened_throwsDocumentNotOpenException() {
        final EpubWriter writer = new EpubWriter(new OpenEpubRegistry());
        final Document unopened = new Document(
                "never-registered", BookFormat.EPUB, null, null, null, null, "deadbeef", Map.of(), List.of());

        assertThatThrownBy(() -> writer.write(unopened, tempDir.resolve("out.epub"), "uk"))
                .isInstanceOf(DocumentNotOpenException.class);
    }

    private static Document withSegmentTargets(Document document, Map<Integer, String> targetsByOrder) {
        final Unit unit = document.units().get(0);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        targetsByOrder.forEach((order, target) -> segments.set(order, withTarget(segments.get(order), target)));
        final Unit editedUnit =
                new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments);
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                List.of(editedUnit));
    }

    private static Segment withTarget(Segment segment, String targetInner) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                targetInner,
                segment.status(),
                segment.confidence());
    }

    private static byte[] binaryPayload() {
        final byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        return payload;
    }

    private static byte[] contentOf(Path zip, String entryName) {
        return readZip(zip).stream()
                .filter(record -> record.name().equals(entryName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No entry named " + entryName + " in " + zip))
                .content();
    }

    private static List<String> dcLanguagesOf(Path zip) {
        final byte[] opfBytes = contentOf(zip, "OEBPS/content.opf");
        return OpfParser.parse(opfBytes, "OEBPS/content.opf").dcLanguages();
    }

    private static List<String> paragraphTextsOf(Path zip) {
        final byte[] chapterBytes = contentOf(zip, "OEBPS/c01.xhtml");
        final org.jsoup.nodes.Document parsed = Jsoup.parse(new String(chapterBytes, StandardCharsets.UTF_8));
        return parsed.body().select("p").eachText();
    }

    private static List<ZipRecord> readZip(Path zip) {
        final List<ZipRecord> result = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            addEntries(in, result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static void addEntries(ZipInputStream in, List<ZipRecord> result) throws IOException {
        ZipEntry entry = in.getNextEntry();
        while (entry != null) {
            result.add(new ZipRecord(entry.getName(), entry.getMethod(), in.readAllBytes()));
            entry = in.getNextEntry();
        }
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

    private static String chapterWithDuplicateId() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>
                <p id="note1">First note.</p>
                <p>Prose.</p>
                <p id="note1">Second note.</p>
                </body>
                </html>
                """;
    }

    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw zip content read back purely for assertions.
    private record ZipRecord(String name, int method, byte[] content) {}
}
