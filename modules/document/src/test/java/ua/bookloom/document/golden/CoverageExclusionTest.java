package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.EpubFixtures;

/**
 * Proves the two exclusions {@link TextCoverage} makes on the source side of its ratio actually keep excluded text
 * off <strong>both</strong> sides — a {@code <pre><code>} listing and a code-only block. The fixture surrounds each
 * with prose that alone reaches every non-excluded word, so a leak on either side would show up as a coverage below
 * the clean {@code 1.0} this fixture reports when the measurement is symmetric.
 */
class CoverageExclusionTest {

    @TempDir
    private Path tempDir;

    private Document openFixture(Path fixture) {
        return Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());
    }

    // WHEN coverage is measured, the system SHALL count text inside deliberately excluded
    // blocks and protected spans as not translatable on both sides of the ratio.
    @Test
    void coverage_codeListingText_isInNeitherSegmentsNorSourceTotal() {
        final Path fixture = EpubFixtures.codeExclusionsAmongProse(tempDir.resolve("book.epub"));
        final Document document = openFixture(fixture);

        assertThat(allSourceInner(document)).doesNotContain("int x = 1;");
        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isEqualTo(1.0);
    }

    // WHEN coverage is measured, the system SHALL count text inside deliberately excluded
    // blocks and protected spans as not translatable on both sides of the ratio.
    @Test
    void coverage_codeOnlyBlockText_isInNeitherSegmentsNorSourceTotal() {
        final Path fixture = EpubFixtures.codeExclusionsAmongProse(tempDir.resolve("book.epub"));
        final Document document = openFixture(fixture);

        assertThat(allSourceInner(document)).doesNotContain("List.of()");
        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isEqualTo(1.0);
    }

    // WHEN coverage is measured, the system SHALL count text inside deliberately excluded
    // blocks and protected spans as not translatable on both sides of the ratio.
    @Test
    void coverage_codeSpanInsideProse_isCountedOnBothSidesOfTheRatio() {
        final Path fixture = EpubFixtures.codeExclusionsAmongProse(tempDir.resolve("book.epub"));
        final Document document = openFixture(fixture);

        // Both sides asserted directly. The ratio alone cannot prove the source-total half: if a regression dropped
        // this paragraph from the denominator, its words would leave `translatable` as well as staying reached and
        // the ratio would still be exactly 1.0.
        assertThat(allSourceInner(document)).as("segments side").contains("Xray.now()");
        assertThat(TextCoverage.translatableTextOf(fixture, BookFormat.EPUB, document.charset()))
                .as("source-total side")
                .contains("Xray.now()");
        assertThat(TextCoverage.of(fixture, BookFormat.EPUB, document)).isEqualTo(1.0);
    }

    private static String allSourceInner(Document document) {
        final StringBuilder text = new StringBuilder();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                text.append(segment.sourceInner()).append(' ');
            }
        }
        return text.toString();
    }
}
