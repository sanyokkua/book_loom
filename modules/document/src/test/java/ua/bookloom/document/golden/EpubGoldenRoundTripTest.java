package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.EpubFixtures;
import ua.bookloom.document.fixture.FixtureCatalog;

/**
 * The gate the whole change is judged by (task 5.4): the adversarial primary fixture (task 5.1), parsed and
 * reassembled with zero segment edits through {@link DocumentService}, must be canonical-equal to the source
 * (design.md D5) — plus the fixed-point obligation (design.md D3) that a zero-edit write is stable under a
 * second write, checked without a comparator for every catalogued EPUB fixture, and the two self-closed-element
 * shapes (design.md D2b, task 5.1/5.3) whose structural correctness a canonical comparison cannot state at all
 * (an id count, an ancestor check) because it re-parses only text, not tree shape.
 */
class EpubGoldenRoundTripTest {

    @TempDir
    private Path tempDir;

    // Covers: FR-DOC-09 — a fixture EPUB parsed and reassembled with zero segment edits is canonical-equal to the
    // source: text entries compared by decompressed canonical content, entry order preserved, mimetype first and
    // STORED, and unchanged binary entries compared by decompressed bytes.
    @Test
    void write_zeroEditRoundTrip_isCanonicalEqualToSource() {
        final Path fixture = PrimaryFixtureEpub.build(tempDir.resolve("fixture.epub"));
        final DocumentService service = newService();

        final Result<Document> openResult = service.open(fixture);
        assertThat(openResult.isOk()).isTrue();
        final Document document = Objects.requireNonNull(openResult.data(), "data");

        // The fixture's own declared language as the target: this proves the no-op round trip, not language
        // replacement (that behavior is EpubWriterTest's, task group 3).
        final Result<Path> writeResult = service.write(
                document, tempDir.resolve("output.epub"), Objects.requireNonNull(document.declaredLang()));
        assertThat(writeResult.isOk()).isTrue();
        final Path output = Objects.requireNonNull(writeResult.data(), "data");

        EpubCanonicalAssert.assertCanonicalEqual(fixture, output);
    }

    // Covers: FR-DOC-03 — WHEN each EPUB fixture registered in the fixture catalogue is reassembled with zero
    // segment edits and that output is reassembled again with zero segment edits, THEN for every one of them the
    // second output is canonical-equal to the first.
    @ParameterizedTest(name = "{0}")
    @MethodSource("epubFixtures")
    void write_everyCatalogueFixtureTwiceWithZeroEdits_isFixedPoint(FixtureCatalog.Case fixture) {
        final Path source = fixture.builder().apply(tempDir.resolve(fixture.fileName()));

        // A fresh service (and so a fresh registry) for each write: EpubWriter mutates the tree its registry
        // holds live (F9, design.md D6), so reusing one service's registry across both writes would make the
        // second write compound the first's mutation instead of measuring an independent re-parse.
        final Path firstOutput = writeZeroEdit(newService(), source, tempDir.resolve("first-" + fixture.fileName()));
        final Path secondOutput =
                writeZeroEdit(newService(), firstOutput, tempDir.resolve("second-" + fixture.fileName()));

        EpubCanonicalAssert.assertCanonicalEqual(firstOutput, secondOutput);
    }

