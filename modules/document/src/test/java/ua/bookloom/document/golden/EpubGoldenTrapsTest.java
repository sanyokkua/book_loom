package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;

/**
 * Names each trap the primary fixture (task 5.1) is built to catch as its own test (task 5.5, design.md "Risks"),
 * so a fixture that stops covering one of them fails a specifically-named test rather than only the aggregate
 * {@link EpubGoldenRoundTripTest} going red with no diagnosis.
 */
class EpubGoldenTrapsTest {

    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    private static final Namespace DC_NS = Namespace.getNamespace("http://purl.org/dc/elements/1.1/");
    private static final String OPF_ENTRY = "OEBPS/content.opf";
    private static final String CHAPTER_ONE_ENTRY = "OEBPS/c01.xhtml";
    private static final String CHAPTER_TWO_ENTRY = "OEBPS/c02.xhtml";

    @TempDir
    private Path tempDir;

    // on export mimetype is written first and STORED.
    @Test
    void write_primaryFixture_mimetypeIsFirstAndStored() {
        final RoundTrip roundTrip = roundTrip();

        final List<ZipRecord> written = readZip(roundTrip.output());

        assertThat(written.get(0).name()).isEqualTo("mimetype");
        assertThat(written.get(0).method()).isEqualTo(ZipEntry.STORED);
    }

    // the duplicate id="note1" elements both survive a zero-edit round trip, unrewritten.
    @Test
    void write_primaryFixture_duplicateIdSurvivesUnrewritten() {
        final RoundTrip roundTrip = roundTrip();

        final org.jsoup.nodes.Document chapterOne = Jsoup.parse(textOf(roundTrip.output(), CHAPTER_ONE_ENTRY));

        assertThat(chapterOne.select("[id=note1]")).hasSize(2);
    }

    // the <pre><code> listing's exact text survives, and no segment produced by parsing contains
    // its code text (excluded from segmentation entirely).
    @Test
    void write_primaryFixture_codeListingSurvivesVerbatimAndProducesNoSegment() {
        final RoundTrip roundTrip = roundTrip();

        final org.jsoup.nodes.Document chapterTwo = Jsoup.parse(textOf(roundTrip.output(), CHAPTER_TWO_ENTRY));
        final org.jsoup.nodes.Element code = chapterTwo.selectFirst("pre code");

        assertThat(Objects.requireNonNull(code, "code").wholeText()).isEqualTo(PrimaryFixtureEpub.CODE_LISTING_TEXT);
        assertThat(allSourceInner(roundTrip.document())).noneMatch(inner -> inner.contains("int x = 1;"));
    }

