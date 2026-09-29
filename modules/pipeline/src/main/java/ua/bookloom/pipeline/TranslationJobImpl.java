package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
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
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.OutcomeRecords;
import ua.bookloom.pipeline.run.PauseDecider;
import ua.bookloom.pipeline.run.RunRecorder;
import ua.bookloom.pipeline.run.RunReports;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.WorkItem;
import ua.bookloom.pipeline.run.WorkList;

/** The single-run translation lifecycle, including pause and cancellation boundaries. */
@Slf4j
final class TranslationJobImpl implements TranslationJob {

    private final DocumentPort documents;
    private final RunRequest request;
    private final ChatModel model;
    private final ObjectMapper mapper;
    private final PromptTemplates templates;
    private final RunStores stores;
    private final Clock clock;
    private final JobControl control = new JobControl();
    private final JobSubscribers subscribers = new JobSubscribers();
    private final String jobId = UUID.randomUUID().toString();
    private final RunRecorder recorder;
    // Read and written only on the job thread: decide sets it, and the model decorator runs inside decide.
    private @Nullable String decidingSegment;
    // Written and read only on the job thread, from the claim on.
    private Instant startedAt;

    TranslationJobImpl(
            final DocumentPort documents,
            final RunRequest request,
            final ChatModel model,
            final ObjectMapper mapper,
            final PromptTemplates templates,
            final RunStores stores,
            final Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.request = Objects.requireNonNull(request, "request");
        this.model = Objects.requireNonNull(model, "model");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.recorder = new RunRecorder(stores.runs(), jobId, request.projectId(), clock);
        this.startedAt = clock.instant();
    }

    @Override
    public Result<JobReport> run() {
        log.debug("Running translation job project={} mode={}", request.projectId(), request.mode());
        if (!control.claimRun()) {
            return Result.err(TranslationJobErrors.alreadyRun());
        }
        MDC.put("job", jobId);
        try {
            return runClaimed();
        } catch (Throwable cause) {
            control.finish(JobState.FAILED);
            return Result.err(unexpectedError(cause));
        } finally {
            MDC.remove("segment");
            MDC.remove("job");
        }
    }

    @Override
    public void pause() {
        log.debug("Pause requested for translation job project={}", request.projectId());
        control.pause();
    }

    @Override
    public void resume() {
        log.debug("Resume requested for translation job project={}", request.projectId());
        control.resume();
    }

    @Override
    public void cancel() {
        log.debug("Cancellation requested for translation job project={}", request.projectId());
        control.cancel();
    }

    @Override
    public void pauseAt(final Set<PausePoint> points) {
        log.debug("Pause points requested for translation job project={} points={}", request.projectId(), points);
        control.pauseAt(points);
    }

    @Override
    public JobState state() {
        log.debug("Reading translation job state project={}", request.projectId());
        return control.state();
    }

    @Override
    public Subscription subscribe(final ua.bookloom.api.pipeline.JobListener listener) {
        log.debug("Subscribing translation job listener project={}", request.projectId());
        return subscribers.add(listener);
    }

    private Result<JobReport> runClaimed() {
        startedAt = clock.instant();
        final Result<RunStart.Started> started = RunStart.check(stores, request.projectId());
        if (started.isErr()) {
            control.finish(JobState.FAILED);
            return Result.err(errorOf(started));
        }
        final RunStart.Started run = dataOf(started);
        recorder.started();
        try {
            if (control.isCancellationRequested()) {
                return finish(JobState.CANCELLED, run, null);
            }
            final Result<WorkList> read = WorkList.read(stores, request.projectId(), run.document());
            if (read.isErr()) {
                return finish(JobState.FAILED, run, errorOf(read));
            }
            final WorkList work = dataOf(read);
            JobLifecycleLogger.started(request, run, work, control.pausePoints());
            log.debug("Translation job stage change stage={} project={}", JobStage.TRANSLATE, request.projectId());
            emit(new StageStarted(JobStage.TRANSLATE, work.currentTranslationProgress()));
            return translate(run, work);
        } catch (Throwable cause) {
            return failAtBoundary(cause, run);
        }
    }

    private Result<JobReport> translate(final RunStart.Started run, final WorkList work) {
        final SegmentTranslator translator = new SegmentTranslator(
                GateFunction.of(documents, run.document().format()),
                new CancellableChatModel(model, control, this::announceModelCall),
                run.document().format(),
                new DraftPromptBuilder(templates, callFrame(run.project().brief())),
                new DraftReplyParser(mapper));
        while (work.hasPending()) {
            final Result<JobReport> before =
                    honorBoundary(control.boundary(false, false, false), work.currentTranslationProgress(), run);
            if (before != null) {
                return before;
            }
            final Result<JobReport> result = translateOne(run, work, translator);
            if (result != null) {
                return result;
            }
        }
        return finish(JobState.COMPLETED, run, null);
    }

