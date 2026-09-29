package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.hiddenFiles;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.read;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/** The chosen side files land beside the written book, named after it with its format suffix removed. */
class ExportJobSideFilesTest {

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    // All three chosen for Frankenstein.uk.epub give three files named after it, reported with the book.
    @Test
    void run_allThreeChosen_writesThemBesideTheBook() {
        final String id = frankenstein();
        final Path destination = tempDir.resolve("Frankenstein.uk.epub");

        final ExportReport report = ok(fixture.export(request(id, destination, false, Set.of(SideFile.values()))));

        assertThat(report.sideFiles())
                .containsExactlyInAnyOrder(
                        tempDir.resolve("Frankenstein.uk.glossary.csv"),
                        tempDir.resolve("Frankenstein.uk.bilingual.html"),
                        tempDir.resolve("Frankenstein.uk.report.md"));
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // The glossary uses the columns Names & style imports, so a sequel can load it back.
    @Test
    void run_glossaryChosen_writesImportColumns() {
        final String id = frankenstein();

        ok(fixture.export(request(id, tempDir.resolve("Frankenstein.uk.epub"), false, Set.of(SideFile.GLOSSARY_CSV))));

        assertThat(read(tempDir.resolve("Frankenstein.uk.glossary.csv")))
                .isEqualTo("term,target,type,gender,locked\r\nVictor,Віктор,character,male,true\r\n");
    }

    // One row per segment, source beside what was written, self-contained: no URL, script or outside reference.
    @Test
    void run_bilingualChosen_writesOneRowPerSegmentWithNoReference() {
        final String id = frankenstein();

        ok(fixture.export(
                request(id, tempDir.resolve("Frankenstein.uk.epub"), false, Set.of(SideFile.BILINGUAL_HTML))));

        final String html = read(tempDir.resolve("Frankenstein.uk.bilingual.html"));
        assertThat(html.split("<tr>", -1)).hasSize(fixture.records(id).size() + 1);
        assertThat(html)
                .contains("<td>Victor &amp; I left.</td><td>Віктор &amp; я пішли.</td>")
                .contains("<td>The end.</td><td>The end.</td>")
                .doesNotContain("http", "<script", "src=", "href=");
    }

    // The report states the counts — the untranslated book title and page title are pending beside "The end." — names
    // each flagged segment by its locator with its findings, and says whether the consistency pass ran.
    @Test
    void run_reportChosen_writesCountsAndFlaggedSegments() {
        final String id = frankenstein();

        ok(fixture.export(
                request(id, tempDir.resolve("Frankenstein.uk.epub"), false, Set.of(SideFile.QUALITY_REPORT))));

        assertThat(read(tempDir.resolve("Frankenstein.uk.report.md")))
                .contains(
                        "- Written with a translation: 2",
                        "- Pending, written in the source language: 3",
                        "- Flagged, written with the machine translation: 1",
                        "- ch1 · p02: omission (medium) — a clause is missing",
                        "The consistency pass was not run.");
    }

    // A composite format suffix is removed whole.
    @Test
    void run_zippedFb2WithGlossary_namesTheGlossaryWithoutTheContainerSuffix() {
        final String id =
                fixture.importBook(TestBooks.zippedFb2(tempDir.resolve("Kobzar.fb2.zip"), List.of("One."), "en"), "en");

        final ExportReport report = ok(fixture.export(
                request(id, tempDir.resolve("Kobzar.uk.fb2.zip"), false, Set.of(SideFile.GLOSSARY_CSV))));

        assertThat(report.sideFiles()).containsExactly(tempDir.resolve("Kobzar.uk.glossary.csv"));
        assertThat(tempDir.resolve("Kobzar.uk.glossary.csv")).exists();
    }

    // A side file that cannot be put in place after the book was is named, and the verified book stays.
    @Test
    void run_sideFileCannotBeMovedInPlace_returnsInternalNamingItAndKeepsTheBook() throws IOException {
        final String id = frankenstein();
        final Path occupied = Files.createDirectories(
                tempDir.resolve("Frankenstein.uk.report.md").resolve("inside"));
        final Path destination = tempDir.resolve("Frankenstein.uk.epub");

        final Result<ExportReport> result =
                fixture.export(request(id, destination, true, Set.of(SideFile.QUALITY_REPORT)));

        assertThat(error(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(error(result).message()).contains("Frankenstein.uk.report.md");
        assertThat(destination).exists();
        assertThat(occupied).exists();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    /** One chapter: an accepted paragraph with an ampersand, a flagged one with a finding, and a pending one. */
    private String frankenstein() {
        final String id = fixture.importBook(
                TestBooks.epub(
                        tempDir.resolve("Frankenstein.epub"),
                        List.of(List.of("Victor &amp; I left.", "He opened the door.", "The end.")),
                        "en"),
                "en");
        final List<String> ids = fixture.bodyRecords(id).stream()
                .map(record -> record.segmentId())
                .toList();
        fixture.accept(id, ids.get(0), "Віктор & я пішли.");
        fixture.flag(id, ids.get(1), "Він відчинив двері.");
        fixture.decide(
                id,
                ids.get(1),
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withFindings(
                                List.of(new QaFinding("omission", Severity.MEDIUM, "a clause is missing", "judge"))));
        ok(fixture.glossary()
                .add(new GlossaryEntry(
                        GlossaryIds.of(id, "Victor"), id, "Victor", "Віктор", TermType.CHARACTER, Gender.MALE, true)));
        return id;
    }
}
