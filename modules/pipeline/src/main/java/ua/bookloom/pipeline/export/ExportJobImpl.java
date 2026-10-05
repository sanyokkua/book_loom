package ua.bookloom.pipeline.export;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
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
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/**
 * Writes what a project stores: each segment record's effective target laid over a fresh read of the source by
 * {@link BookExporter}, so the file holds the best text every segment has today, then the chosen side files beside it.
 * An occupied path, or a side file that would land on the source or the book, is refused before anything slow; the
 * consistency pass, when asked, runs before the records are read. A cancel is honoured at each step — before the pass,
 * inside its model calls, before the book is written and before each side file — so a cancel never leaves a partial
 * file, and its message says how far the export got. The export never reads or changes a run's control state: it is the only writer, allowed at any time.
 */
@Slf4j
final class ExportJobImpl implements ExportJob {

    private final ExportRequest request;
    private final ExportParts parts;
    private final @Nullable ModelCalls calls;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    ExportJobImpl(final ExportRequest request, final ExportParts parts, @Nullable final ModelCalls calls) {
        this.request = Objects.requireNonNull(request, "request");
        this.parts = Objects.requireNonNull(parts, "parts");
        this.calls = calls;
    }

    @Override
    public Result<ExportReport> run() {
        try {
            log.info(
                    "export starting project={} destination={} overwrite={} sideFiles={} consistencyPass={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite(),
                    chosenSideFiles(),
                    request.consistencyPass());
            final Result<ExportReport> result =
                    isCancelledBefore("start") ? Result.err(BookExporter.cancelledBeforeWriting()) : write();
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
        final Document opened = parts.openProjects().get(project.id());
        final AppError refusal = refusal(project, opened);
        if (refusal != null) {
            return Result.err(refusal);
        }
        final Document book = Objects.requireNonNull(opened, "open book");
        if (isCancelledBefore("consistency-pass")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        return consistencyPass(project.id()).flatMap(pass -> writeStored(project, book, pass.orElse(null)));
    }

    private @Nullable AppError refusal(final Project project, @Nullable final Document opened) {
        final String targetLanguage = project.brief().targetLanguage();
        log.debug("export check=target-language value={}", targetLanguage);
        if (targetLanguage == null) {
            return refused("target-language", "No target language is chosen", "Choose the target language first.");
        }
        log.debug("export check=book-open outcome={}", opened == null ? "closed" : "open");
        if (opened == null) {
            return refused(
                    "book-open", "This project is not open", "No open project has this id; open the book again.");
        }
        if (isRefusedOccupied(request.destination())) {
            return refused(
                    "destination-exists",
                    "This destination already exists",
                    "Allow replacing it, or choose another file.");
        }
        return occupiedSideFile(project.source(), opened);
    }

    private @Nullable AppError occupiedSideFile(final Path source, final Document opened) {
        for (final SideFile sideFile : chosenSideFiles()) {
            final Path path = sideFile.pathBeside(request.destination(), opened.format());
            if (isBookItself(path, source)) {
                return refused(
                        "side-file-is-book",
                        "A side file would replace a book",
                        path.getFileName() + " is the source book or the translated book. Choose another file.");
            }
            if (isRefusedOccupied(path)) {
                return refused(
                        "side-file-exists",
                        "A side file already exists",
                        path.getFileName() + " already exists. Allow replacing it, or choose another file.");
            }
        }
        return null;
    }

    // The same alias test the book's own temporary file gets: one normalized path, the same existing file, or one link
    // target — so neither a name clash nor a symbolic link lets a side file land on a book.
    private boolean isBookItself(final Path sideFile, final Path source) {
        final Path normalized = sideFile.toAbsolutePath().normalize();
        final boolean isSource =
                ExportPathAliases.aliases(normalized, source.toAbsolutePath().normalize());
        final boolean isDestination = ExportPathAliases.aliases(
                normalized, request.destination().toAbsolutePath().normalize());
        log.debug(
                "export check=side-file-is-book path={} isSource={} isDestination={}",
                sideFile,
                isSource,
                isDestination);
        return isSource || isDestination;
    }

    private boolean isRefusedOccupied(final Path path) {
        final boolean occupied = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        log.debug(
                "export check=path-free path={} outcome={} overwrite={}",
                path,
                occupied ? "occupied" : "free",
                request.overwrite());
        return occupied && !request.overwrite();
    }

    private Result<Optional<ConsistencyReport>> consistencyPass(final String projectId) {
        log.debug(
                "export step=consistency-pass project={} requested={} withModel={}",
                projectId,
                request.consistencyPass(),
                calls != null);
        if (!request.consistencyPass()) {
            return Result.ok(Optional.empty());
        }
        return parts.consistencyPass().run(projectId, cancellableCalls()).map(Optional::of);
    }

    // A cancel raised while the pass waits on the model stops every later revision call, which ends the pass.
    private @Nullable ModelCalls cancellableCalls() {
        final ModelCalls model = calls;
        if (model == null) {
            return null;
        }
        return (kind, segmentId, chat) -> isCancelledBefore("model-call")
                ? Result.err(BookExporter.cancelledBeforeWriting())
                : model.call(kind, segmentId, chat);
    }

    private boolean isCancelledBefore(final String step) {
        final boolean isCancelled = cancelled.get();
        log.debug("export check=cancelled before={} outcome={}", step, isCancelled ? "cancelled" : "go-on");
        return isCancelled;
    }

    private Result<ExportReport> writeStored(
            final Project project, final Document opened, @Nullable final ConsistencyReport pass) {
        final Set<SegmentKind> kept = project.brief().alsoTranslate().keptKinds();
        log.debug("export step=read-records project={} keptKinds={}", project.id(), kept);
        final Result<List<SegmentRecord>> records = parts.segments().all(project.id());
        if (records.isErr()) {
            return Result.err(errorOf(records));
        }
        final List<SegmentRecord> stored = Objects.requireNonNull(records.data(), "records");
        final EffectiveTargets targets = EffectiveTargets.apply(
                opened,
                stored,
                kept,
                (segment, masked) -> parts.documents().unmask(opened.format(), segment, masked),
                Objects.requireNonNull(project.brief().targetLanguage(), "target language"));
        final Fallbacks fallbacks = new Fallbacks(opened, stored, ExportCounts.of(stored, kept, opened));
        return glossaryEntries(project.id()).flatMap(glossary -> publish(project, targets, fallbacks, glossary, pass));
    }

    private ConsistencySummary summary(@Nullable final ConsistencyReport pass) {
        if (pass == null) {
            return ConsistencySummary.NOT_RUN;
        }
        final ConsistencySummary.Status status =
                calls == null ? ConsistencySummary.Status.RAN_WITHOUT_MODEL : ConsistencySummary.Status.RAN;
        log.debug(
                "export consistency summary status={} termSubstitutions={} genderReRenders={}",
                status,
                pass.termSubstitutions(),
                pass.genderReRenders());
        return new ConsistencySummary(status, pass.termSubstitutions(), pass.genderReRenders());
    }

    private Result<List<GlossaryEntry>> glossaryEntries(final String projectId) {
        log.debug("export step=read-glossary project={}", projectId);
        return parts.glossary().all(projectId);
    }

    // The side files are built once the book is written, so the quality report counts the segments the check had to
    // write in their source.
    private Result<ExportReport> publish(
            final Project project,
            final EffectiveTargets targets,
            final Fallbacks fallbacks,
            final List<GlossaryEntry> glossary,
            @Nullable final ConsistencyReport pass) {
        final ExportPlan plan = new ExportPlan(
                project.source(),
                request.destination(),
                project.brief().sourceLanguage(),
                Objects.requireNonNull(project.brief().targetLanguage(), "target language"),
                request.overwrite());
        if (isCancelledBefore("write")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        final Result<BookExporter.Exported> written =
                new BookExporter(parts.documents(), parts.moves()).exportReporting(plan, targets, cancelled::get);
        if (written.isErr()) {
            return Result.err(errorOf(written));
        }
        return withSideFiles(
                project,
                new Written(targets, fallbacks, glossary, pass),
                Objects.requireNonNull(written.data(), "written book"));
    }

    /** What the side files and the report are built from once the book is written. */
    private record Written(
            EffectiveTargets targets,
            Fallbacks fallbacks,
            List<GlossaryEntry> glossary,
            @Nullable ConsistencyReport pass) {}

    private Result<ExportReport> withSideFiles(
            final Project project, final Written book, final BookExporter.Exported exported) {
        final Fallbacks.Fallen fallen = book.fallbacks()
                .of(
                        book.targets().sourceFallbacks(),
                        exported.sourceFallbacks(),
                        book.targets().noTarget());
        final List<SuspiciousSegment> suspicious = audit(project, book.fallbacks(), book.glossary());
        final List<SideFiles.Content> sideFiles = SideFiles.build(
                request.sideFiles(),
                new SideFiles.Sources(
                        request.destination(),
                        book.targets(),
                        book.fallbacks().stored(),
                        project.brief().alsoTranslate().keptKinds(),
                        fallen.counts(),
                        book.pass(),
                        book.glossary(),
                        suspicious));
        return SideFiles.write(sideFiles, request.overwrite(), parts.moves(), () -> isCancelledBefore("side-file"))
                .map(paths -> fallen.counts()
                        .report(exported.path(), paths, summary(book.pass()), fallen.listed(), suspicious));
    }

    // On demand, over the records as they stand now, nothing stored: the run's own audit may be older than an edit.
    private List<SuspiciousSegment> audit(
            final Project project, final Fallbacks fallbacks, final List<GlossaryEntry> glossary) {
        final FinalAudit.Book book =
                new FinalAudit.Book(project.brief(), fallbacks.opened(), glossary, WordValidator.none());
        final List<SuspiciousSegment> suspicious =
                FinalAudit.named(FinalAudit.scan(book, fallbacks.stored()), fallbacks.opened());
        log.debug("export step=audit project={} suspicious={}", project.id(), suspicious.size());
        return suspicious;
    }

    private Result<Project> findProject() {
        final Result<Optional<Project>> found = parts.projects().find(request.projectId());
        if (found.isErr()) {
            return Result.err(errorOf(found));
        }
        return Objects.requireNonNull(found.data(), "found")
                .map(Result::ok)
                .orElseGet(() -> Result.err(unknownProject(request.projectId())));
    }

    private List<SideFile> chosenSideFiles() {
        return Arrays.stream(SideFile.values())
                .filter(request.sideFiles()::contains)
                .toList();
    }

    // A failure is logged once, where its AppError was built; this is the lifecycle line of the export's end.
    private void logOutcome(final Result<ExportReport> result) {
        if (result.isErr()) {
            log.info(
                    "export ended project={} outcome=failed code={}",
                    request.projectId(),
                    errorOf(result).code());
            return;
        }
        final ExportReport report = Objects.requireNonNull(result.data(), "report");
        log.info(
                "export finished project={} destination={} written={} pending={} sourceKept={} keptVerbatim={}"
                        + " flaggedWritten={} autoAccepted={} reviewed={} verifiedSegments={} sideFiles={}"
                        + " consistency={} sourceFallbacks={}",
                request.projectId(),
                report.destination(),
                report.written(),
                report.pending(),
                report.sourceKept(),
                report.keptVerbatim(),
                report.flaggedWritten(),
                report.autoAccepted(),
                report.reviewed(),
                report.verifiedSegments(),
                report.sideFiles(),
                report.consistency(),
                report.sourceFallbacks());
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
