package ua.bookloom.pipeline;

import java.time.Clock;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStartLog;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;
import ua.bookloom.pipeline.run.StageRunner;
import ua.bookloom.pipeline.run.SummariesSwitch;

/** Wires one run's stages and chunk runner from the tools a job holds, so the job itself names none of the steps. */
@Slf4j
final class RunWiring {

    private final RunRequest request;
    private final ChatModel model;
    private final RunStores stores;
    private final ChunkRunnerFactory chunkRunners;
    private final PromptTemplates templates;
    private final ProofreadWiring proofreadWiring;
    private final WordValidator words;
    private final Clock clock;
    private final int pacedBatchSize;
    private final String providerKind;
    private final RunLive live;

    RunWiring(
            final RunRequest request,
            final JobTools tools,
            final RunStores stores,
            final RunChecks checks,
            final RunPacing pacing,
            final RunLogFacts logFacts,
            final RunLive live) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(checks, "checks");
        this.request = Objects.requireNonNull(request, "request");
        this.model = tools.model();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.chunkRunners = new ChunkRunnerFactory(tools, stores, checks);
        this.templates = tools.templates();
        this.words = checks.words();
        this.proofreadWiring = new ProofreadWiring(tools, this.stores, checks.proofread(), words);
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
        final ModelCalls calls = RunModelCalls.of(model, live, clock, run.document(), runWindow, frame);
        final RunSettings settings =
                RunSettings.forRun(request, brief, frame, runWindow.current(), templates.variants());
        RunStartLog.write(request, providerKind, settings.window(), brief.dial(), batchSize, prepared);
        final TranslatePlanner planner = new TranslatePlanner(
                new ContextBuilder(templates),
                runFrame,
                model.profile(),
                runWindow::current,
                run.document().units(),
                () -> stores.summaries().list(request.projectId()),
                new SummariesSwitch(stores, request.projectId(), brief.summariesOn()));
        proofreadWiring.forecast(run.document(), settings, sinks, model.profile());
        final ChunkRunner chunks = chunkRunners.create(run, settings, runWindow, sinks, calls, batchSize, planner);
        return proofreadWiring.stages(run.document(), settings, sinks, chunks, calls, planner);
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
