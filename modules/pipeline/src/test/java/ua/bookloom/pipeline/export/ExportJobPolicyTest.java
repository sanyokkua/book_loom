package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
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
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TestBooks;

/**
 * What a flagged segment is written as, in order: its accepted or edited target, the best refused candidate that passes
 * the blocking checks, the machine translation, and the source last; and when the export leaves a report beside the book.
 */
class ExportJobPolicyTest {

    private static final String SOURCE = "He opened the door.";
    private static final String CANDIDATE = "Він відчинив двері.";
    private static final String FIRST = "Book.md:0";
    private static final String SECOND = "Book.md:1";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    private String book(final String first) {
        final String id =
                fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), first + "\n\nShe left."), "en");
        fixture.accept(id, SECOND, "Вона пішла.");
        return id;
    }

    private void rejected(final String id, final String masked) {
        fixture.decide(
                id,
                FIRST,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(null, null)
                        .withRejectedTarget(masked));
    }

    // IF the refused reply were thrown away, THEN a line whose last reply only broke the quote marks stays English.
    @Test
    void run_flaggedWithNoTargetAndACleanRefusedReply_writesTheReplyNotTheSource() throws IOException {
        final String id = book(SOURCE);
        rejected(id, CANDIDATE);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(Files.readString(destination)).contains(CANDIDATE).doesNotContain(SOURCE);
        assertThat(report.sourceFallbacks()).isEmpty();
        assertThat(report.written()).isEqualTo(2);
        assertThat(report.pending()).isZero();
        assertThat(report.flaggedWritten()).isEqualTo(1);
    }

    // IF a candidate with reply residue were written, THEN a stray brace would reach the book.
    @Test
    void run_refusedReplyWithResidue_isWrittenInTheSourceAndListed() throws IOException {
        final String id = book(SOURCE);
        rejected(id, CANDIDATE + "}");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(Files.readString(destination)).contains(SOURCE).doesNotContain("}");
        assertThat(report.sourceFallbacks())
                .containsExactly(new SourceFallback(FIRST, "ch1 · p01", SourceFallback.Reason.NO_TARGET));
    }

    // IF a candidate with a Latin letter inside a Cyrillic word were written, THEN the book would hold a mixed word.
    @Test
    void run_refusedReplyWithMixedScriptWord_isWrittenInTheSource() throws IOException {
        final String id = book(SOURCE);
        rejected(id, "Він відчинив dверi.");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(Files.readString(destination)).contains(SOURCE);
    }

    // A candidate whose markup no longer matches the segment's fails the placeholder gate.
    @Test
    void run_refusedReplyThatBrokeThePlaceholders_isWrittenInTheSource() throws IOException {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe left."), "en");
        fixture.accept(id, SECOND, "Вона пішла.");
        rejected(id, "Він відчинив старі двері.");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(Files.readString(destination)).contains("He opened the *old* door.");
        assertThat(report.sourceFallbacks())
                .extracting(SourceFallback::segmentId)
                .containsExactly(FIRST);
    }

    // A refused reply is only the second choice: the machine translation of a flagged segment is its own target.
    @Test
    void run_flaggedWithAMachineTranslation_writesItAndNotTheRefusedReply() throws IOException {
        final String id = book(SOURCE);
        fixture.decide(
                id,
                FIRST,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget("Він відчинив вікно.", "Він відчинив вікно.")
                        .withRejectedTarget(CANDIDATE));
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(Files.readString(destination))
                .contains("Він відчинив вікно.")
                .doesNotContain(CANDIDATE);
    }

    // IF the report were written only on request, THEN a book with an English line could ship with no word of it.
    @Test
    void run_sourceLanguageFallbackAndNoReportChosen_writesTheReportAnyway() {
        final String id = book(SOURCE);
        fixture.flag(id, FIRST, null);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        final Path file = tempDir.resolve("Book.uk.report.md");
        assertThat(report.sideFiles()).containsExactly(file);
        assertThat(read(file))
                .contains("## Fallbacks")
                .contains("Source language: 1")
                .contains("ch1 · p01");
    }

    @Test
    void run_unresolvedBlockingFindingAndNoReportChosen_writesTheReportAnyway() {
        final String id = book(SOURCE);
        fixture.decide(
                id,
                FIRST,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget("Він «відчинив двері.", "Він «відчинив двері.")
                        .withFindings(
                                List.of(new QaFinding("fluency", Severity.HIGH, "quote left open", "quote-balance"))));
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.sideFiles()).containsExactly(tempDir.resolve("Book.uk.report.md"));
        assertThat(report.unresolvedBlocking()).containsExactly("ch1 · p01");
        assertThat(read(tempDir.resolve("Book.uk.report.md")))
                .contains("## Unresolved blocking findings")
                .contains("ch1 · p01: quote-balance")
                .contains("Machine translation: 1");
    }

    // A clean book gets no report it did not ask for.
    @Test
    void run_cleanBookAndNoReportChosen_writesNoReport() {
        final String id = book(SOURCE);
        fixture.accept(id, FIRST, CANDIDATE);

        final ExportReport report = ok(fixture.export(request(id, tempDir.resolve("Book.uk.md"), false)));

        assertThat(report.sideFiles()).isEmpty();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
    }

    // The candidate used is counted per kind and named by locator in the report the person asked for.
    @Test
    void run_candidateUsed_reportCountsItPerKind() {
        final String id = book(SOURCE);
        rejected(id, CANDIDATE);

        ok(fixture.export(request(id, tempDir.resolve("Book.uk.md"), false, Set.of(SideFile.QUALITY_REPORT))));

        assertThat(read(tempDir.resolve("Book.uk.report.md")))
                .contains("Refused reply used: 1 (ch1 · p01)")
                .contains("Machine translation: 0")
                .contains("Source language: 0");
    }

    // A forced report replaces the report of an earlier export of the same book instead of failing the export.
    @Test
    void run_forcedReportAlreadyThere_replacesIt() throws IOException {
        final String id = book(SOURCE);
        fixture.flag(id, FIRST, null);
        Files.writeString(tempDir.resolve("Book.uk.report.md"), "old");

        ok(fixture.export(request(id, tempDir.resolve("Book.uk.md"), false)));

        assertThat(read(tempDir.resolve("Book.uk.report.md"))).startsWith("# Export report");
    }
}
