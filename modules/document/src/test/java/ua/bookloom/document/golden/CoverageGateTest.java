package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.EpubFixtures;
import ua.bookloom.document.fixture.MarkdownFixtures;
import ua.bookloom.document.fixture.TxtFixtures;

/**
 * Proves the coverage assertion has teeth.
 *
 * <p>A gate that cannot fail is not a gate, and this one is easy to write in a form that never does — which
 * matters more here than usual, because a zero-segment import satisfies every <em>other</em> assertion in the
 * suite perfectly. So the same fixture is measured twice: once as the importer really parses it, and once against
 * a document whose segments have been removed, standing in for the tag-whitelist walker that reached 73.74% of a
 * real corpus and left 42 books importing empty.
 */
class CoverageGateTest {

    @TempDir
    private Path tempDir;

    private Document openDivParagraphs() {
        final Path fixture = EpubFixtures.divParagraphs(tempDir.resolve("book.epub"));
        return Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());
    }

    // Covers: FR-DOC-09 — WHEN a fixture whose paragraphs are div elements is parsed with structural block
    // recognition, THEN the coverage assertion reports a coverage above 0.95 and passes.
    @Test
    void coverage_divParagraphFixtureParsedStructurally_isAboveTheFloor() {
        final Path fixture = EpubFixtures.divParagraphs(tempDir.resolve("book.epub"));
        final Document document = Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());

        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isGreaterThan(0.95);
    }

    // Covers: FR-DOC-09 — WHEN a fixture's prose is not segmented, THEN the coverage assertion fails, reporting a
    // coverage close to 0 — which is what a tag-whitelist walker would produce on this shape.
    @Test
    void coverage_sameFixtureWithNoSegments_isCloseToZero() {
        final Path fixture = EpubFixtures.divParagraphs(tempDir.resolve("book.epub"));
        final Document parsed = openDivParagraphs();

        final double coverage = TextCoverage.of(fixture, BookFormat.EPUB, withoutSegments(parsed));

        assertThat(coverage).isLessThan(0.05);
    }

    // Covers: FR-DOC-09 — an excluded code listing does not count against coverage, so a fixture carrying one
    // still passes its floor.
    @Test
    void coverage_fixtureCarryingAnExcludedListing_stillPassesItsFloor() {
        final Path fixture = EpubFixtures.combination(tempDir.resolve("book.epub"));
        final Document document = Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());

        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isGreaterThan(0.95);
    }

    // Covers: FR-DOC-09 — WHEN a fixture Markdown file whose every block is segmented contains the inline spans
    // **bold** and `code`, THEN the coverage assertion reports a coverage above 0.95.
    @Test
    void coverage_markdownFixtureCarryingHeavyInlineSyntax_isAboveTheFloor() {
        final Path fixture = MarkdownFixtures.markupDense(tempDir.resolve("markup-dense.md"));
        final Document document = Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());

        assertThat(TextCoverage.of(fixture, BookFormat.MARKDOWN, document)).isGreaterThan(0.95);
    }

    // Covers: FR-DOC-09 — WHEN a fixture TXT file encoded in windows-1251 whose every paragraph is segmented is
    // measured, THEN the coverage assertion reports a coverage above 0.99.
    @Test
    void coverage_windows1251TxtFixture_isAboveTheFloor() {
        final Path fixture = TxtFixtures.windows1251(tempDir.resolve("windows-1251.txt"));
        final Result<Document> opened = DocumentServices.newService().open(fixture);
        final Document document = Objects.requireNonNull(opened.data());

        assertThat(TextCoverage.of(fixture, BookFormat.TXT, document)).isGreaterThan(0.99);
    }

    // Covers: FR-DOC-09 — WHEN a fixture EPUB paragraph whose content is <b>bold</b> text is segmented and
    // measured, THEN the coverage assertion reports a coverage above 0.95.
    @Test
    void coverage_epubBoldInlineFixture_isAboveTheFloor() {
        final Path fixture = EpubFixtures.boldInline(tempDir.resolve("book.epub"));
        final Document document = Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());

        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isGreaterThan(0.95);
    }

    /** The same document with every segment removed — what a walker that recognised nothing would have produced. */
    private static Document withoutSegments(Document document) {
        final List<Unit> stripped = new ArrayList<>();
        for (final Unit unit : document.units()) {
            stripped.add(new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), List.of()));
        }
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                stripped);
    }
}
