package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;

/** The single-run translation lifecycle, including pause and cancellation boundaries. */
@Slf4j
final class TranslationJobImpl implements TranslationJob {

    private final DocumentPort documents;
    private final TranslationRequest request;
    private final ChatModel model;
    private final ObjectMapper mapper;
    private final ExportMoveOperation moves;
    private final JobControl control = new JobControl();
    private final JobSubscribers subscribers = new JobSubscribers();
    private final String jobId = UUID.randomUUID().toString();
    // Read and written only on the job thread: decide sets it, and the model decorator runs inside decide.
    private @Nullable String decidingSegment;

    TranslationJobImpl(
            final DocumentPort documents,
            final TranslationRequest request,
            final ChatModel model,
            final ObjectMapper mapper) {
        this(documents, request, model, mapper, ExportMoveOperation.nio());
    }

    TranslationJobImpl(
            final DocumentPort documents,
            final TranslationRequest request,
            final ChatModel model,
            final ObjectMapper mapper,
            final ExportMoveOperation moves) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.request = Objects.requireNonNull(request, "request");
        this.model = Objects.requireNonNull(model, "model");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.moves = Objects.requireNonNull(moves, "moves");
    }

    @Override
    public Result<JobReport> run() {
        log.debug("Running translation job source={} destination={}", request.source(), request.destination());
        if (!control.claimRun()) {
            return Result.err(TranslationJobErrors.alreadyRun());
        }
        MDC.put("job", jobId);
        try {
            return runClaimed();
        } catch (Throwable cause) {
            return failAtBoundary(cause, null);
        } finally {
            MDC.remove("segment");
            MDC.remove("job");
        }
    }

    @Override
    public void pause() {
        log.debug("Pause requested for translation job source={}", request.source());
        control.pause();
    }

    @Override
    public void resume() {
        log.debug("Resume requested for translation job source={}", request.source());
        control.resume();
    }

    @Override
    public void cancel() {
        log.debug("Cancellation requested for translation job source={}", request.source());
        control.cancel();
    }

    @Override
    public void pauseAt(final Set<PausePoint> points) {
        log.debug("Pause points requested for translation job source={} points={}", request.source(), points);
        control.pauseAt(points);
    }

    @Override
    public JobState state() {
        log.debug("Reading translation job state source={}", request.source());
        return control.state();
    }

    @Override
    public Subscription subscribe(final ua.bookloom.api.pipeline.JobListener listener) {
        log.debug("Subscribing translation job listener source={}", request.source());
        return subscribers.add(listener);
    }

    private Result<JobReport> runClaimed() {
        if (control.isCancellationRequested()) {
            return finish(JobState.CANCELLED, null, null, null);
        }
        final Result<InitialSnapshot> snapshot = openSnapshot();
        if (snapshot.isErr()) {
            control.finish(JobState.FAILED);
            return Result.err(errorOf(snapshot));
        }
        final InitialSnapshot initial = dataOf(snapshot);
        final JobProgressTracker tracker = new JobProgressTracker(initial.document());
        try {
            JobLifecycleLogger.started(request, tracker, control.pausePoints());
            emit(new StageStarted(JobStage.TRANSLATE, tracker.currentTranslationProgress()));
            return initial.releaseError() == null
                    ? translate(tracker)
                    : finish(JobState.FAILED, tracker, null, initial.releaseError());
        } catch (Throwable cause) {
            return failAtBoundary(cause, tracker);
        }
    }

    private Result<JobReport> translate(final JobProgressTracker tracker) {
        final SegmentTranslator translator = new SegmentTranslator(
                documents,
                new CancellableChatModel(model, control, this::announceModelCall),
                tracker.format(),
                new DraftPromptBuilder(
                        TranslationJobRequestContext.sourceLanguage(request, tracker.declaredLanguage()),
                        request.targetLanguage()),
                new DraftReplyParser(mapper));
        while (tracker.hasPending()) {
            final Result<JobReport> before =
                    honorBoundary(control.boundary(false, false, false), tracker.currentTranslationProgress(), tracker);
            if (before != null) {
                return before;
            }
            final Result<JobReport> result = translateOne(tracker, translator);
            if (result != null) {
                return result;
            }
        }
        return beginExport(tracker);
    }

    private @Nullable Result<JobReport> translateOne(
            final JobProgressTracker tracker, final SegmentTranslator translator) {
        final SegmentWork work = tracker.next();
        final Result<Decision> result = decide(work, tracker.draftContextFor(work), translator);
        if (result.isErr()) {
            return errorOf(result).code() == ErrorCode.cancelled
                    ? honorBoundary(control.abortedCallBoundary(), tracker.currentTranslationProgress(), tracker)
                    : recoverOrFail(errorOf(result), tracker, work);
        }
        final Decision decision = dataOf(result);
        final JobProgress progress = tracker.apply(work, decision);
        emit(new SegmentDecided(decision.segment().id(), decision.segment().status(), flagCode(decision), progress));
        return honorBoundary(
                control.boundary(true, tracker.endsSection(work), tracker.isComplete()), progress, tracker);
    }

    private Result<JobReport> beginExport(final JobProgressTracker tracker) {
        final Result<JobReport> boundary =
                honorBoundary(control.boundary(false, false, false), tracker.exportProgress(), tracker);
        if (boundary != null) {
            return boundary;
        }
        control.markExportStarted();
        emit(new StageStarted(JobStage.EXPORT, tracker.exportProgress()));
        return export(tracker);
    }

    private Result<JobReport> export(final JobProgressTracker tracker) {
        final BookExporter exporter = new BookExporter(documents, moves);
        while (true) {
            final Result<JobReport> attempt = exportAttempt(exporter, tracker);
            if (attempt != null) {
                return attempt;
            }
        }
    }

    private @Nullable Result<JobReport> exportAttempt(final BookExporter exporter, final JobProgressTracker tracker) {
        final Result<Path> result =
                exporter.export(request, tracker.decidedDocument(), control::isCancellationRequested);
        if (result.isOk()) {
            return finish(JobState.COMPLETED, tracker, dataOf(result), null);
        }
        final AppError error = errorOf(result);
        return error.code() == ErrorCode.cancelled
                ? finish(JobState.CANCELLED, tracker, null, null)
                : recoverOrFail(error, tracker, null);
    }

    private @Nullable Result<JobReport> recoverOrFail(
            final AppError error, final JobProgressTracker tracker, @Nullable final SegmentWork failedWork) {
        if (error.code() == ErrorCode.cancelled) {
            return finish(JobState.CANCELLED, tracker, null, null);
        }
        final BoundaryDecision decision = control.failureBoundary(error);
        if (decision.cancelled()) {
            return finish(JobState.CANCELLED, tracker, null, null);
        }
        if (decision.pauseReason() != null) {
            final JobProgress progress =
                    failedWork == null ? tracker.exportProgress() : tracker.currentTranslationProgress();
            JobPauseLogger.recoveryPause(error, decision.pauseReason(), progress);
            return pause(decision.pauseReason(), error, progress, tracker);
        }
        return finish(JobState.FAILED, tracker, null, error);
    }

    private @Nullable Result<JobReport> honorBoundary(
            final BoundaryDecision decision, final JobProgress progress, final JobProgressTracker tracker) {
        if (decision.cancelled()) {
            return finish(JobState.CANCELLED, tracker, null, null);
        }
        return decision.pauseReason() == null ? null : pause(decision.pauseReason(), null, progress, tracker);
    }

    private @Nullable Result<JobReport> pause(
            final PauseReason reason,
            @Nullable final AppError error,
            final JobProgress progress,
            final JobProgressTracker tracker) {
        emit(new Paused(reason, error, progress));
        if (control.awaitPause() == PauseWait.CANCELLED) {
            return finish(JobState.CANCELLED, tracker, null, null);
        }
        emit(new Resumed(progress));
        return null;
    }

    private Result<Decision> decide(
            final SegmentWork work,
            final ua.bookloom.pipeline.prompt.DraftContext context,
            final SegmentTranslator translator) {
        MDC.put("segment", work.segment().id());
        decidingSegment = work.segment().id();
        try {
            return translator.translate(work.segment(), context);
        } finally {
            decidingSegment = null;
            MDC.remove("segment");
        }
    }

    private void announceModelCall() {
        emit(new ModelCallStarted(Objects.requireNonNull(decidingSegment, "segment being decided")));
    }

    private Result<InitialSnapshot> openSnapshot() {
        final Result<Boolean> destination = checkDestination();
        if (destination.isErr()) {
            return Result.err(errorOf(destination));
        }
        try {
            final Result<Document> opened = documents.open(request.source());
            return opened.isErr() ? Result.err(errorOf(opened)) : snapshotAfterClose(dataOf(opened));
        } catch (Throwable cause) {
            return Result.err(snapshotError(cause));
        }
    }

    private Result<Boolean> checkDestination() {
        try {
            if (!request.overwrite() && Files.exists(request.destination())) {
                log.warn("Refusing translation job because destination already exists: {}", request.destination());
                return Result.err(TranslationJobErrors.destinationExists());
            }
            return Result.ok(Boolean.TRUE);
        } catch (Throwable cause) {
            return Result.err(snapshotError(cause));
        }
    }

    private Result<InitialSnapshot> snapshotAfterClose(final Document snapshot) {
        try {
            final Result<Boolean> closed = documents.close(snapshot);
            return Result.ok(new InitialSnapshot(snapshot, closed.isErr() ? errorOf(closed) : null));
        } catch (Throwable cause) {
            return Result.ok(new InitialSnapshot(snapshot, snapshotError(cause)));
        }
    }

    private Result<JobReport> finish(
            final JobState end,
            @Nullable final JobProgressTracker tracker,
            @Nullable final Path written,
            @Nullable final AppError error) {
        control.finish(end);
        final JobReport report = report(end, tracker, written, error);
        if (end == JobState.FAILED) {
            log.warn(
                    "Translation job ended failed code={}",
                    Objects.requireNonNull(error, "error").code());
        }
        emit(new Finished(report));
        JobLifecycleLogger.ended(report);
        return Result.ok(report);
    }

    private JobReport report(
            final JobState end,
            @Nullable final JobProgressTracker tracker,
            @Nullable final Path written,
            @Nullable final AppError error) {
        return tracker == null
                ? new JobReport(
                        TranslationJobRequestContext.sourceFormat(request),
                        end,
                        0,
                        0,
                        0,
                        java.util.List.of(),
                        written,
                        error)
                : tracker.report(end, written, error);
    }

    private Result<JobReport> failAtBoundary(final Throwable cause, @Nullable final JobProgressTracker tracker) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
                null,
                cause);
        log.error("Unexpected translation job boundary failure source={}", request.source(), cause);
        return finish(JobState.FAILED, tracker, null, error);
    }

    private void emit(final JobEvent event) {
        log.debug("Sending translation job event type={}", event.getClass().getSimpleName());
        subscribers.deliver(event);
    }

    private @Nullable ErrorCode flagCode(final Decision decision) {
        return decision.segment().status() == SegmentStatus.FLAGGED
                ? Objects.requireNonNull(decision.flagReason(), "flag reason").code()
                : null;
    }

    private static AppError snapshotError(final Throwable cause) {
        log.error("Unexpected failure while preparing source snapshot", cause);
        return AppError.of(
                ErrorCode.internal,
                "This book could not be opened",
                "The source book could not be prepared for translation.",
                null,
                cause);
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
