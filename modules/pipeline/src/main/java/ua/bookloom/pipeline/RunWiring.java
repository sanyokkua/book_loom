package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.memory.RollingSummaryKeeper;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStartLog;
import ua.bookloom.pipeline.run.RunSteps;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;
import ua.bookloom.pipeline.run.StageRunner;

/** Wires one run's stages and chunk runner from the tools a job holds, so the job itself names none of the steps. */
@Slf4j
final class RunWiring {

    private final RunRequest request;
    private final ChatModel model;
    private final DocumentPort documents;
    private final ObjectMapper mapper;
    private final PromptTemplates templates;
    private final RunStores stores;
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final ConsistencyPass revision;
    private final WordValidator words;
    private final Clock clock;
    private final int batchSize;
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
        this.documents = tools.documents();
        this.mapper = tools.mapper();
        this.templates = tools.templates();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.qualityLoop = checks.qualityLoop();
        this.splitter = checks.splitter();
        this.revision = checks.revision();
        this.words = checks.words();
        this.clock = pacing.clock();
        this.batchSize = pacing.batchSize();
        this.providerKind = logFacts.providerKind();
        this.live = Objects.requireNonNull(live, "live");
    }

    StageRunner stages(final RunStart.Started run, final PrepStage.Prepared prepared, final RunSinks sinks) {
        log.debug("Wiring the stages project={} batchSize={}", request.projectId(), batchSize);
        final BookBrief brief = run.project().brief();
        final RunWindow runWindow = new RunWindow(model, request.detectedContext());
        final CallFrame frame = CallFrame.forRun(
                brief,
                prepared.styleSheet(),
                run.document(),
                run.index().majorityScript().orElse(null),
                words);
        final ModelCalls calls = new JobModelCalls(
                        onSent -> new CancellableChatModel(model, live.control(), onSent, live.watchdog()),
                        live.emit(),
                        clock,
                        frame.targetLanguage(),
                        runWindow::current,
                        SegmentLocators.of(run.document()))
                .withSteps(StepAnnouncer.over(live.emit()))
                .inRun();
        final RunSettings settings = RunSettings.forRun(request, brief, frame, runWindow.current());
        RunStartLog.write(request, providerKind, settings.window(), brief.dial(), batchSize, prepared);
        return new StageRunner(
                create(run, settings, runWindow, sinks, calls),
                revision,
                settings,
                sinks,
                calls,
                new NarratorStage(stores, sinks, run.document(), settings));
    }

    private ChunkRunner create(
            final RunStart.Started run,
            final RunSettings settings,
            final RunWindow runWindow,
            final RunSinks sinks,
            final ModelCalls calls) {
        log.debug(
                "Wiring the chunk runner batchSize={} format={}",
                batchSize,
                run.document().format());
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
}
