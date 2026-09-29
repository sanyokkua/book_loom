package ua.bookloom.pipeline.export;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * Writes what a project stores: each segment record's effective target laid over a fresh read of the source by
 * {@link BookExporter}, so the file holds the best text every segment has today.
 */
@Slf4j
final class ExportJobImpl implements ExportJob {

    private final ExportRequest request;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final OpenProjects openProjects;
    private final ExportMoveOperation moves;
    private final DocumentPort documents;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    ExportJobImpl(
            final ExportRequest request,
            final ProjectRepository projects,
            final SegmentRepository segments,
            final OpenProjects openProjects,
            final DocumentPort documents,
            final ExportMoveOperation moves) {
        this.request = Objects.requireNonNull(request, "request");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.segments = Objects.requireNonNull(segments, "segments");
        this.openProjects = Objects.requireNonNull(openProjects, "openProjects");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.moves = Objects.requireNonNull(moves, "moves");
    }

    @Override
    public Result<ExportReport> run() {
        try {
            log.info(
                    "export starting project={} destination={} overwrite={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite());
            final Result<ExportReport> result = write();
            logOutcome(result);
            return result;
        } catch (Throwable cause) {
            return Result.err(internalError("run the export", cause));
        }
    }

    @Override
    public void cancel() {
        log.debug("export cancel requested project={}", request.projectId());
        cancelled.set(true);
    }

    private Result<ExportReport> write() {
        final Result<Project> found = findProject();
        if (found.isErr()) {
            return Result.err(errorOf(found));
        }
        final Project project = Objects.requireNonNull(found.data(), "project");
        final Document opened = openProjects.get(project.id());
        final String targetLanguage = project.brief().targetLanguage();
        final AppError refusal = refusal(opened, targetLanguage);
        if (refusal != null) {
            return Result.err(refusal);
        }
        return writeStored(project, Objects.requireNonNull(opened, "open book"), targetLanguage);
    }

    private @Nullable AppError refusal(@Nullable final Document opened, @Nullable final String targetLanguage) {
        if (targetLanguage == null) {
            return refused("target-language", "No target language is chosen", "Choose the target language first.");
        }
        if (opened == null) {
            return refused(
                    "book-open", "This project is not open", "No open project has this id; open the book again.");
        }
        final boolean occupied = Files.exists(request.destination(), LinkOption.NOFOLLOW_LINKS);
        log.debug("export check=destination-free outcome={}", occupied ? "occupied" : "free");
        if (occupied && !request.overwrite()) {
            return refused(
                    "destination-exists",
                    "This destination already exists",
                    "Allow replacing it, or choose another file.");
        }
        return null;
    }

    private Result<ExportReport> writeStored(
            final Project project, final Document opened, @Nullable final String targetLanguage) {
        final Set<SegmentKind> kept = project.brief().alsoTranslate().keptKinds();
        final Result<List<SegmentRecord>> records = segments.all(project.id());
        if (records.isErr()) {
            return Result.err(errorOf(records));
        }
        final Result<SegmentCounts> counts = segments.countsByStatus(project.id(), kept);
        if (counts.isErr()) {
            return Result.err(errorOf(counts));
        }
        final List<SegmentRecord> stored = Objects.requireNonNull(records.data(), "records");
        final ExportPlan plan = new ExportPlan(
                project.source(),
                request.destination(),
                Objects.requireNonNull(targetLanguage, "target language"),
                request.overwrite());
        final Result<Path> written = new BookExporter(documents, moves)
                .export(plan, DecidedBook.apply(opened, stored, kept), cancelled::get);
        if (written.isErr()) {
            return Result.err(errorOf(written));
        }
        return Result.ok(report(
                Objects.requireNonNull(written.data(), "written path"),
                Objects.requireNonNull(counts.data(), "counts"),
                stored,
                kept));
    }

    /**
     * {@code written} counts every segment that carries a target; a flagged record with none is a pending segment
     * written as source, so it moves from {@code flagged} to {@code pending}.
     */
    private static ExportReport report(
            final Path destination,
            final SegmentCounts counts,
            final List<SegmentRecord> stored,
            final Set<SegmentKind> kept) {
        final int flaggedWritten = (int) stored.stream()
                .filter(record -> record.status() == SegmentStatus.FLAGGED)
                .filter(record -> !record.isKeptAsSource(kept))
                .filter(record -> record.machineTarget() != null)
                .count();
        final int flaggedWithoutTarget = counts.flagged() - flaggedWritten;
        log.debug(
                "export records applied pending={} accepted={} revised={} flaggedWritten={} flaggedWithoutTarget={}"
                        + " sourceKept={}",
                counts.pending(),
                counts.accepted(),
                counts.revised(),
                flaggedWritten,
                flaggedWithoutTarget,
                counts.sourceKept());
        return new ExportReport(
                destination,
                counts.accepted() + counts.revised() + flaggedWritten,
                counts.pending() + flaggedWithoutTarget,
                counts.sourceKept(),
                flaggedWritten,
                0,
                0,
                List.of(),
                0);
    }

    private Result<Project> findProject() {
        final Result<Optional<Project>> found = projects.find(request.projectId());
        if (found.isErr()) {
            return Result.err(errorOf(found));
        }
        return Objects.requireNonNull(found.data(), "found")
                .map(Result::ok)
                .orElseGet(() -> Result.err(unknownProject(request.projectId())));
    }

    private void logOutcome(final Result<ExportReport> result) {
        if (result.isErr()) {
            log.warn(
                    "export ended project={} code={}",
                    request.projectId(),
                    errorOf(result).code());
            return;
        }
        final ExportReport report = Objects.requireNonNull(result.data(), "report");
        log.info(
                "export finished project={} destination={} written={} pending={} sourceKept={} flaggedWritten={}",
                request.projectId(),
                report.destination(),
                report.written(),
                report.pending(),
                report.sourceKept(),
                report.flaggedWritten());
    }

    private static AppError refused(final String check, final String title, final String message) {
        log.warn("Refused export check={} code={}", check, ErrorCode.validation);
        return AppError.of(ErrorCode.validation, title, message);
    }

    static AppError unknownProject(final String projectId) {
        log.warn("unknown project={}", projectId);
        return AppError.of(
                ErrorCode.validation,
                "This project is not open",
                "No open project has this id; import the book again.");
    }

    static AppError internalError(final String action, final Throwable cause) {
        log.error("Failed to {}", action, cause);
        return AppError.of(
                ErrorCode.internal,
                "This book could not be exported",
                "An unexpected error stopped the export before publication.",
                null,
                cause);
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }
}
