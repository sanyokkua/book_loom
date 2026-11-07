package ua.bookloom.pipeline;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.proofread.ProofreadCall;
import ua.bookloom.pipeline.proofread.ProofreadStage;
import ua.bookloom.pipeline.proofread.ProofreadTools;
import ua.bookloom.pipeline.proofread.ProofreadWindows;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RoutedCalls;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStartLog;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;
import ua.bookloom.pipeline.run.StageRunner;
import ua.bookloom.pipeline.run.SummariesSwitch;
import ua.bookloom.pipeline.typography.TypographyGate;

/** Wires one run's stages and chunk runner from the tools a job holds, so the job itself names none of the steps. */
@Slf4j
final class RunWiring {

    private final RunRequest request;
    private final ChatModel model;
    private final JobTools tools;
    private final RunStores stores;
    private final ChunkRunnerFactory chunkRunners;
    private final PromptTemplates templates;
    private final ProofreadTools proofread;
    private final WordValidator words;
    private final Clock clock;
    private final int pacedBatchSize;
    private final String providerKind;
    private final Live live;

    /** The parts of a running job the wiring hands to the calls and stages it builds. */
    record Live(JobControl control, StallWatchdog watchdog, Consumer<JobEvent> emit) {}

    RunWiring(
            final RunRequest request,
            final JobTools tools,
            final RunStores stores,
            final RunChecks checks,
            final RunPacing pacing,
            final RunLogFacts logFacts,
            final Live live) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(checks, "checks");
        this.request = Objects.requireNonNull(request, "request");
        this.model = tools.model();
        this.tools = tools;
        this.stores = Objects.requireNonNull(stores, "stores");
        this.chunkRunners = new ChunkRunnerFactory(tools, stores, checks);
        this.templates = tools.templates();
        this.proofread = checks.proofread();
        this.words = checks.words();
        this.clock = pacing.clock();
        this.pacedBatchSize = pacing.batchSize();
        this.providerKind = logFacts.providerKind();
        this.live = Objects.requireNonNull(live, "live");
    }

    /**
     * Wires the run's stages. The run's fixed frame is built here, once, from the brief, the book and the map at Start,
     * so every translate-family call of the run sends the same system bytes.
     *
     * @return the stages, or the frame's error, which fails the run before its first chunk
     */
    Result<StageRunner> stages(final RunStart.Started run, final PrepStage.Prepared prepared, final RunSinks sinks) {
        return frameOf(run, run.project().brief()).map(runFrame -> wired(run, prepared, sinks, runFrame));
    }

    private StageRunner wired(
            final RunStart.Started run,
            final PrepStage.Prepared prepared,
            final RunSinks sinks,
            final RunFrame runFrame) {
        final int batchSize = startBatchSize();
        log.debug("Wiring the stages project={} batchSize={}", request.projectId(), batchSize);
        final BookBrief brief = run.project().brief();
        final RunWindow runWindow = new RunWindow(model, request.detectedContext());
        final CallFrame frame = CallFrame.forRun(
                brief,
                prepared.styleSheet(),
                run.document(),
                run.index().majorityScript().orElse(null),
                words);
        final ModelCalls calls = callsOf(run, runWindow, frame);
        final RunSettings settings = RunSettings.forRun(request, brief, frame, runWindow.current());
        RunStartLog.write(request, providerKind, settings.window(), brief.dial(), batchSize, prepared);
        final TranslatePlanner planner = new TranslatePlanner(
                new ContextBuilder(templates),
                runFrame,
                model.profile(),
                runWindow::current,
                run.document().units(),
                () -> stores.summaries().list(request.projectId()),
                new SummariesSwitch(stores, request.projectId(), brief.summariesOn()));
        forecast(run, settings, sinks);
        final ChunkRunner chunks = chunkRunners.create(run, settings, runWindow, sinks, calls, batchSize, planner);
        return stagesOf(run, settings, sinks, chunks, windowsOf(run, settings, sinks, calls, planner));
    }

    // Fast's Proofread stage reads no window, so it is forecast nothing; the window takes a run with no forecast as one
    // whose Proofread makes no model call.
    private void forecast(final RunStart.Started run, final RunSettings settings, final RunSinks sinks) {
        final ProofreadCoverage coverage = settings.dial().proofreadCoverage();
        if (coverage == ProofreadCoverage.NONE) {
            log.debug("No proofread forecast: the dial's coverage is {}", coverage);
            return;
        }
        sinks.emit()
                .accept(ProofreadWindows.forecast(
                        run.document(), coverage, model.profile().batchSize()));
    }

    // The proofread windows go to the run's own model through the run's one call seam (design D15 "Call").
    private ProofreadStage.Windows windowsOf(
            final RunStart.Started run,
            final RunSettings settings,
            final RunSinks sinks,
            final ModelCalls calls,
            final TranslatePlanner planner) {
        final GateFunction gate = TypographyGate.around(
                GateFunction.of(tools.documents(), run.document().format()),
                settings.frame().targetLanguage(),
                settings.frame().typography());
        return new ProofreadStage.Windows(
                new ProofreadCall(planner, calls, tools.mapper()),
                gate,
                new RoutedCalls(sinks.boundaries()),
                planner,
                words);
    }

    // The narrator count serves both the Proofread stage and a run stopped during Translate.
    private StageRunner stagesOf(
            final RunStart.Started run,
            final RunSettings settings,
            final RunSinks sinks,
            final ChunkRunner chunks,
            final ProofreadStage.Windows windows) {
        final NarratorStage narrator = new NarratorStage(stores, sinks, run.document(), settings);
        final ProofreadStage proofreadStage = new ProofreadStage(
                new ProofreadStage.Parts(stores, proofread, words, narrator), windows, settings, sinks, run.document());
        return new StageRunner(chunks, settings, sinks, narrator, proofreadStage);
    }

    private ModelCalls callsOf(final RunStart.Started run, final RunWindow runWindow, final CallFrame frame) {
        return new JobModelCalls(
                        onSent -> new CancellableChatModel(model, live.control(), onSent, live.watchdog()),
                        live.emit(),
                        clock,
                        frame.targetLanguage(),
                        runWindow::current,
                        SegmentLocators.of(run.document()))
                .withSteps(StepAnnouncer.over(live.emit()))
                .inRun();
    }

    // The frame reads the map as it stands at Start; a later glossary change reaches the map of the next chunk.
    private Result<RunFrame> frameOf(final RunStart.Started run, final BookBrief brief) {
        return new RunFrames(templates, new BookRules(templates.languageRules()))
                .buildStored(
                        new RunFrames.FrameStores(stores.glossary(), stores.lexicon(), stores.promptFrames()),
                        request.projectId(),
                        brief,
                        run.document(),
                        run.index(),
                        run.chapters(),
                        model.profile());
    }

    private int startBatchSize() {
        final int size =
                pacedBatchSize == RunPacing.FROM_PROFILE ? model.profile().batchSize() : pacedBatchSize;
        log.debug(
                "Batch size starts at {} ({})",
                size,
                pacedBatchSize == RunPacing.FROM_PROFILE ? "model profile" : "set by the pacing");
        return size;
    }
}