    // the out-of-spine stylesheet's bytes survive byte-for-byte.
    @Test
    void write_primaryFixture_outOfSpineStylesheetSurvivesByteForByte() {
        final RoundTrip roundTrip = roundTrip();

        final byte[] css = entryBytes(roundTrip.output(), "OEBPS/styles.css");

        assertThat(css).isEqualTo(PrimaryFixtureEpub.STYLESHEET_CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    // the embedded font's bytes survive byte-for-byte.
    @Test
    void write_primaryFixture_embeddedFontSurvivesByteForByte() {
        final RoundTrip roundTrip = roundTrip();

        final byte[] font = entryBytes(roundTrip.output(), "OEBPS/fonts/serif.otf");

        assertThat(font).isEqualTo(PrimaryFixtureEpub.fontBytes());
    }

    // WHEN a document containing an XML comment between two block elements is reassembled,
    // THEN the comment is present in the output, between the same two elements.
    @Test
    void write_primaryFixture_commentSurvivesInPosition() {
        final RoundTrip roundTrip = roundTrip();

        final String chapterOne = textOf(roundTrip.output(), CHAPTER_ONE_ENTRY);

        assertThat(chapterOne).contains("<!-- a comment between two block elements -->");
        final int commentIndex = chapterOne.indexOf("<!-- a comment between two block elements -->");
        assertThat(chapterOne.indexOf("Before.")).isLessThan(commentIndex);
        assertThat(chapterOne.indexOf("After.")).isGreaterThan(commentIndex);
    }

    // WHEN a document containing <p id="ch01-p07" class="first">Text.</p> is parsed and
    // reassembled with zero edits, THEN the output element still carries id="ch01-p07" and class="first" — the
    // skeleton's attributes survive reassembly, not merely its id (EC-EPUB-4's own test already covers duplicate
    // ids specifically).
    @Test
    void write_primaryFixture_elementIdAndClassSurviveReassembly() {
        final RoundTrip roundTrip = roundTrip();

        final org.jsoup.nodes.Document chapterOne = Jsoup.parse(textOf(roundTrip.output(), CHAPTER_ONE_ENTRY));
        final org.jsoup.nodes.Element paragraph = chapterOne.selectFirst("[id=ch01-p07]");

        assertThat(Objects.requireNonNull(paragraph, "ch01-p07").attr("class")).isEqualTo("first");
    }

    // spine order, not zip order, drives the parsed document's unit order (the fixture's
    // zip entries physically place c02.xhtml before c01.xhtml; the OPF spine declares c01 then c02).
    @Test
    void read_primaryFixture_followsSpineOrderNotZipOrder() {
        final RoundTrip roundTrip = roundTrip();

        assertThat(roundTrip.document().units())
                .extracting(Unit::href)
                .containsExactly(CHAPTER_ONE_ENTRY, CHAPTER_TWO_ENTRY);
    }

    // Mechanical: a zero-edit round trip that targets the fixture's own first declared language leaves both
    // dc:language entries unchanged — this is not proof of general first-only replacement (EpubWriterTest already
    // covers that), only that the no-op path touches neither.
    @Test
    void write_primaryFixtureZeroEditWithOwnDeclaredLanguageAsTarget_leavesBothDcLanguagesUnchanged() {
        final RoundTrip roundTrip = roundTrip();

        assertThat(dcLanguagesOf(roundTrip.output())).containsExactly("en", "la");
    }

    private RoundTrip roundTrip() {
        final Path fixture = PrimaryFixtureEpub.build(tempDir.resolve("fixture.epub"));
        final DocumentService service = DocumentServices.newService();

        final Result<Document> openResult = service.open(fixture);
        assertThat(openResult.isOk()).isTrue();
        final Document document = Objects.requireNonNull(openResult.data(), "data");

        final Result<Path> writeResult = service.write(document, tempDir.resolve("output.epub"), "en");
        assertThat(writeResult.isOk()).isTrue();
        final Path output = Objects.requireNonNull(writeResult.data(), "data");

        return new RoundTrip(document, output);
    }

    private static List<String> allSourceInner(Document document) {
        final List<String> inner = new ArrayList<>();
        for (final Unit unit : document.units()) {
            unit.segments().forEach(segment -> inner.add(segment.sourceInner()));
        }
        return inner;
    }

    private static List<String> dcLanguagesOf(Path zip) {
        try {
            final org.jdom2.Document opf = new SAXBuilder().build(new ByteArrayInputStream(entryBytes(zip, OPF_ENTRY)));
            final Element metadata = opf.getRootElement().getChild("metadata", OPF_NS);
            final List<String> values = new ArrayList<>();
            for (final Element language :
                    Objects.requireNonNull(metadata, "metadata").getChildren("language", DC_NS)) {
                values.add(language.getTextTrim());
            }
            return values;
        } catch (JDOMException | IOException e) {
            throw new AssertionError("Unable to parse output OPF", e);
        }
    }

    private static String textOf(Path zip, String entryName) {
        return new String(entryBytes(zip, entryName), StandardCharsets.UTF_8);
    }

    private static byte[] entryBytes(Path zip, String entryName) {
        return readZip(zip).stream()
                .filter(record -> record.name().equals(entryName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No entry named " + entryName + " in " + zip))
                .content();
    }

    private static List<ZipRecord> readZip(Path zip) {
        final List<ZipRecord> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                entries.add(new ZipRecord(entry.getName(), entry.getMethod(), in.readAllBytes()));
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    private record RoundTrip(Document document, Path output) {}

    @SuppressWarnings("ArrayRecordComponent") // deliberate: raw zip content read back purely for assertions.
    private record ZipRecord(String name, int method, byte[] content) {}
}
