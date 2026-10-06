package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.zipEntry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * With the consistency pass on, a locked name the person renamed is carried back into earlier chapters before the book
 * is written — never over the person's own edit, and never over a rendering the glossary did not hold.
 */
class ExportJobConsistencyTest {

    private static final String WENT = "ch02.xhtml:1";
    private static final String CAME = "ch02.xhtml:3";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private String projectId;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        projectId = fixture.importBook(
                TestBooks.epubAtRoot(
                        tempDir.resolve("Book.epub"),
                        List.of("ch01.xhtml", "ch02.xhtml"),
                        List.of(
                                List.of("One.", "Two."),
                                List.of("Zero.", "Justine went away.", "Then.", "Justine came in."))),
                "en");
    }

    // Джастін held when chapter 2 was accepted, renamed to Жустіна and locked: swept, but the edit stays.
    @Test
    void run_passOnAfterLockedRename_writesNewNameAndKeepsTheEdit() {
        final GlossaryEntry before = justine("Джастін");
        ok(fixture.glossary().add(before));
        fixture.accept(projectId, WENT, "Джастін пішла.");
        fixture.decide(
                projectId,
                CAME,
                record -> record.withStatus(SegmentStatus.REVISED)
                        .withMachineTarget("Джастін увійшла.", "Джастін увійшла.")
                        .withUserTarget("Джастін прийшла.", "Джастін прийшла.")
                        .withReviewed(true));
        rename(before, justine("Жустіна"));
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(new ExportRequest(projectId, destination, false, Set.of(SideFile.QUALITY_REPORT), true)));

        assertThat(zipEntry(destination, "ch02.xhtml")).contains("Жустіна пішла.", "Джастін прийшла.");
        assertThat(ExportJobFixture.read(tempDir.resolve("Book.uk.report.md")))
                .contains("ch2 · p02: locked term substituted");
    }

    // The glossary held no target while chapter 2 was accepted, so the model's own Джастін is not swept.
    @Test
    void run_passOnAfterTargetAddedLater_keepsTheModelsRendering() {
        final GlossaryEntry before = justine(null);
        ok(fixture.glossary().add(before));
        fixture.accept(projectId, WENT, "Джастін пішла.");
        rename(before, justine("Жустіна"));
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(new ExportRequest(projectId, destination, false, Set.of(), true)));

        assertThat(zipEntry(destination, "ch02.xhtml"))
                .contains("Джастін пішла.")
                .doesNotContain("Жустіна");
    }

    // The report says the pass swept one segment, so the screen can show it.
    @Test
    void run_passOnWithModelAndASweptName_reportsTheSubstitution() {
        final GlossaryEntry before = justine("Джастін");
        ok(fixture.glossary().add(before));
        fixture.accept(projectId, WENT, "Джастін пішла.");
        rename(before, justine("Жустіна"));

        final ExportReport report =
                exportWithModel(new ExportRequest(projectId, tempDir.resolve("A.uk.epub"), false, Set.of(), true));

        assertThat(report.consistency()).isEqualTo(new ConsistencySummary(ConsistencySummary.Status.RAN, 1, 0));
    }

    // A pass that finds nothing to change still says it ran.
    @Test
    void run_passOnWithModelAndNothingToChange_reportsARunThatChangedNothing() {
        fixture.accept(projectId, WENT, "Джастін пішла.");

        final ExportReport report =
                exportWithModel(new ExportRequest(projectId, tempDir.resolve("B.uk.epub"), false, Set.of(), true));

        assertThat(report.consistency()).isEqualTo(new ConsistencySummary(ConsistencySummary.Status.RAN, 0, 0));
    }

    // A pass that changes nothing says what it waits for: the segments whose character has no gender yet.
    @Test
    void run_passOnWithOpenGenderDeferrals_reportsTheirCount() {
        fixture.accept(projectId, WENT, "Джастін пішла.");
        ok(fixture.deferrals()
                .add(new Deferral("d1", projectId, WENT, DeferralReason.GENDER_UNKNOWN, "Justine", null, null, null)));

        final ExportReport report =
                exportWithModel(new ExportRequest(projectId, tempDir.resolve("E.uk.epub"), false, Set.of(), true));

        assertThat(report.consistency().adjusted()).isZero();
        assertThat(report.consistency().openDeferrals()).containsExactly(Map.entry(DeferralReason.GENDER_UNKNOWN, 1));
    }

    // Without a model the name sweep still runs and the report says the gender step was skipped.
    @Test
    void run_passOnWithNoModel_reportsTheSweepAndTheSkippedGenderStep() {
        final GlossaryEntry before = justine("Джастін");
        ok(fixture.glossary().add(before));
        fixture.accept(projectId, WENT, "Джастін пішла.");
        rename(before, justine("Жустіна"));

        final ExportReport report =
                ok(fixture.export(new ExportRequest(projectId, tempDir.resolve("C.uk.epub"), false, Set.of(), true)));

        assertThat(report.consistency())
                .isEqualTo(new ConsistencySummary(ConsistencySummary.Status.RAN_WITHOUT_MODEL, 1, 0));
    }

    // With the switch off nothing is reported.
    @Test
    void run_passOff_reportsNoPass() {
        final ExportReport report =
                ok(fixture.export(new ExportRequest(projectId, tempDir.resolve("D.uk.epub"), false, Set.of(), false)));

        assertThat(report.consistency()).isEqualTo(ConsistencySummary.NOT_RUN);
    }

    private ExportReport exportWithModel(final ExportRequest request) {
        return ok(ok(fixture.service().newExport(request, new ScriptedChatModel()))
                .run());
    }

    /** Changes the entry as the glossary service does: stores it, then records the deferrals the change leaves. */
    private void rename(final GlossaryEntry before, final GlossaryEntry after) {
        ok(fixture.glossary().update(after));
        DeferralRegister.termChanged(before, after, fixture.records(projectId))
                .forEach(deferral -> ok(fixture.deferrals().add(deferral)));
    }

    private GlossaryEntry justine(@Nullable final String target) {
        return new GlossaryEntry(
                GlossaryIds.of(projectId, "Justine"),
                projectId,
                "Justine",
                target,
                TermType.CHARACTER,
                Gender.FEMALE,
                target != null);
    }
}