    // Covers: FR-DOC-03 — IF an element in the source is written in XML self-closing form and its HTML content
    // model is not empty, THEN the system SHALL NOT emit an additional copy of that element, SHALL NOT move a
    // sibling element inside it, and SHALL NOT duplicate its id attribute: a self-closed indexterm anchor before a
    // section boundary does not duplicate its id or absorb the following heading.
    @Test
    void write_selfClosedIndextermAnchor_doesNotDuplicateIdOrAbsorbTheHeading() {
        final Path fixture = EpubFixtures.selfClosedIndextermAnchor(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        final org.jsoup.nodes.Document parsed = firstContentDocumentOf(output);
        assertThat(parsed.select("[id=idm001]"))
                .as("elements carrying id=idm001")
                .hasSize(1);
        for (final Element heading : parsed.select("h1")) {
            // Elements.is() tests the ancestor list itself, not its descendants — Elements.select() would search
            // each ancestor's whole subtree and find the <a> anywhere in the document, which is not what "is the
            // heading a descendant of it" asks.
            assertThat(heading.parents().is("a")).as("h1 has an <a> ancestor").isFalse();
        }
    }

    // Covers: FR-DOC-03 — a self-closed inline element does not swallow the element after it: exactly one element
    // carries its id, and the paragraph that follows it is not a descendant of it.
    @Test
    void write_selfClosedInlineSpan_doesNotSwallowTheFollowingParagraph() {
        final Path fixture = EpubFixtures.selfClosedInlineSpan(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        final org.jsoup.nodes.Document parsed = firstContentDocumentOf(output);
        assertThat(parsed.select("[id=s1]")).as("elements carrying id=s1").hasSize(1);
        final Element secondParagraph = Objects.requireNonNull(
                parsed.select("p:contains(Next paragraph.)").first(), "second paragraph");
        // See the note on Elements.is() vs Elements.select() above: this must test the ancestor list itself.
        assertThat(secondParagraph.parents().is("span"))
                .as("second <p> has a <span> ancestor")
                .isFalse();
    }

    // Covers: FR-DOC-03 — a paragraph wrapping a division does not gain a phantom sibling: writing it, then
    // writing that output again with zero segment edits, produces a second output canonical-equal to the first,
    // even though the un-nesting a <div> forces inside a <p> is invisible to a comparator that re-parses both
    // sides through the same rule (design.md D2a).
    @Test
    void write_paragraphWrappingDivisionTwice_isFixedPoint() {
        final Path fixture = EpubFixtures.paragraphWrappingDivision(tempDir.resolve("fixture.epub"));

        final Path firstOutput = writeZeroEdit(newService(), fixture, tempDir.resolve("first.epub"));
        final Path secondOutput = writeZeroEdit(newService(), firstOutput, tempDir.resolve("second.epub"));

        EpubCanonicalAssert.assertCanonicalEqual(firstOutput, secondOutput);
    }

    // Covers: FR-DOC-09 — reassembly without target changes is canonical-equal to the source: a <pre> whose
    // content does not begin with a line break gains none on a zero-edit write.
    @Test
    void write_preWithNoLeadingLineFeed_reparsesWithNoLeadingLineFeed() {
        final Path fixture = EpubFixtures.preNoLeadingLineFeed(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        assertThat(preWholeTextOf(output)).isEqualTo("code here");
    }

    // Covers: FR-DOC-09 — reassembly without target changes is canonical-equal to the source: a <pre> beginning
    // with two carriage-return-line-feed pairs still begins with exactly those two pairs after re-parsing.
    @Test
    void write_preWithTwoLeadingCrLfPairs_reparsesUnchanged() {
        final Path fixture = EpubFixtures.preTwoLeadingCrLfPairs(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        assertThat(preWholeTextOf(output)).isEqualTo("\r\n\r\nX");
    }

    // Covers: FR-DOC-09 — reassembly without target changes is canonical-equal to the source: an empty <pre></pre>
    // reassembles without failing and the golden round-trip comparison passes.
    @Test
    void write_preEmpty_reassemblesWithoutFailingAndGoldenPasses() {
        final Path fixture = EpubFixtures.preEmpty(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        EpubCanonicalAssert.assertCanonicalEqual(fixture, output);
    }

    // Covers: FR-DOC-09 — reassembly without target changes is canonical-equal to the source: a poem's <pre>
    // beginning with two line breaks still begins with two line breaks in the reassembled output.
    //
    // Asserted against the output's own raw markup (ZipText, independent of jsoup) rather than a re-parsed DOM:
    // the HTML parsing spec discards exactly one bare LF immediately after ANY <pre> start tag on EVERY parse,
    // source or output alike, so a DOM built by re-parsing the output can only ever show ONE surviving line
    // feed — that is what makes the two of them canonical-equal, not a literal count in a re-parsed tree. The
    // restore's observable effect is on the bytes the write path emits, which this fixture proves are the same
    // two line feeds the source itself carries.
    @Test
    void write_prePoemWithTwoLeadingLineFeeds_bothSurviveReassembly() {
        final Path fixture = EpubFixtures.preTwoLeadingLineFeeds(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        final String contentDocument =
                ZipText.contentDocumentsOf(output).getFirst().text();
        assertThat(contentDocument)
                .contains("<pre class=\"poem\">\n\n        \u201cSpeak roughly to your little boy,</pre>");
    }

    // Covers: FR-DOC-09 — reassembly without target changes is canonical-equal to the source: a <pre> beginning
    // with a single line break passes the golden round-trip comparison unchanged.
    @Test
    void write_preWithOneLeadingLineFeed_goldenPasses() {
        final Path fixture = EpubFixtures.preOneLeadingLineFeed(tempDir.resolve("fixture.epub"));
        final Path output = writeZeroEdit(newService(), fixture, tempDir.resolve("output.epub"));

        EpubCanonicalAssert.assertCanonicalEqual(fixture, output);
    }

    private static String preWholeTextOf(Path epub) {
        final org.jsoup.nodes.Document parsed = firstContentDocumentOf(epub);
        final Element pre = parsed.selectFirst("pre");
        if (pre == null) {
            throw new AssertionError("no <pre> element in " + epub);
        }
        return pre.wholeText();
    }

    private static List<FixtureCatalog.Case> epubFixtures() {
        return FixtureCatalog.all().stream()
                .filter(fixture -> fixture.format() == BookFormat.EPUB)
                .toList();
    }

    private static org.jsoup.nodes.Document firstContentDocumentOf(Path epub) {
        final ZipText contentDocument = ZipText.contentDocumentsOf(epub).getFirst();
        return Jsoup.parse(contentDocument.text());
    }

    private static Path writeZeroEdit(DocumentService service, Path source, Path destination) {
        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("did not open %s: %s", source.getFileName(), opened.error())
                .isTrue();
        final Document document = Objects.requireNonNull(opened.data(), "data");
        final String targetLanguage = document.declaredLang() == null ? "uk" : document.declaredLang();
        final Result<Path> written = service.write(document, destination, targetLanguage);
        assertThat(written.isOk())
                .withFailMessage("did not reassemble %s: %s", source.getFileName(), written.error())
                .isTrue();
        return Objects.requireNonNull(written.data(), "data");
    }

    private static DocumentService newService() {
        return DocumentServices.newService();
    }
}
