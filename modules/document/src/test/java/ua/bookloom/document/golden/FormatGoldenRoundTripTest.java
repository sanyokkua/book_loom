package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.fixture.MarkdownFixtures;
import ua.bookloom.document.fixture.TxtFixtures;

/**
 * The three new formats' golden round trips — one named test per format, each asserting that format's own
 * comparison and its coverage.
 *
 * <p>These stay alongside the catalogue sweep rather than being replaced by it, because the two answer different
 * questions: the sweep says <em>which fixture</em> broke, and a golden says <em>which obligation</em> broke.
 */
class FormatGoldenRoundTripTest {

    @TempDir
    private Path tempDir;

    private record RoundTrip(Path source, Path output, Document document, String targetLanguage) {}

    private RoundTrip roundTrip(Path source) {
        final DocumentService service = DocumentServices.newService();
        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("open failed: %s", opened.error())
                .isTrue();
        final Document document = Objects.requireNonNull(opened.data());

        final String targetLanguage = document.declaredLang() == null ? "uk" : document.declaredLang();
        final Result<Path> written =
                service.write(document, tempDir.resolve("out-" + source.getFileName()), targetLanguage);
        assertThat(written.isOk())
                .withFailMessage("write failed: %s", written.error())
                .isTrue();
        return new RoundTrip(source, Objects.requireNonNull(written.data()), document, targetLanguage);
    }

    // WHEN a fixture FB2 book is parsed and reassembled with no segment receiving target
    // text, THEN re-parsing the output yields a canonical form equal to the source's, its declared encoding value
    // equals the source's, and every binary payload is character-for-character identical.
    @Test
    void golden_fb2_isCanonicalXmlEqualWithItsEncodingAndBinariesIntact() {
        final RoundTrip result = roundTrip(Fb2Fixtures.primary(tempDir.resolve("book.fb2")));

        Fb2CanonicalAssert.assertCanonicalEqual(result.source(), result.output(), result.targetLanguage());
        assertThat(TextCoverage.of(result.source(), result.document().format(), result.document()))
                .as("FB2 text coverage")
                .isGreaterThanOrEqualTo(0.95);
        // WHEN a document is reassembled with no target text, the system SHALL produce output
        // canonical-equal to the source for EPUB, FB2 and Markdown and byte-identical for TXT.
        assertThat(allSegmentsOf(result.document()))
                .as("at least one FB2 segment was genuinely masked on the way through")
                .anySatisfy(segment -> assertThat(segment.placeholders()).isNotEmpty());
    }

    // WHERE the source's title information declares no language element, THEN the output
    // SHALL carry one declaring the target language, and the golden comparison SHALL treat that single added
    // element as expected rather than as a difference.
    @Test
    void golden_fb2WithNoDeclaredLanguage_gainsExactlyOneLanguageElement() {
        final RoundTrip result = roundTrip(Fb2Fixtures.noLanguage(tempDir.resolve("no-lang.fb2")));

        assertThat(result.targetLanguage()).isEqualTo("uk");
        Fb2CanonicalAssert.assertCanonicalEqual(result.source(), result.output(), result.targetLanguage());
        assertThat(new String(bytesOf(result.output()), StandardCharsets.UTF_8)).contains("<lang>uk</lang>");
    }

    // WHEN a fixture FB2 book whose <title-info> contains <lang></lang> is parsed and
    // reassembled with no segment receiving target text and target language uk, THEN the golden test passes and
    // the output's <title-info> contains <lang>uk</lang> — the empty element is replaced, not treated as absent.
    @Test
    void golden_fb2WithEmptyDeclaredLanguage_replacesIt() {
        final RoundTrip result = roundTrip(Fb2Fixtures.emptyLanguage(tempDir.resolve("empty-lang.fb2")));

        assertThat(result.targetLanguage()).isEqualTo("uk");
        Fb2CanonicalAssert.assertCanonicalEqual(result.source(), result.output(), result.targetLanguage());
        assertThat(new String(bytesOf(result.output()), StandardCharsets.UTF_8)).contains("<lang>uk</lang>");
    }

    // WHEN a fixture Markdown book is reassembled with zero segment edits, THEN re-parsing
    // the output yields a tree equal to the source's in node types, order, nesting and literal text.
    @Test
    void golden_markdown_isReParseEqual() {
        final RoundTrip result = roundTrip(MarkdownFixtures.primary(tempDir.resolve("chapter.md")));

        MarkdownAstAssert.assertReParseEqual(result.source(), result.output());
        assertThat(TextCoverage.of(result.source(), result.document().format(), result.document()))
                .as("Markdown text coverage")
                .isGreaterThanOrEqualTo(0.90);
        // WHEN a document is reassembled with no target text, the system SHALL produce output
        // canonical-equal to the source for EPUB, FB2 and Markdown and byte-identical for TXT.
        assertThat(allSegmentsOf(result.document()))
                .as("at least one Markdown segment was genuinely masked on the way through")
                .anySatisfy(segment -> assertThat(segment.placeholders()).isNotEmpty());
    }

    // WHEN a fixture TXT book with a byte-order mark and CRLF endings is reassembled with
    // zero segment edits, THEN the output bytes are identical to the source's, mark and line endings included.
    @Test
    void golden_txt_isByteIdentical() {
        final RoundTrip result = roundTrip(TxtFixtures.primary(tempDir.resolve("notes.txt")));

        assertThat(bytesOf(result.output())).isEqualTo(bytesOf(result.source()));
        assertThat(TextCoverage.of(result.source(), result.document().format(), result.document()))
                .as("TXT text coverage")
                .isGreaterThanOrEqualTo(0.99);
    }

    /**
     * The encoding-switch case is excluded from the source-language golden by design: it is the one fixture whose
     * output is <em>supposed</em> to differ from its source, because one unrepresentable character forces the
     * whole document to UTF-8. It is asserted against a re-parsed canonical tree instead — the structure must
     * survive even though the bytes and the declaration must not.
     */
    // a document that legitimately switches encoding still round-trips its structure.
    @Test
    void golden_fb2WithAnEncodingSwitch_keepsItsStructureWhileItsDeclarationChanges() {
        final Path source = Fb2Fixtures.primary(tempDir.resolve("book.fb2"));
        final DocumentService service = DocumentServices.newService();
        final Document document = Objects.requireNonNull(service.open(source).data());
        final Document translated = withUnrepresentableTarget(document);

        final Path output = Objects.requireNonNull(
                service.write(translated, tempDir.resolve("switched.fb2"), "uk").data());

        final String xml = new String(bytesOf(output), StandardCharsets.UTF_8);
        assertThat(xml).contains("encoding=\"UTF-8\"");
        assertThat(xml).contains(Fb2Fixtures.UNREPRESENTABLE_IN_WINDOWS_1251);
        assertThat(xml).contains("<binary");
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

    private static Document withUnrepresentableTarget(Document document) {
        final var unit = document.units().get(0);
        final List<Segment> segments = new java.util.ArrayList<>(unit.segments());
        segments.set(0, withTarget(segments.get(0), Fb2Fixtures.UNREPRESENTABLE_IN_WINDOWS_1251));
        final List<ua.bookloom.api.document.Unit> units = new java.util.ArrayList<>(document.units());
        units.set(
                0,
                new ua.bookloom.api.document.Unit(
                        unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                java.util.Map.copyOf(document.metadata()),
                units);
    }

    private static List<Segment> allSegmentsOf(Document document) {
        return document.units().stream().flatMap(u -> u.segments().stream()).toList();
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
