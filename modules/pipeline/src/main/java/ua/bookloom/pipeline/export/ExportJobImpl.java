package ua.bookloom.pipeline.export;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.CallKindTotals;
import ua.bookloom.pipeline.PrepSummary;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.checks.WordValidator;

/**
 * Writes what a project stores: each segment record's effective target laid over a fresh read of the source by
 * {@link BookExporter}, so the file holds the best text every segment has today, then the chosen side files beside it.
 * An occupied path, or a side file that would land on the source or the book, is refused before anything slow. No
 * model is called and no record is changed: the proofread summary is counted from the stored verdicts, findings and
 * deferrals before the book is written. A cancel is honoured at each step — before the stores are read, before the
 * book is written and before each side file — so a cancel never leaves a partial file, and its message says how far
 * the export got. The export never reads or changes a run's control state: it is the only writer, allowed at any time.
 */
@Slf4j
final class ExportJobImpl implements ExportJob {

    private final ExportRequest request;
    private final ExportParts parts;
    private final ExportProgressListener progress;
    private final CallLedger ledger;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    // What the finished line names; the report carries the older consistency summary until the screens read this.
    private final AtomicReference<ProofreadSummary> proofread = new AtomicReference<>(ProofreadSummary.NONE);

    ExportJobImpl(
            final ExportRequest request,
            final ExportParts parts,
            final ExportProgressListener progress,
            final CallLedger ledger) {
        this.request = Objects.requireNonNull(request, "request");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.parts = Objects.requireNonNull(parts, "parts");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    @Override
    public Result<ExportReport> run() {
        final long mark = ledger.mark();
        try {
            log.info(
                    "export starting project={} destination={} overwrite={} sideFiles={}",
                    request.projectId(),
                    request.destination(),
                    request.overwrite(),
                    chosenSideFiles());
            final Result<ExportReport> result =
                    isCancelledBefore("start") ? Result.err(BookExporter.cancelledBeforeWriting()) : write();
            logOutcome(result);
            return result;
        } catch (Exception cause) {
            return Result.err(internalError("run the export", cause));
        } finally {
            PrepSummary.log("export", request.projectId(), CallKindTotals.of(ledger.since(mark)));
        }
    }

    @Override
    public void cancel() {
        log.debug("export cancel requested project={}", request.projectId());
        cancelled.set(true);
    }

    @Override
    public void writeNow() {
        log.debug("export write-now ignored project={}: the export makes no model call", request.projectId());
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
        if (isCancelledBefore("read-stores")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        return ProofreadInputs.read(parts, project, book).flatMap(inputs -> writeStored(project, book, inputs));
    }

    private boolean isCancelledBefore(final String step) {
        final boolean isCancelled = cancelled.get();
        log.debug("export check=cancelled before={} outcome={}", step, isCancelled ? "cancelled" : "go-on");
        return isCancelled;
    }

    private Result<ExportReport> writeStored(
            final Project project, final Document opened, final ProofreadInputs inputs) {
        final Set<SegmentKind> kept = project.brief().alsoTranslate().keptKinds();
        log.debug("export step=read-records project={} keptKinds={}", project.id(), kept);
        final List<SegmentRecord> stored = inputs.records();
        final ProofreadSummary summary = ProofreadSummaries.of(inputs.book(), stored, inputs.open());
        proofread.set(summary);
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
        return publish(project, targets, fallbacks, inputs.book().glossary(), summary);
    }

    // The side files are built once the book is written, so the quality report counts the segments the check had to
    // write in their source.
    private Result<ExportReport> publish(
            final Project project,
            final EffectiveTargets targets,
            final Fallbacks fallbacks,
            final List<GlossaryEntry> glossary,
            final ProofreadSummary summary) {
        final ExportPlan plan = new ExportPlan(
                project.source(),
                request.destination(),
                project.brief().sourceLanguage(),
                Objects.requireNonNull(project.brief().targetLanguage(), "target language"),
                request.overwrite());
        if (isCancelledBefore("write")) {
            return Result.err(BookExporter.cancelledBeforeWriting());
        }
        progress.onProgress(new ExportProgress(ExportProgress.Step.WRITING, 0, 1));
        final Result<BookExporter.Exported> written =
                new BookExporter(parts.documents(), parts.moves()).exportReporting(plan, targets, cancelled::get);
        if (written.isErr()) {
            return Result.err(errorOf(written));
        }
        return withSideFiles(
                project,
                new ExportFinish.Written(targets, fallbacks, glossary, summary, parts.lastRun(project.id())),
                Objects.requireNonNull(written.data(), "written book"));
    }

    private Result<ExportReport> withSideFiles(
            final Project project, final ExportFinish.Written book, final BookExporter.Exported exported) {
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
                                    book.summary(),
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
        final ProofreadSummary summary = Objects.requireNonNull(proofread.get(), "proofread");
        log.info(
                "export finished project={} destination={} written={} pending={} sourceKept={} keptVerbatim={}"
                        + " flaggedWritten={} autoAccepted={} reviewed={} verifiedSegments={} sideFiles={}"
                        + " proofreadReRead={} proofreadWaiting={} sourceFallbacks={}",
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
                summary.reRead(),
                summary.waiting(),
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
