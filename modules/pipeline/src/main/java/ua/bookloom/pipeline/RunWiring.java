package ua.bookloom.pipeline;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.project.SegmentLocators;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.StepAnnouncer;
import ua.bookloom.pipeline.revision.ConsistencyPass;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.PrepStage;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunStartLog;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;
import ua.bookloom.pipeline.run.StageRunner;

/** Wires one run's stages and chunk runner from the tools a job holds, so the job itself names none of the steps. */
@Slf4j
final class RunWiring {

    private final RunRequest request;
    private final ChatModel model;
    private final RunStores stores;
    private final ChunkRunnerFactory chunkRunners;
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
        this.stores = Objects.requireNonNull(stores, "stores");
        this.chunkRunners = new ChunkRunnerFactory(tools, stores, checks, pacing.batchSize());
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
                chunkRunners.create(run, settings, runWindow, sinks, calls),
                revision,
                settings,
                sinks,
                calls,
                new NarratorStage(stores, sinks, run.document(), settings));
    }
}
