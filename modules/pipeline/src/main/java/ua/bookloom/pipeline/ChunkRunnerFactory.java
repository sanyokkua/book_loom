package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunSteps;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;

/** Wires one run's chunk runner from the tools a job holds. */
@Slf4j
final class ChunkRunnerFactory {

    private final DocumentPort documents;
    private final ObjectMapper mapper;
    private final RunStores stores;
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;

    ChunkRunnerFactory(final JobTools tools, final RunStores stores, final RunChecks checks) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(checks, "checks");
        this.documents = tools.documents();
        this.mapper = tools.mapper();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.qualityLoop = checks.qualityLoop();
        this.splitter = checks.splitter();
    }

    ChunkRunner create(
            final RunStart.Started run,
            final RunSettings settings,
            final RunWindow runWindow,
            final RunSinks sinks,
            final ModelCalls calls,
            final int batchSize,
            final TranslatePlanner planner) {
        log.debug(
                "Wiring the chunk runner batchSize={} format={}",
                batchSize,
                run.document().format());
        final GateFunction gate = GateFunction.of(documents, run.document().format());
        final SegmentTranslator translator =
                new SegmentTranslator(gate, calls, run.document().format(), planner, mapper);
        final BatchDrafter batch = new BatchDrafter(
                new BatchPromptBuilder(planner),
                new BatchReplyParser(mapper),
                calls,
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage(),
                batchSize);
        return new ChunkRunner(
                new RunSteps(translator, qualityLoop, gate, calls, splitter, batch, planner),
                settings,
                runWindow,
                stores,
                sinks,
                run.index(),
                run.document());
    }
}
