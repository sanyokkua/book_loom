package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.review.ReviewQueries;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * A name swap by backward revision never hides a real problem: a FLAGGED segment takes the swapped name but stays
 * FLAGGED — in the review list, the report's flagged list and the flagged count — unless every finding it carries is a
 * glossary finding the swap fixes. The pass runs through a real export with the report side file.
 */
class RevisionWriterFlaggedTest {

    private static final String HALE_LEFT = "ch05.xhtml:11";
    private static final String OLD_TARGET = "Хейл пішов.";
    private static final String SWEPT_TARGET = "Гейл пішов.";
    private static final QaFinding OMISSION = new QaFinding("omission", Severity.MEDIUM, "drops a clause", "judge");

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private String projectId;
    private Path destination;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        final List<String> names = IntStream.rangeClosed(1, 5)
                .mapToObj(number -> String.format("ch%02d.xhtml", number))
                .toList();
        final List<List<String>> chapters = IntStream.rangeClosed(1, 5)
                .mapToObj(RevisionWriterFlaggedTest::chapter)
                .toList();
        projectId = fixture.importBook(TestBooks.epubAtRoot(tempDir.resolve("Book.epub"), names, chapters), "en");
        destination = tempDir.resolve("Book.uk.epub");
    }

    // ch5 · p12 is FLAGGED for an omission; the swap gives it the new name but the omission is still there to review.
    @Test
    void run_flaggedWithOmissionSwept_takesTheNameAndStaysFlaggedEverywhere() {
        decided(SegmentStatus.FLAGGED, OMISSION);

        final ExportReport report = sweptThroughExport();

        final SegmentRecord record = stored();
        assertThat(record.machineTarget()).isEqualTo(SWEPT_TARGET);
        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.findings()).containsExactly(OMISSION);
        assertThat(flaggedQueue()).containsExactly(HALE_LEFT);
        assertThat(report.flaggedWritten()).isEqualTo(1);
        assertThat(ExportJobFixture.read(tempDir.resolve("Book.uk.report.md")))
                .contains("- ch5 · p12: omission (medium) — drops a clause\n");
        assertThat(ExportJobFixture.zipEntry(destination, "ch05.xhtml")).contains(SWEPT_TARGET);
    }

    // Only a glossary finding, fixed by the swap: REVISED and off the review list, the finding kept as history.
    @Test
    void run_flaggedWithOnlyAGlossaryFindingSwept_becomesRevisedAndKeepsTheFinding() {
        final QaFinding glossary =
                new QaFinding("glossary", Severity.MEDIUM, "a locked term's rendering is missing", "glossary");
        decided(SegmentStatus.FLAGGED, glossary);

        final ExportReport report = sweptThroughExport();

        final SegmentRecord record = stored();
        assertThat(record.machineTarget()).isEqualTo(SWEPT_TARGET);
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.findings()).containsExactly(glossary);
        assertThat(flaggedQueue()).isEmpty();
        assertThat(report.flaggedWritten()).isZero();
    }

    // The status after the swap, by the status and the one finding the segment held before it.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, omission, length, REVISED",
        "FLAGGED, glossary, locked-term, REVISED",
        "FLAGGED, language, script, FLAGGED",
        "FLAGGED, meaning, judge, FLAGGED"
    })
    void run_lockedNameSwept_statusFollowsTheFindings(
            final SegmentStatus before, final String kind, final String raisedBy, final SegmentStatus after) {
        decided(before, new QaFinding(kind, Severity.LOW, "note", raisedBy));

        sweptThroughExport();

        assertThat(stored())
                .extracting(SegmentRecord::machineTarget, SegmentRecord::status)
                .containsExactly(SWEPT_TARGET, after);
    }

    // A FLAGGED segment with no finding recorded (a low score) has nothing the swap can be said to fix.
    @Test
    void run_flaggedWithoutFindingSwept_staysFlagged() {
        decided(SegmentStatus.FLAGGED, null);

        sweptThroughExport();

        assertThat(stored())
                .extracting(SegmentRecord::machineTarget, SegmentRecord::status)
                .containsExactly(SWEPT_TARGET, SegmentStatus.FLAGGED);
    }

    private void decided(final SegmentStatus status, @Nullable final QaFinding finding) {
        fixture.decide(
                projectId,
                HALE_LEFT,
                record -> record.withStatus(status)
                        .withMachineTarget(OLD_TARGET, OLD_TARGET)
                        .withFindings(finding == null ? List.of() : List.of(finding)));
    }

    /** Hale was locked as Хейл when ch5 · p12 was decided; the person renames him Гейл, then exports with the pass. */
    private ExportReport sweptThroughExport() {
        final GlossaryEntry before = hale("Хейл");
        ok(fixture.glossary().add(before));
        final GlossaryEntry after = hale("Гейл");
        ok(fixture.glossary().update(after));
        DeferralRegister.termChanged(before, after, fixture.records(projectId))
                .forEach(deferral -> ok(fixture.deferrals().add(deferral)));
        return ok(fixture.export(
                new ExportRequest(projectId, destination, false, Set.of(SideFile.QUALITY_REPORT), true)));
    }

    private List<String> flaggedQueue() {
        final ReviewQueries queries =
                new ReviewQueries(fixture.openProjects(), fixture.projects(), fixture.segments(), fixture.deferrals());
        return ok(queries.queue(projectId, ReviewFilter.ALL_FLAGGED)).stream()
                .map(SegmentView::segmentId)
                .toList();
    }

    private SegmentRecord stored() {
        return fixture.records(projectId).stream()
                .filter(record -> record.segmentId().equals(HALE_LEFT))
                .findFirst()
                .orElseThrow();
    }

    private GlossaryEntry hale(final String target) {
        return new GlossaryEntry(
                GlossaryIds.of(projectId, "Hale"), projectId, "Hale", target, TermType.CHARACTER, Gender.MALE, true);
    }

    private static List<String> chapter(final int number) {
        return IntStream.range(0, 12)
                .mapToObj(index -> number == 5 && index == 11 ? "Hale went away." : "Chapter " + number + " line.")
                .toList();
    }
}
