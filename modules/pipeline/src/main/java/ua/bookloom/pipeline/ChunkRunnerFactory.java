package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.memory.RollingSummaryKeeper;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStart;
import ua.bookloom.pipeline.run.RunSteps;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.RunWindow;

/** Wires one run's chunk runner from the tools a job holds, so the job itself names none of the steps. */
@Slf4j
final class ChunkRunnerFactory {

    private final DocumentPort documents;
    private final ObjectMapper mapper;
    private final PromptTemplates templates;
    private final RunStores stores;
    private final QualityLoop qualityLoop;
    private final SentenceSplitter splitter;
    private final int batchSize;

    ChunkRunnerFactory(final JobTools tools, final RunStores stores, final RunChecks checks, final int batchSize) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(checks, "checks");
        this.documents = tools.documents();
        this.mapper = tools.mapper();
        this.templates = tools.templates();
        this.stores = Objects.requireNonNull(stores, "stores");
        this.qualityLoop = checks.qualityLoop();
        this.splitter = checks.splitter();
        this.batchSize = batchSize;
    }

    ChunkRunner create(
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
