package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TestBooks;

/** A real EPUB export writes the title once: package, NCX docTitle and label, navigation and the page's own title. */
class ExportTitleConsistencyTest {

    private static final String TITLE = "Бурштин";
    private static final String OTHER = "Інше";

    @TempDir
    private Path tempDir;

    // IF each place kept the model's own rendering, THEN the package, the NCX and a page title read three ways.
    @Test
    void export_titleSegmentTranslatedDifferentlyElsewhere_everyPlaceOfTheTitleIsTheBriefTitle() {
        final ExportJobFixture fixture = new ExportJobFixture();
        final Path source =
                TestBooks.epubWithNcx(tempDir.resolve("Book.epub"), List.of(List.of("He left.")), "Test", "Test");
        final String projectId = fixture.importBook(source, "en");
        fixture.accept(projectId, fixture.bodyRecords(projectId).getFirst().segmentId(), "Він пішов.");
        fixture.accept(projectId, "aux:title", TITLE);
        fixture.records(projectId).stream()
                .filter(record ->
                        record.unitId().equals("aux") && !record.segmentId().equals("aux:title"))
                .map(SegmentRecord::segmentId)
                .forEach(id -> fixture.accept(projectId, id, OTHER));
        final Path destination = tempDir.resolve("Book.uk.epub");

        final ExportReport report = ok(fixture.export(ExportJobFixture.request(projectId, destination, false)));

        assertThat(report).isNotNull();
        assertThat(ExportJobFixture.zipEntry(destination, "OEBPS/content.opf")).contains(">" + TITLE + "</dc:title>");
        assertThat(ExportJobFixture.zipEntry(destination, "OEBPS/toc.ncx"))
                .contains("<docTitle><text>" + TITLE + "</text></docTitle>")
                .contains("<text>" + TITLE + "</text></navLabel>")
                .doesNotContain(OTHER);
        assertThat(ExportJobFixture.zipEntry(destination, "OEBPS/nav.xhtml"))
                .contains(TITLE)
                .doesNotContain(OTHER);
        assertThat(ExportJobFixture.zipEntry(destination, "OEBPS/ch0.xhtml")).contains("<title>" + TITLE + "</title>");
    }

    // The test's premise: these auxiliary segments exist and are not all the title segment itself.
    @Test
    void import_epubWithNcx_holdsDocTitleNavigationAndPageTitleSegments() {
        final ExportJobFixture fixture = new ExportJobFixture();
        final Path source =
                TestBooks.epubWithNcx(tempDir.resolve("Book.epub"), List.of(List.of("He left.")), "Test", "Test");
        final String projectId = fixture.importBook(source, "en");

        assertThat(fixture.records(projectId))
                .extracting(SegmentRecord::kind)
                .containsAll(Set.of(SegmentKind.METADATA_TITLE, SegmentKind.NAV_LABEL, SegmentKind.TITLE));
    }
}