    private @Nullable Result<JobReport> translateOne(
            final RunStart.Started run, final WorkList work, final SegmentTranslator translator) {
        final WorkItem item = work.next();
        final Result<Decision> result = decide(item, work.draftContextFor(item), translator);
        if (result.isErr()) {
            return errorOf(result).code() == ErrorCode.cancelled
                    ? honorBoundary(control.abortedCallBoundary(), work.currentTranslationProgress(), run)
                    : recoverOrFail(errorOf(result), run, work);
        }
        final Decision decision = dataOf(result);
        final AppError storeFailure = commit(item, decision);
        if (storeFailure != null) {
            return finish(JobState.FAILED, run, storeFailure);
        }
        recorder.decided(decision.segment().status());
        final JobProgress progress = work.apply(item, decision);
        emit(new SegmentDecided(decision.segment().id(), decision.segment().status(), flagCode(decision), progress));
        return honorBoundary(control.boundary(true, work.endsSection(item), work.isComplete()), progress, run);
    }

    private @Nullable AppError commit(final WorkItem item, final Decision decision) {
        final SegmentRecord record = OutcomeRecords.decided(item.record(), decision);
        final Result<Integer> committed = stores.checkpoint()
                .commit(new ChunkCommit(record.projectId(), List.of(record), List.of(), List.of(), List.of()));
        log.debug(
                "Committed decision segmentId={} status={} ok={}",
                record.segmentId(),
                record.status(),
                committed.isOk());
        return committed.isErr() ? errorOf(committed) : null;
    }

    private @Nullable Result<JobReport> recoverOrFail(
            final AppError error, final RunStart.Started run, final WorkList work) {
        return switch (PauseDecider.route(error.code())) {
            case CANCELLED -> finish(JobState.CANCELLED, run, null);
            case PAUSE_OR_FAIL -> pauseOrFail(error, run, work);
            case FLAG_AT_ONCE, FAIL -> finish(JobState.FAILED, run, endingError(error));
        };
    }

    private @Nullable Result<JobReport> pauseOrFail(
            final AppError error, final RunStart.Started run, final WorkList work) {
        final BoundaryDecision decision = control.failureBoundary(error);
        if (decision.cancelled()) {
            return finish(JobState.CANCELLED, run, null);
        }
        if (decision.pauseReason() != null) {
            final JobProgress progress = work.currentTranslationProgress();
            JobPauseLogger.recoveryPause(error, decision.pauseReason(), progress);
            return pause(decision.pauseReason(), error, progress, run);
        }
        return finish(JobState.FAILED, run, error);
    }

    // A model error the run cannot recover from is an application fault, never a provider error that Retry now
    // could fix, so it ends the run as internal. An internal error was already logged where it was built.
    private AppError endingError(final AppError error) {
        if (error.code() == ErrorCode.internal) {
            return error;
        }
        final IllegalStateException cause = new IllegalStateException(
                "A model call answered " + error.code() + ", which a run cannot recover from");
        log.error("Translation job stopped by a model error it cannot recover from code={}", error.code(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
                null,
                cause);
    }

    private @Nullable Result<JobReport> honorBoundary(
            final BoundaryDecision decision, final JobProgress progress, final RunStart.Started run) {
        if (decision.cancelled()) {
            return finish(JobState.CANCELLED, run, null);
        }
        return decision.pauseReason() == null ? null : pause(decision.pauseReason(), null, progress, run);
    }

    private @Nullable Result<JobReport> pause(
            final PauseReason reason,
            @Nullable final AppError error,
            final JobProgress progress,
            final RunStart.Started run) {
        recorder.paused();
        emit(new Paused(reason, error, progress));
        if (control.awaitPause() == PauseWait.CANCELLED) {
            return finish(JobState.CANCELLED, run, null);
        }
        recorder.resumed();
        emit(new Resumed(progress));
        return null;
    }

    private Result<Decision> decide(
            final WorkItem item, final DraftContext context, final SegmentTranslator translator) {
        MDC.put("segment", item.segment().id());
        decidingSegment = item.segment().id();
        try {
            return translator.translate(item.segment(), context);
        } finally {
            decidingSegment = null;
            MDC.remove("segment");
        }
    }

    private void announceModelCall() {
        emit(new ModelCallStarted(Objects.requireNonNull(decidingSegment, "segment being decided")));
    }

    private Result<JobReport> finish(final JobState end, final RunStart.Started run, @Nullable final AppError error) {
        final JobReport report =
                RunReports.of(stores, run.project().id(), run.document().format(), end, error);
        recorder.ended(report.end());
        control.finish(report.end());
        if (report.end() == JobState.FAILED) {
            log.warn(
                    "Translation job ended failed code={}",
                    Objects.requireNonNull(report.error(), "error").code());
        }
        emit(new Finished(report));
        JobLifecycleLogger.ended(
                report, Duration.between(startedAt, clock.instant()).toMillis());
        return Result.ok(report);
    }

    private Result<JobReport> failAtBoundary(final Throwable cause, final RunStart.Started run) {
        return finish(JobState.FAILED, run, unexpectedError(cause));
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

    private AppError unexpectedError(final Throwable cause) {
        log.error("Unexpected translation job boundary failure project={}", request.projectId(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
                null,
                cause);
    }

    private static CallFrame callFrame(final BookBrief brief) {
        return new CallFrame(
                brief.sourceLanguage(),
                Objects.requireNonNull(brief.targetLanguage(), "target language checked at the start"),
                StyleSheet.from(BookBrief.defaults(null)),
                ForeignPassagePolicy.KEEP);
    }

    private static <T> T dataOf(final Result<T> result) {
        return Objects.requireNonNull(result.data(), "result data");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "result error");
    }
}
