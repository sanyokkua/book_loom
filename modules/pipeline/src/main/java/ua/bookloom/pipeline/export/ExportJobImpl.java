package ua.bookloom.pipeline.export;

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
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.revision.ConsistencyReport;
import ua.bookloom.pipeline.revision.PassOptions;

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
    private final @Nullable ExportCalls calls;
    private final ExportProgressListener progress;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean stopEarly = new AtomicBoolean();
    private final AtomicBoolean writing = new AtomicBoolean();

    ExportJobImpl(
            final ExportRequest request,
            final ExportParts parts,
            @Nullable final ModelCalls calls,
            final ExportProgressListener progress) {
        this.request = Objects.requireNonNull(request, "request");
        this.parts = Objects.requireNonNull(parts, "parts");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.calls = calls == null ? null : new ExportCalls(calls, cancelled, stopEarly, progress);
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
        final ExportCalls inFlight = calls;
        if (inFlight != null) {
            inFlight.interrupt();
        }
    }

    @Override
    public void writeNow() {
        final ExportCalls inFlight = calls;
        if (inFlight == null || writing.get()) {
            log.warn(
                    "export write-now ignored project={} pass={} writing={}",
                    request.projectId(),
                    inFlight != null,
                    writing.get());
            return;
        }
        log.info("export write now requested project={}", request.projectId());
        stopEarly.set(true);
        inFlight.interrupt();
    }

    private Result<ExportReport> write() {
        progress.onProgress(new ExportProgress(ExportProgress.Step.VALIDATING, 0, 1));
        final Result<Project> found = findProject();
        if (found.isErr()) {
            return Result.err(errorOf(found));
        }
        final Project project = Objects.requireNonNull(found.data(), "project");
        final Document opened = parts.openProjects().get(project.id());
        final AppError refusal = new ExportPreflight(request, chosenSideFiles()).refusal(project, opened);
        if (refusal != null) {
            return Result.err(refusal);
        }
        final Document book = Objects.requireNonNull(opened, "open book");
        if (isCancelledBefore("consistency-pass")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        return consistencyPass(project)
                .flatMap(pass -> summaryOf(project, pass.orElse(null))
                        .flatMap(summary -> writeStored(project, book, pass.orElse(null), summary)));
    }

    private Result<Optional<ConsistencyReport>> consistencyPass(final Project project) {
        final boolean everySegment =
                switch (request.consistencyScope()) {
                    case BY_DIAL -> project.brief().dial() == QualityDial.MAX;
                    case DOUBTED -> false;
                    case EVERY_SEGMENT -> true;
                };
        log.debug(
                "export step=consistency-pass project={} requested={} withModel={} scope={} dial={} everySegment={}",
                project.id(),
                request.consistencyPass(),
                calls != null,
                request.consistencyScope(),
                project.brief().dial(),
                everySegment);
        if (!request.consistencyPass()) {
            return Result.ok(Optional.empty());
        }
        return parts.consistencyPass()
                .run(project.id(), calls, new PassOptions(true, everySegment))
                .map(Optional::of);
    }

    private boolean isCancelledBefore(final String step) {
        final boolean isCancelled = cancelled.get();
        log.debug("export check=cancelled before={} outcome={}", step, isCancelled ? "cancelled" : "go-on");
        return isCancelled;
    }

    private Result<ExportReport> writeStored(
            final Project project,
            final Document opened,
            @Nullable final ConsistencyReport pass,
            final ConsistencySummary summary) {
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
                project.brief().sourceLanguage(),
                Objects.requireNonNull(project.brief().targetLanguage(), "target language"));
        final Fallbacks fallbacks = new Fallbacks(
                opened,
                stored,
                ExportCounts.of(stored, kept, opened)
                        .withRefusedReplies(targets.candidates().size()));
        return glossaryEntries(project.id())
                .flatMap(glossary -> publish(project, targets, fallbacks, glossary, new Passed(pass, summary)));
    }

    private Result<ConsistencySummary> summaryOf(final Project project, @Nullable final ConsistencyReport pass) {
        if (pass != null) {
            return Result.ok(ExportSummaries.ran(pass, calls != null));
        }
        return parts.consistencyPass().waiting(project.id()).map(ExportSummaries::notRun);
    }

    private Result<List<GlossaryEntry>> glossaryEntries(final String projectId) {
        log.debug("export step=read-glossary project={}", projectId);
        return parts.glossary().all(projectId);
    }

    /** The pass's own report, or null when it was off, and what the export says of it. */
    private record Passed(@Nullable ConsistencyReport report, ConsistencySummary summary) {}

    // The side files are built once the book is written, so the quality report counts the segments the check had to
    // write in their source.
    private Result<ExportReport> publish(
            final Project project,
            final EffectiveTargets targets,
            final Fallbacks fallbacks,
            final List<GlossaryEntry> glossary,
            final Passed passed) {
        final ExportPlan plan = new ExportPlan(
                project.source(),
                request.destination(),
                project.brief().sourceLanguage(),
                Objects.requireNonNull(project.brief().targetLanguage(), "target language"),
                request.overwrite());
        if (isCancelledBefore("write")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        writing.set(true);
        progress.onProgress(new ExportProgress(ExportProgress.Step.WRITING, 0, 1));
        final Result<BookExporter.Exported> written =
                new BookExporter(parts.documents(), parts.moves()).exportReporting(plan, targets, cancelled::get);
        if (written.isErr()) {
            return Result.err(errorOf(written));
        }
        return withSideFiles(
                project,
                new ExportFinish.Written(targets, fallbacks, glossary, passed.report(), parts.lastRun(project.id())),
                Objects.requireNonNull(written.data(), "written book"),
                passed.summary());
    }

    private Result<ExportReport> withSideFiles(
            final Project project,
            final ExportFinish.Written book,
            final BookExporter.Exported exported,
            final ConsistencySummary summary) {
        final Fallbacks.Fallen fallen = book.fallbacks()
                .of(
                        book.targets().sourceFallbacks(),
                        exported.sourceFallbacks(),
                        book.targets().noTarget());
        final List<SuspiciousSegment> suspicious = audit(project, book.fallbacks(), book.glossary());
        final ExportFinish.Built built = ExportFinish.build(project, request, book, fallen, suspicious);
        return SideFiles.write(
                        built.contents(),
                        request.overwrite() || built.isReportForced(),
                        parts.moves(),
                        () -> isCancelledBefore("side-file"))
                .map(paths -> {
                    progress.onProgress(new ExportProgress(ExportProgress.Step.WRITING, 1, 1));
                    return fallen.counts()
                            .report(
                                    exported.path(),
                                    paths,
                                    summary,
                                    fallen.listed(),
                                    suspicious,
                                    built.policy().unresolvedLocators());
                });
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
