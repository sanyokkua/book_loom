package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.memory.RollingSummaryKeeper;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.MemoryEvents;
import ua.bookloom.pipeline.run.PendingCommit;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunRecorder;
import ua.bookloom.pipeline.run.RunReports;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunSteps;
import ua.bookloom.pipeline.run.RunStores;
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
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final Clock clock;
    private final JobControl control;
    private final JobSubscribers subscribers = new JobSubscribers();
    private final String jobId = UUID.randomUUID().toString();
    private final RunRecorder recorder;
    // Used from the job thread only.
    private final PendingCommit pending;
    // Written and read only on the job thread, from the claim on.
    private Instant startedAt;

    TranslationJobImpl(
            final DocumentPort documents,
            final RunRequest request,
            final ChatModel model,
            final ObjectMapper mapper,
            final PromptTemplates templates,
            final RunStores stores,
            final QualityLoop qualityLoop,
            final SentenceSplitter splitter,
            final Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.request = Objects.requireNonNull(request, "request");
        this.model = Objects.requireNonNull(model, "model");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.qualityLoop = Objects.requireNonNull(qualityLoop, "qualityLoop");
        this.splitter = Objects.requireNonNull(splitter, "splitter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.control = new JobControl(request.mode().pausePoints());
        this.recorder = new RunRecorder(stores.runs(), jobId, request.projectId(), clock);
        this.pending = new PendingCommit(stores.checkpoint(), request.projectId());
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
        // Silent: a screen polls it as often as it draws.
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
            return translate(run, work);
        } catch (Throwable cause) {
            return failAtBoundary(cause, run);
        }
    }

    private Result<JobReport> translate(final RunStart.Started run, final WorkList work) {
        log.debug("Translation job stage change stage={} project={}", JobStage.PREP, request.projectId());
        emit(new StageStarted(JobStage.PREP, work.preparationProgress()));
        final Result<PrepStage.Prepared> prepared = PrepStage.prepare(
                stores.glossary(), request.projectId(), run.project().brief(), run.document());
        if (prepared.isErr()) {
            return finish(JobState.FAILED, run, errorOf(prepared));
        }
        final int proposed = dataOf(prepared).proposed();
        if (proposed > 0) {
            emit(MemoryEvents.namesAdded(proposed));
        }
        log.debug("Translation job stage change stage={} project={}", JobStage.TRANSLATE, request.projectId());
        emit(new StageStarted(JobStage.TRANSLATE, work.currentTranslationProgress()));
        final RunEnd end = runner(run, dataOf(prepared).styleSheet()).run(work);
        return finish(end.state(), run, end.error());
    }

    private ChunkRunner runner(final RunStart.Started run, final StyleSheet styleSheet) {
        final BookBrief brief = run.project().brief();
        final CallFrame frame = new CallFrame(
                brief.sourceLanguage(),
                Objects.requireNonNull(brief.targetLanguage(), "target language checked at the start"),
                styleSheet,
                brief.foreignPassages());
        final GateFunction gate = GateFunction.of(documents, run.document().format());
        final ModelCalls calls =
                new JobModelCalls(onSent -> new CancellableChatModel(model, control, onSent), this::emit);
        final SegmentTranslator translator = new SegmentTranslator(
                gate,
                calls,
                run.document().format(),
                new DraftPromptBuilder(templates, frame),
                new DraftReplyParser(mapper));
        final DialParameters dial = DialParameters.of(brief.dial());
        final RollingSummaryKeeper summary =
                new RollingSummaryKeeper(stores.summaries(), stores.glossary(), templates, mapper, frame, dial, calls);
        return new ChunkRunner(
                new RunSteps(translator, qualityLoop, gate, calls, splitter, summary),
                new RunSettings(request.projectId(), request.mode(), dial, frame, brief.names()),
                stores,
                new RunSinks(pending, recorder, this::emit, boundaries()),
                SegmentLocators.of(run.document()));
    }

    private JobBoundaries boundaries() {
        return new JobBoundaries(control, pending, recorder, this::emit, stores.segments(), request.projectId());
    }

    private Result<JobReport> finish(final JobState end, final RunStart.Started run, @Nullable final AppError error) {
        final Result<Integer> flushed = pending.flush();
        final AppError unsaved = flushed.isErr() ? errorOf(flushed) : null;
        if (unsaved != null && end != JobState.FAILED) {
            return finish(JobState.FAILED, run, unsaved);
        }
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

    private AppError unexpectedError(final Throwable cause) {
        log.error("Unexpected translation job boundary failure project={}", request.projectId(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
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
