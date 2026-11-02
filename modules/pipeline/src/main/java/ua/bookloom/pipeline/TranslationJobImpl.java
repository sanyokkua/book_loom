package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.Subscription;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.lexicon.Lexicon;
import ua.bookloom.pipeline.memory.RollingSummaryKeeper;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.MemoryEvents;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.PendingCommit;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RunAudit;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunRecorder;
import ua.bookloom.pipeline.run.RunReports;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStartLog;
import ua.bookloom.pipeline.run.RunSteps;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;
import ua.bookloom.pipeline.run.StageRunner;
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
    private final ConsistencyPass revision;
    private final Clock clock;
    private final JobControl control;
    private final JobSubscribers subscribers = new JobSubscribers();
    // Used from the job thread only, like every event it is handed.
    private final RunSummaryLogger runSummary;
    private final AtomicBoolean skipRequested = new AtomicBoolean();
    private final String jobId;
    private final RunRecorder recorder;
    // Used from the job thread only.
    private final PendingCommit pending;
    // Written and read only on the job thread, from the claim on.
    private Instant startedAt;
    private final UnattendedRecovery recovery;
    private final StallWatchdog watchdog;
    private final RunTicks ticks;
    private final int batchSize;
    private final WordValidator words;
    private final String providerKind;

    /**
     * Creates a job paced by {@code pacing}, for a scripted clock; its start line names the provider kind {@code
     * logFacts} holds, the kind of server the model runs on or {@code unknown}.
     */
    TranslationJobImpl(
            final RunRequest request,
            final JobTools tools,
            final RunStores stores,
            final RunChecks checks,
            final RunPacing pacing,
            final RunLogFacts logFacts) {
        this.providerKind = logFacts.providerKind();
        this.jobId = pacing.idSource().newId();
        this.words = checks.words();
        this.batchSize = pacing.batchSize();
        this.documents = tools.documents();
        this.request = Objects.requireNonNull(request, "request");
        this.model = tools.model();
        this.mapper = tools.mapper();
        this.templates = tools.templates();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.qualityLoop = checks.qualityLoop();
        this.splitter = checks.splitter();
        this.revision = checks.revision();
        this.clock = pacing.clock();
        this.control = new JobControl(request.mode().pausePoints());
        this.runSummary = new RunSummaryLogger(clock, logFacts.ledger());
        this.recorder = new RunRecorder(stores.runs(), jobId, request.projectId(), clock, request.model());
        this.pending = new PendingCommit(stores.checkpoint(), request.projectId(), recorder);
        this.startedAt = clock.instant();
        this.recovery = new UnattendedRecovery(control, clock, pacing.timer(), this::emit);
        this.watchdog = new StallWatchdog(control, clock);
        this.ticks = pacing.ticks();
    }

    @Override
    public Result<JobReport> run() {
        log.debug("Running translation job project={} mode={}", request.projectId(), request.mode());
        if (!control.claimRun()) {
            return Result.err(TranslationJobErrors.alreadyRun());
        }
        MDC.put("job", jobId);
        Runnable stopWatchdog = () -> {};
        try {
            stopWatchdog = watchdog.start(ticks);
            return runClaimed();
        } catch (Exception cause) {
            control.finish(JobState.FAILED);
            return Result.err(unexpectedError(cause));
        } finally {
            stopWatchdog.run();
            MDC.remove("segment");
            MDC.remove("job");
        }
    }

    @Override
    public void pause() {
        log.info("Pause requested for translation job project={}", request.projectId());
        control.pause();
    }

    @Override
    public void resume() {
        log.info("Resume requested for translation job project={}", request.projectId());
        control.resume();
    }

    @Override
    public void skipSegment() {
        final boolean paused = control.state() == JobState.PAUSED;
        log.info("Skip requested for translation job project={} paused={}", request.projectId(), paused);
        if (paused) {
            skipRequested.set(true);
            control.resume();
        }
    }

    @Override
    public void recoverWith(final ProviderProbe probe, final Duration maxOutage) {
        log.debug("Recovery probe set for translation job project={} maxOutage={}", request.projectId(), maxOutage);
        recovery.probeWith(probe, maxOutage);
    }

    @Override
    public void cancel() {
        log.info("Stop requested for translation job project={}", request.projectId());
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
        } catch (Exception cause) {
            return finish(JobState.FAILED, run, unexpectedError(cause));
        }
    }

    private Result<JobReport> translate(final RunStart.Started run, final WorkList work) {
        log.debug("Translation job stage change stage={} project={}", JobStage.PREP, request.projectId());
        emit(new StageStarted(JobStage.PREP, work.preparationProgress()));
        final Result<PrepStage.Prepared> prepared = PrepStage.prepare(
                stores.glossary(),
                stores.lexicon(),
                request.projectId(),
                run.project().brief(),
                run.document(),
                run.index());
        if (prepared.isErr()) {
            return finish(JobState.FAILED, run, errorOf(prepared));
        }
        final int proposed = dataOf(prepared).proposed();
        if (proposed > 0) {
            emit(MemoryEvents.namesAdded(proposed));
        }
        announceProfile();
        final RunEnd end = stages(run, dataOf(prepared)).run(work);
        new Lexicon(stores.lexicon()).logConsistency(request.projectId());
        return finish(end.state(), run, end.error());
    }

    private StageRunner stages(final RunStart.Started run, final PrepStage.Prepared prepared) {
        final StyleSheet styleSheet = prepared.styleSheet();
        final BookBrief brief = run.project().brief();
        final RunWindow runWindow = new RunWindow(model, request.detectedContext());
        final CallFrame frame = CallFrame.forRun(
                brief, styleSheet, run.document(), run.index().majorityScript().orElse(null), words);
        final ModelCalls calls = new JobModelCalls(
                        onSent -> new CancellableChatModel(model, control, onSent, watchdog),
                        this::emit,
                        clock,
                        frame.targetLanguage(),
                        runWindow::current,
                        SegmentLocators.of(run.document()))
                .withSteps(StepAnnouncer.over(this::emit))
                .inRun();
        final RunSettings settings = RunSettings.forRun(request, brief, frame, runWindow.current());
        RunStartLog.write(request, providerKind, settings.window(), brief.dial(), batchSize, prepared);
        final RunSinks sinks = new RunSinks(pending, recorder, this::emit, boundaries());
        return new StageRunner(
                chunkRunner(run, settings, runWindow, sinks, calls),
                revision,
                settings,
                sinks,
                calls,
                new NarratorStage(stores, sinks, run.document(), settings));
    }

    // The model's first profile ask is the run's start: it writes the profile line beside the start line.
    private void announceProfile() {
        final ModelProfile profile = model.profile();
        log.debug("Run model profile batch={} tier={}", profile.batchSize(), profile.tier());
    }

    private ChunkRunner chunkRunner(
            final RunStart.Started run,
            final RunSettings settings,
            final RunWindow runWindow,
            final RunSinks sinks,
            final ModelCalls calls) {
        final GateFunction gate = GateFunction.of(documents, run.document().format());
        final SegmentTranslator translator = new SegmentTranslator(
                gate,
                calls,
                run.document().format(),
                new DraftPromptBuilder(templates, settings.frame()),
                new DraftReplyParser(mapper));
        final RollingSummaryKeeper summary = new RollingSummaryKeeper(
                stores.summaries(), stores.glossary(), templates, mapper, settings.frame(), settings.dial(), calls);
        final BatchDrafter batch = new BatchDrafter(
                new BatchPromptBuilder(templates, settings.frame()),
                new BatchReplyParser(mapper),
                calls,
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage(),
                batchSize);
        return new ChunkRunner(
                new RunSteps(translator, qualityLoop, gate, calls, splitter, summary, batch),
                settings,
                runWindow,
                stores,
                sinks,
                run.index(),
                run.document());
    }

    private JobBoundaries boundaries() {
        return new JobBoundaries(
                control,
                pending,
                recorder,
                this::emit,
                stores.segments(),
                request.projectId(),
                new JobBoundaries.PauseHandling(skipRequested, recovery));
    }

    private Result<JobReport> finish(final JobState end, final RunStart.Started run, @Nullable final AppError error) {
        final Result<Integer> flushed = pending.flush();
        final AppError unsaved = flushed.isErr() ? errorOf(flushed) : null;
        if (unsaved != null && end != JobState.FAILED) {
            return finish(JobState.FAILED, run, unsaved);
        }
        RunAudit.after(end, stores, words, run.project().id());
        final JobReport report =
                RunReports.of(stores, run.project().id(), run.document().format(), end, error);
        recorder.ended(report.end());
        control.finish(report.end());
        emit(new Finished(report));
        JobLifecycleLogger.ended(
                report, Duration.between(startedAt, clock.instant()).toMillis());
        return Result.ok(report);
    }

    private void emit(final JobEvent event) {
        log.debug("Sending translation job event type={}", event.getClass().getSimpleName());
        runSummary.onEvent(event);
        watchdog.onEvent(event);
        subscribers.deliver(event);
    }

    private AppError unexpectedError(final Throwable cause) {
        log.error("Unexpected translation job boundary failure project={}", request.projectId(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected error stopped the run outside any segment, so it could not be retried. The segments"
                        + " decided so far are kept, and the log names the error.",
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
