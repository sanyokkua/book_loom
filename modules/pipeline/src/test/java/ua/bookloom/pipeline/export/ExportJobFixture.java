package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.revision.ConsistencyPass;

/**
 * A real book imported through the project service over the in-memory stores, with the real export service built
 * over them — or over a decorated document port — and the engine a test runs the pseudo model with.
 */
final class ExportJobFixture {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final Injector injector =
            Guice.createInjector(new DocumentModule(), new LlmModule(), new PersistenceModule(), new PipelineModule());

    DocumentPort documents() {
        return injector.getInstance(DocumentPort.class);
    }

    SegmentRepository segments() {
        return injector.getInstance(SegmentRepository.class);
    }

    ProjectRepository projects() {
        return injector.getInstance(ProjectRepository.class);
    }

    GlossaryRepository glossary() {
        return injector.getInstance(GlossaryRepository.class);
    }

    DeferralRepository deferrals() {
        return injector.getInstance(DeferralRepository.class);
    }

    RunRepository runs() {
        return injector.getInstance(RunRepository.class);
    }

    OpenProjects openProjects() {
        return injector.getInstance(OpenProjects.class);
    }

    TranslationEngine engine() {
        return injector.getInstance(TranslationEngine.class);
    }

    ExportServiceImpl service() {
        return serviceOver(documents());
    }

    ExportServiceImpl serviceOver(final DocumentPort port) {
        return new ExportServiceImpl(
                projects(), segments(), openProjects(), port, injector.getInstance(ConsistencyPass.class), glossary());
    }

    /** Imports {@code source} and briefs it from {@code sourceLanguage} to Ukrainian with the default switches. */
    String importBook(final Path source, final String sourceLanguage) {
        return importBook(source, sourceLanguage, AlsoTranslate.defaults());
    }

    String importBook(final Path source, final String sourceLanguage, final AlsoTranslate alsoTranslate) {
        final ProjectService service = injector.getInstance(ProjectService.class);
        final String id = Objects.requireNonNull(ok(service.importBook(source)).projectId(), "project id");
        ok(service.updateBrief(id, brief(sourceLanguage, alsoTranslate)));
        return id;
    }

    List<SegmentRecord> records(final String projectId) {
        return ok(segments().all(projectId));
    }

    List<SegmentRecord> bodyRecords(final String projectId) {
        return records(projectId).stream()
                .filter(record -> !record.unitId().equals("aux"))
                .toList();
    }

    SegmentRecord recordOfKind(final String projectId, final SegmentKind kind) {
        return records(projectId).stream()
                .filter(record -> record.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    void decide(final String projectId, final String segmentId, final UnaryOperator<SegmentRecord> change) {
        ok(segments().update(projectId, segmentId, change));
    }

    /** Stores a segment as ACCEPTED with {@code masked} and the plain form the document port restores from it. */
    void accept(final String projectId, final String segmentId, final String masked) {
        final String plain = restored(projectId, segmentId, masked);
        decide(
                projectId,
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget(plain, masked));
    }

    /** Stores a segment as FLAGGED with {@code masked} restored as its machine target, or with none when null. */
    void flag(final String projectId, final String segmentId, @Nullable final String masked) {
        final String plain = masked == null ? null : restored(projectId, segmentId, masked);
        decide(
                projectId,
                segmentId,
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(plain, masked));
    }

    String restored(final String projectId, final String segmentId, final String masked) {
        final Document open = Objects.requireNonNull(openProjects().get(projectId), "open book");
        return ok(documents().unmask(open.format(), segment(open, segmentId), masked));
    }

    Result<ExportReport> export(final ExportRequest request) {
        return export(service(), request);
    }

    static Result<ExportReport> export(final ExportServiceImpl service, final ExportRequest request) {
        return service.newExport(request, null).flatMap(ExportJob::run);
    }

    static ExportRequest request(final String projectId, final Path destination, final boolean overwrite) {
        return new ExportRequest(projectId, destination, overwrite, Set.of(), false);
    }

    static ExportRequest request(
            final String projectId, final Path destination, final boolean overwrite, final Set<SideFile> sideFiles) {
        return new ExportRequest(projectId, destination, overwrite, sideFiles, false);
    }

    static BookBrief brief(final String sourceLanguage, final AlsoTranslate alsoTranslate) {
        final BookBrief defaults = BookBrief.defaults(sourceLanguage);
        return new BookBrief(
                sourceLanguage,
                "uk",
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                alsoTranslate,
                defaults.dial());
    }

    static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    static String zipEntry(final Path archive, final String entryName) {
        return BookExporterTestSupport.zipEntry(archive, entryName);
    }

    /** Every entry of an archive in order, each name followed by its decompressed text. */
    static List<String> entries(final Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.stream()
                    .map(entry -> entry.getName() + "\n" + zipEntry(archive, entry.getName()))
                    .toList();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /** The files in {@code folder} whose names start with a dot — the export's temporary files. */
    static List<Path> hiddenFiles(final Path folder) {
        try (var files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().startsWith("."))
                    .toList();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    static Segment segment(final Document document, final String segmentId) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(candidate -> candidate.id().equals(segmentId))
                .findFirst()
                .orElseThrow();
    }

    static List<Segment> bodySegments(final Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected ok, got " + result.error());
    }

    static AppError error(final Result<?> result) {
        final AppError error = result.error();
        assertThat(error).isNotNull();
        return Objects.requireNonNull(error, "error");
    }
}
