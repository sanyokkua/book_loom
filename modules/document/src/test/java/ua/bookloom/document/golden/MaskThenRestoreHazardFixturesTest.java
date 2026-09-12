package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.EpubHazardFixtures;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * The two hand-picked fixtures task 9.3 asks for: one EPUB paragraph and one FB2 paragraph, each carrying every
 * hazard this change introduces at once, rather than one hazard per fixture the way {@link
 * MaskThenRestoreIdentitySweepTest}'s catalogue does. Both builders are also registered in {@code FixtureCatalog}
 * ({@code epub/hazard-paragraph}, {@code fb2/hazard-paragraph}) and so are already swept by that catalogue-driven
 * test; this class adds value on top of the sweep by asserting the two hazard paragraphs directly and by name, so
 * a failure here points straight at the hazard rather than at a fixture id in a parameterized sweep's report.
 */
class MaskThenRestoreHazardFixturesTest {

    @TempDir
    private Path tempDir;

    // WHEN a segment's masked form is supplied back as its own target, the system SHALL
    // produce restored content canonical-equal to its source content.
    @Test
    void unmask_epubHazardParagraph_restoresCanonicalEqualToSource() {
        final Path source = EpubHazardFixtures.hazardParagraph(tempDir.resolve("book.epub"));
        final DocumentService service = DocumentServices.newService();

        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("epub hazard paragraph did not open: %s", opened.error())
                .isTrue();
        final Segment segment = onlySegment(Objects.requireNonNull(opened.data()));

        final Result<String> restored = service.unmask(BookFormat.EPUB, segment, segment.masked());
        assertThat(restored.isOk())
                .withFailMessage("epub hazard paragraph did not restore: %s", restored.error())
                .isTrue();
        MaskRestoreCanonicalAssert.assertEpubFragmentCanonicalEqual(
                segment.sourceInner(), Objects.requireNonNull(restored.data()), "epub hazard paragraph");
    }

    // WHEN a segment's masked form is supplied back as its own target, the system SHALL
    // produce restored content canonical-equal to its source content.
    @Test
    void unmask_fb2HazardParagraph_restoresCanonicalEqualToSource() {
        final Path source = Fb2Fixtures.hazardParagraph(tempDir.resolve("book.fb2"));
        final DocumentService service = DocumentServices.newService();

        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("fb2 hazard paragraph did not open: %s", opened.error())
                .isTrue();
        final Segment segment = onlySegment(Objects.requireNonNull(opened.data()));

        final Result<String> restored = service.unmask(BookFormat.FB2, segment, segment.masked());
        assertThat(restored.isOk())
                .withFailMessage("fb2 hazard paragraph did not restore: %s", restored.error())
                .isTrue();
        MaskRestoreCanonicalAssert.assertFb2FragmentCanonicalEqual(
                segment.sourceInner(), Objects.requireNonNull(restored.data()), "fb2 hazard paragraph");
    }

    private static Segment onlySegment(Document document) {
        final List<Segment> segments =
                document.units().stream().flatMap(u -> u.segments().stream()).toList();
        assertThat(segments).as("hazard paragraph segment count").hasSize(1);
        return segments.get(0);
    }
}
