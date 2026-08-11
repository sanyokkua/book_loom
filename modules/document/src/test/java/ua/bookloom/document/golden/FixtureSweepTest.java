package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.FixtureCatalog;

/**
 * The catalogue sweep: every fixture written to a real file, opened through the real {@code DocumentPort},
 * reassembled with zero segment edits, and asserted against its <strong>declared</strong> expectations.
 *
 * <p><strong>Why through the port and a real file.</strong> Format resolution, container reading, charset
 * resolution and error classification all sit between a file and a parser, and a test that calls the parser
 * directly skips every one of them. Going through the port is what makes the assertion mean "a user could open
 * this book".
 *
 * <p><strong>What the sweep adds over the named goldens.</strong> The goldens say <em>which obligation</em> broke;
 * the sweep says <em>which fixture</em> broke. What only the sweep provides is that a fixture registered in the
 * catalogue can never be authored and then left ungated — which is precisely how the inline-markup shape came to
 * have no test at all.
 */
class FixtureSweepTest {

    @TempDir
    private Path tempDir;

    private static List<FixtureCatalog.Case> catalogue() {
        return FixtureCatalog.all();
    }

    // Covers: FR-DOC-09 — WHEN the fixture catalogue is exercised, THEN each fixture is written to a file, opened
    // through the document port and reassembled with no segment receiving target text, and each meets its
    // declared segment count, its format's canonical comparison and its coverage floor.
    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogue")
    void sweep_everyCatalogueFixture_roundTripsThroughThePortAndMeetsItsDeclaredExpectations(
            FixtureCatalog.Case fixture) {
        final Path source = fixture.builder().apply(tempDir.resolve(fixture.fileName()));
        // One service for both halves: a reader and its writer share a registry, so a document opened by one
        // service instance is unknown to another.
        final DocumentService service = DocumentServices.newService();

        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("%s did not open: %s", fixture.name(), opened.error())
                .isTrue();
        final Document document = Objects.requireNonNull(opened.data());

        assertThat(document.format()).as("resolved format").isEqualTo(fixture.format());
        assertThat(segmentsOf(document)).as("declared segment count").hasSize(fixture.expectedSegmentCount());
        assertThat(kindsOf(document)).as("declared kinds").isEqualTo(fixture.expectedKinds());
        // Covers: FR-DOC-01 — no segment this change emits is masked: `masked` equals `sourceInner` and
        // `placeholders` is empty, for every format. Asserted on a parsed document rather than on a hand-built
        // Segment, because the readers are what could break it.
        assertThat(segmentsOf(document)).allSatisfy(segment -> {
            assertThat(segment.masked()).isEqualTo(segment.sourceInner());
            assertThat(segment.placeholders()).isEmpty();
            assertThat(segment.targetInner()).isNull();
        });

        // The fixture's own declared language as the target, so this stays a no-op round trip. Replacing the
        // language is real, specified behaviour with its own tests; folding it in here would mean every canonical
        // comparison had to make an exception for the one element the export is supposed to change.
        final String targetLanguage = document.declaredLang() == null ? "uk" : document.declaredLang();
        final Path output = reassembleWithZeroEdits(service, document, fixture, targetLanguage);
        assertComparison(fixture, source, output, targetLanguage);

        assertThat(TextCoverage.of(source, fixture.format(), document))
                .as("text coverage of %s", fixture.name())
                .isGreaterThanOrEqualTo(fixture.coverageFloor());
    }

    private Path reassembleWithZeroEdits(
            DocumentService service, Document document, FixtureCatalog.Case fixture, String targetLanguage) {
        final Path output = tempDir.resolve("out-" + fixture.fileName());
        final Result<Path> written = service.write(document, output, targetLanguage);
        assertThat(written.isOk())
                .withFailMessage("%s did not reassemble: %s", fixture.name(), written.error())
                .isTrue();
        return output;
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().stream().flatMap(u -> u.segments().stream()).toList();
    }

    private static Set<SegmentKind> kindsOf(Document document) {
        return segmentsOf(document).stream().map(Segment::kind).collect(java.util.stream.Collectors.toSet());
    }

    private static void assertComparison(FixtureCatalog.Case fixture, Path source, Path output, String targetLanguage) {
        switch (fixture.comparison()) {
            case EPUB_CANONICAL -> EpubCanonicalAssert.assertCanonicalEqual(source, output);
            case FB2_CANONICAL -> Fb2CanonicalAssert.assertCanonicalEqual(source, output, targetLanguage);
            case MARKDOWN_AST -> MarkdownAstAssert.assertReParseEqual(source, output);
            case TXT_BYTES -> assertThat(bytesOf(output)).isEqualTo(bytesOf(source));
        }
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
