package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.BookExporterTestSupport.error;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;
import ua.bookloom.pipeline.export.BookExporterTestSupport.BlockingClosePort;
import ua.bookloom.pipeline.export.BookExporterTestSupport.ScriptedMoveOperation;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.project.ProjectServiceImpl;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/** The export service and job over the real document module and the in-memory repositories. */
class ExportServiceImplTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir
    private Path tempDir;

    private Injector injector;
    private ExportServiceImpl service;
    private ProjectServiceImpl projectService;
    private ProjectRepository projects;
    private SegmentRepository segments;
    private OpenProjects openProjects;
    private @Nullable ExecutorService workers;

    @BeforeEach
    void setUp() {
        injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        service = injector.getInstance(ExportServiceImpl.class);
        projectService = injector.getInstance(ProjectServiceImpl.class);
        projects = injector.getInstance(ProjectRepository.class);
        segments = injector.getInstance(SegmentRepository.class);
        openProjects = injector.getInstance(OpenProjects.class);
    }

    @AfterEach
    void tearDown() {
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    // Removing the owning-module binding would make resolving the public port fail.
    @Test
    void injector_pipelineModule_resolvesExportServiceImpl() {
        final ExportService bound = Guice.createInjector(
                        new DocumentModule(), new PersistenceModule(), new PipelineModule(), new ReviewModeTestModule())
                .getInstance(ExportService.class);

        assertThat(bound).isInstanceOf(ExportServiceImpl.class);
    }

    // Reading the wrong status column would write a flagged draft as source or a pending record as translated.
    @Test
    void run_acceptedFlaggedAndPendingRecords_eachWritesItsEffectiveTarget() throws IOException {
        final String id = importBook("One.\n\nTwo.\n\nThree.", "uk");
        final List<SegmentRecord> records = records(id);
        decide(id, records.get(0), SegmentStatus.ACCEPTED, "ОДИН.", null);
        decide(id, records.get(1), SegmentStatus.FLAGGED, "ДВА.", null);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = run(id, destination, false);

        assertThat(Files.readString(destination)).contains("ОДИН.", "ДВА.", "Three.");
        assertThat(Files.readString(destination)).doesNotContain("One.", "Two.");
        assertThat(report)
                .extracting(
                        ExportReport::destination,
                        ExportReport::written,
                        ExportReport::pending,
                        ExportReport::sourceKept,
                        ExportReport::flaggedWritten)
                .containsExactly(destination, 2, 1, 0, 1);
    }

    // Preferring the machine target would drop the person's saved edit from the book.
    @Test
    void run_revisedRecord_writesTheUserTarget() throws IOException {
        final String id = importBook("One.", "uk");
        decide(id, records(id).get(0), SegmentStatus.REVISED, "ОДИН.", "ПЕРШИЙ.");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = run(id, destination, false);

        assertThat(Files.readString(destination)).contains("ПЕРШИЙ.").doesNotContain("ОДИН.");
        assertThat(report.written()).isEqualTo(1);
    }

    // A flagged record no draft ever passed for has nothing to write, so it is a pending segment kept as source.
    @Test
    void run_flaggedWithoutMachineTarget_writesSourceAndCountsItPending() throws IOException {
        final String id = importBook("One.", "uk");
        decide(id, records(id).get(0), SegmentStatus.FLAGGED, null, null);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = run(id, destination, false);

        assertThat(Files.readString(destination)).contains("One.");
        assertThat(report)
                .extracting(ExportReport::written, ExportReport::pending, ExportReport::flaggedWritten)
                .containsExactly(0, 1, 0);
    }

    // The brief's switch, read at export time, keeps an excluded auxiliary record as source whatever it holds.
    @Test
    void run_frontmatterKeptAsSource_writesItUnchangedAndCountsItApart() throws IOException {
        final String id = importBook("---\ntitle: The Lighthouse\n---\n\nOne.", "uk");
        final SegmentRecord frontmatter = records(id).stream()
                .filter(record -> record.kind() == SegmentKind.FRONTMATTER_VALUE)
                .findFirst()
                .orElseThrow();
        decide(id, frontmatter, SegmentStatus.ACCEPTED, "МАЯК", null);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = run(id, destination, false);

        assertThat(Files.readString(destination)).contains("The Lighthouse").doesNotContain("МАЯК");
        assertThat(report.sourceKept()).isEqualTo(1);
    }

    // Without a target language the writer has nothing to declare, so the export is refused before any file appears.
    @Test
    void run_briefWithoutTargetLanguage_returnsValidation() {
        final String id = importBook("One.", null);
        final ExportJob job = newExport(id, tempDir.resolve("Book.uk.md"), false);

        final Result<ExportReport> result = job.run();

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // Replacing without the person's say-so would destroy a file the person chose to keep.
    @Test
    void run_existingDestinationWithoutOverwrite_returnsValidationAndKeepsIt() throws IOException {
        final String id = importBook("One.", "uk");
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");

        final Result<ExportReport> result = newExport(id, destination, false).run();

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(destination)).isEqualTo("Existing.");
    }

    @Test
    void run_existingDestinationWithOverwrite_replacesIt() throws IOException {
        final String id = importBook("One.", "uk");
        decide(id, records(id).get(0), SegmentStatus.ACCEPTED, "ОДИН.", null);
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");

        run(id, destination, true);

        assertThat(Files.readString(destination)).contains("ОДИН.");
    }

    // The export writes from the opened book, so a closed one is refused instead of reopened behind the person.
    @Test
    void run_bookNoLongerOpen_returnsValidation() {
        final String id = importBook("One.", "uk");
        final ExportJob job = newExport(id, tempDir.resolve("Book.uk.md"), false);
        openProjects.remove(id);

        final Result<ExportReport> result = job.run();

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // A failed publication must leave neither the destination nor the hidden temporary file behind.
    @Test
    void run_publishMoveFails_returnsInternalAndLeavesNoFile() {
        final String id = importBook("One.", "uk");
        final Path destination = tempDir.resolve("Book.uk.md");
        final ScriptedMoveOperation moves = new ScriptedMoveOperation()
                .answer(Result.err(error(ErrorCode.internal, new IOException("scripted move failure"))));
        final ExportJob job = dataOf(service.newExportWith(request(id, destination, false), null, moves));

        final Result<ExportReport> result = job.run();

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve(".Book.uk.md")).doesNotExist();
    }

    // Omitting the exporter's final cancellation check would publish this destination after cancel().
    @Test
    void run_cancelBeforePublication_leavesNoDestination() throws Exception {
        final String id = importBook("One.", "uk");
        final Path destination = tempDir.resolve("Book.uk.md");
        final BlockingClosePort port = new BlockingClosePort(TestDocuments.documents(), 2);
        final ExportServiceImpl blocking = new ExportServiceImpl(
                projects,
                segments,
                openProjects,
                port,
                injector.getInstance(ConsistencyPass.class),
                injector.getInstance(GlossaryRepository.class));
        final ExportJob job = dataOf(blocking.newExport(request(id, destination, false), null));
        workers = Executors.newSingleThreadExecutor();

        final Future<Result<ExportReport>> running = workers.submit(job::run);
        port.awaitBlockingClose();
        job.cancel();
        port.releaseClose();

        final Result<ExportReport> result = running.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve(".Book.uk.md")).doesNotExist();
    }

    private String importBook(final String content, @Nullable final String targetLanguage) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), content);
        final String id =
                Objects.requireNonNull(dataOf(projectService.importBook(source)).projectId(), "project id");
        final Project project = dataOf(projects.find(id)).orElseThrow();
        projects.save(project.withBrief(withTarget(project.brief(), targetLanguage)));
        return id;
    }

    private void decide(
            final String projectId,
            final SegmentRecord record,
            final SegmentStatus status,
            @Nullable final String machine,
            @Nullable final String user) {
        segments.update(
                projectId,
                record.segmentId(),
                stored -> stored.withStatus(status)
                        .withMachineTarget(machine, machine)
                        .withUserTarget(user, user));
    }

    private ExportReport run(final String projectId, final Path destination, final boolean overwrite) {
        return dataOf(newExport(projectId, destination, overwrite).run());
    }

    private ExportJob newExport(final String projectId, final Path destination, final boolean overwrite) {
        return dataOf(service.newExport(request(projectId, destination, overwrite), null));
    }

    private static ExportRequest request(final String projectId, final Path destination, final boolean overwrite) {
        return new ExportRequest(projectId, destination, overwrite, Set.of(), false);
    }

    private static BookBrief withTarget(final BookBrief brief, @Nullable final String targetLanguage) {
        return new BookBrief(
                brief.sourceLanguage(),
                targetLanguage,
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
                brief.dial());
    }

    private List<SegmentRecord> records(final String projectId) {
        return dataOf(segments.all(projectId));
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
