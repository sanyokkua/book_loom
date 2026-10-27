package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CountingIdSource;
import ua.bookloom.pipeline.ProjectServices;
import ua.bookloom.pipeline.TestBooks;

/**
 * Creating a project again resets its stored decisions; the earlier translation can instead be counted through list and continued through open.
 * The service, the document module and the in-memory repositories are real.
 */
class ProjectServiceEarlierWorkTest {

    private static final String FOUR_PARAGRAPHS =
            "It was a dark night.\n\nThe rain kept falling.\n\nNobody came to the door.\n\nMorning was slow.\n";
    private static final String OTHER_TEXT = "A different book entirely.\n\nNothing here matches.\n";

    @TempDir
    private Path tempDir;

    private CountingIdSource ids;
    private ProjectService service;
    private ProjectRepository projects;
    private SegmentRepository segments;
    private Path book;

    @BeforeEach
    void setUp() {
        ids = new CountingIdSource();
        final Injector injector =
                Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule(), binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(ids);
                });
        service = injector.getInstance(ProjectServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
        segments = injector.getInstance(SegmentRepository.class);
        book = TestBooks.markdown(tempDir.resolve("Book.md"), FOUR_PARAGRAPHS);
    }

    private ImportedBook importOk() {
        final Result<ImportedBook> result = service.create(book);
        assertThat(result.isOk()).as("import result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private List<SegmentRecord> records(final String id) {
        return Objects.requireNonNull(segments.all(id).data());
    }

    private void decide(final String id, final int index, final SegmentStatus status, final String target) {
        final String segmentId = records(id).get(index).segmentId();
        segments.update(id, segmentId, record -> record.withStatus(status).withMachineTarget(target, target));
    }

    private int decided(final Path source) {
        return ProjectServices.decidedOf(service, source);
    }

    @Test
    void list_fileNeverImported_reportsNothingForIt() {
        assertThat(decided(book)).isZero();
    }

    @Test
    void list_importedWithNothingDecided_isZero() {
        importOk();

        assertThat(decided(book)).isZero();
    }

    // Accepted, revised and flagged segments all hold a decision; a pending one does not.
    @Test
    void list_acceptedRevisedAndFlaggedSegments_countsAllThree() {
        final String id = Objects.requireNonNull(importOk().projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");
        decide(id, 1, SegmentStatus.REVISED, "Дощ не вщухав.");
        decide(id, 2, SegmentStatus.FLAGGED, "Чернетка.");

        assertThat(decided(book)).isEqualTo(3);
    }

    // IF stored decisions of another text counted, THEN the person would be asked to continue a translation of a book
    // this file no longer is.
    @Test
    void list_fileContentChangedSinceImport_isZero() {
        final String id = Objects.requireNonNull(importOk().projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");

        TestBooks.markdown(book, OTHER_TEXT);

        assertThat(decided(book)).isZero();
    }

    // The FictionBook reader hashes the unzipped member, not the archive; the stored hash must still match its own
    // file.
    @Test
    void list_zippedFictionBookUnchangedSinceImport_countsTheDecisions() {
        final Path zipped = TestBooks.zippedFb2(
                tempDir.resolve("Book.fb2.zip"), List.of("It was a dark night.", "The rain kept falling."), "en");
        final String id = Objects.requireNonNull(
                Objects.requireNonNull(service.create(zipped).data()).projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");

        assertThat(decided(zipped)).isEqualTo(1);
        assertThat(service.open(id).isOk()).isTrue();
    }

    // IF Continue reset the records, THEN the question would promise work it then throws away.
    @Test
    void open_afterDecisions_keepsTheStoredDecisionsAndTheStoredBrief() {
        final ImportedBook first = importOk();
        final String id = Objects.requireNonNull(first.projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");
        final int stored = records(id).size();
        final BookBrief fast = withDial(Objects.requireNonNull(first.brief()), QualityDial.FAST);
        assertThat(service.updateBrief(id, fast).isOk()).isTrue();

        final Result<ProjectOpening> continued = service.open(id);

        assertThat(continued.isOk()).as("continue result %s", continued.error()).isTrue();
        final ImportedBook again = ProjectServices.opened(continued);
        assertThat(again.projectId()).isEqualTo(id);
        assertThat(Objects.requireNonNull(again.brief()).dial()).isEqualTo(QualityDial.FAST);
        assertThat(records(id).get(0).status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(records(id).get(0).machineTarget()).isEqualTo("Була темна ніч.");
        assertThat(records(id)).hasSize(stored);
        assertThat(decided(book)).isEqualTo(1);
    }

    // The continued book must be open for the screens that follow: a plan needs the document in memory.
    @Test
    void open_afterDecisions_leavesTheBookOpenForAPlan() {
        final String id = Objects.requireNonNull(importOk().projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");
        service.close(id);

        assertThat(service.open(id).isOk()).isTrue();

        final Result<BookPlan> plan = service.plan(id);
        assertThat(plan.isOk()).as("plan result %s", plan.error()).isTrue();
    }

    @Test
    void open_unknownProjectId_answersValidation() {
        final Result<ProjectOpening> continued = service.open("no-such-project");

        assertThat(Objects.requireNonNull(continued.error()).code()).isEqualTo(ErrorCode.validation);
    }

    // IF a rewritten file were opened, THEN the stored decisions would be shown against another text.
    @Test
    void open_fileContentChangedSinceImport_answersBookFileChangedAndKeepsDecisions() {
        final String id = Objects.requireNonNull(importOk().projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");
        TestBooks.markdown(book, OTHER_TEXT);

        final Result<ProjectOpening> continued = service.open(id);

        assertThat(continued.data()).isInstanceOf(ProjectOpening.BookFileChanged.class);
        assertThat(records(id).get(0).status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    // Start over is the existing import: it resets every record, so nothing is left to ask about.
    @Test
    void create_afterDecisions_resetsThemSoNothingIsDecidedAnyMore() {
        final String id = Objects.requireNonNull(importOk().projectId());
        decide(id, 0, SegmentStatus.ACCEPTED, "Була темна ніч.");
        assertThat(decided(book)).isEqualTo(1);

        importOk();

        assertThat(decided(book)).isZero();
        assertThat(records(id)).allSatisfy(record -> assertThat(record.status()).isEqualTo(SegmentStatus.PENDING));
        assertThat(projects.find(id).isOk()).isTrue();
    }

    private static BookBrief withDial(final BookBrief brief, final QualityDial dial) {
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                dial);
    }
}
