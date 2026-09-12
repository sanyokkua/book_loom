package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
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
import ua.bookloom.util.hash.HashUtil;

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

    /**
     * The placeholder-token grammar, restated here rather than borrowed from the masker's own constant: an
     * assertion that asks the code under test what a token looks like agrees with it whatever it does.
     */
    private static final Pattern TOKEN_GRAMMAR = Pattern.compile("\u27E6g(\\d+)\u27E7");

    @TempDir
    private Path tempDir;

    private static List<FixtureCatalog.Case> catalogue() {
        return FixtureCatalog.all();
    }

    // WHEN the fixture catalogue is exercised, THEN each fixture is written to a file, opened
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
        // WHEN a fixture is exercised through the port on a real file, the system SHALL emit
        // the placeholder count that fixture declares.
        assertThat(totalPlaceholderCount(document))
                .as("declared placeholder count of %s", fixture.name())
                .isEqualTo(fixture.expectedPlaceholderCount());
        assertMaskingContract(document);

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

    /**
     * The masking half of the sweep's contract, extracted so the parameterized test method stays within the
     * method-length limit rather than growing every time an obligation is added to it.
     *
     * <p>Asserted on a parsed document rather than a hand-built {@code Segment}, because the readers are what could
     * break it. {@link #TOKEN_GRAMMAR} is spelled out in this class rather than borrowed from the masker's own
     * constant, so a change to the grammar has to be made deliberately in two places instead of silently agreeing
     * with itself.
     */
    // WHEN a segment is masked, the system SHALL keep its placeholder map a bijection over
    // exactly the tokens present in its masked form.
    // WHILE masking runs, the system SHALL leave each segment's source content and content
    // hash unchanged.
    private static void assertMaskingContract(Document document) {
        assertThat(segmentsOf(document)).allSatisfy(segment -> {
            final List<String> keys = TOKEN_GRAMMAR
                    .matcher(segment.masked())
                    .results()
                    .map(match -> "g" + match.group(1))
                    .toList();
            assertThat(keys).as("tokens in %s", segment.id()).doesNotHaveDuplicates();
            assertThat(segment.placeholders().keySet())
                    .as("placeholder map of %s", segment.id())
                    .containsExactlyInAnyOrderElementsOf(keys);
            assertThat(segment.sourceHash())
                    .as("hash of %s is over sourceInner, not over masked", segment.id())
                    .isEqualTo(HashUtil.sha256OfNfcText(segment.sourceInner()));
            assertThat(segment.targetInner()).isNull();
        });
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

    /** The total number of placeholder tokens across every segment in {@code document}'s masked forms. */
    private static int totalPlaceholderCount(Document document) {
        return segmentsOf(document).stream()
                .mapToInt(segment -> segment.placeholders().size())
                .sum();
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
