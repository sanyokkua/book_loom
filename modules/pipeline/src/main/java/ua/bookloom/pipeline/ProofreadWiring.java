package ua.bookloom.pipeline;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.proofread.ProofreadCall;
import ua.bookloom.pipeline.proofread.ProofreadStage;
import ua.bookloom.pipeline.proofread.ProofreadTools;
import ua.bookloom.pipeline.proofread.ProofreadWindows;
import ua.bookloom.pipeline.run.ChunkRunner;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.RoutedCalls;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStores;
import ua.bookloom.pipeline.run.StageRunner;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * Wires the Proofread stage of one run and the stage runner that ends with it, kept apart from {@link RunWiring} so
 * the run's Translate wiring and its Proofread wiring each name only their own parts.
 */
@Slf4j
final class ProofreadWiring {

    private final JobTools tools;
    private final RunStores stores;
    private final ProofreadTools proofread;
    private final WordValidator words;

    ProofreadWiring(
            final JobTools tools, final RunStores stores, final ProofreadTools proofread, final WordValidator words) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.proofread = Objects.requireNonNull(proofread, "proofread");
        this.words = Objects.requireNonNull(words, "words");
    }

    // Fast's Proofread stage reads no window, so it is forecast nothing; the window takes a run with no forecast as one
    // whose Proofread makes no model call.
    void forecast(
            final Document document, final RunSettings settings, final RunSinks sinks, final ModelProfile profile) {
        final ProofreadCoverage coverage = settings.dial().proofreadCoverage();
        if (coverage == ProofreadCoverage.NONE) {
            log.debug("No proofread forecast: the dial's coverage is {}", coverage);
            return;
        }
        sinks.emit().accept(ProofreadWindows.forecast(document, coverage, profile.batchSize()));
    }

    /**
     * Builds the stage runner: the chunks, then the Proofread stage, whose windows go to the run's own model through
     * the run's one call seam (design D15 "Call"). The narrator count serves both the Proofread stage and a run
     * stopped during Translate.
     */
    StageRunner stages(
            final Document document,
            final RunSettings settings,
            final RunSinks sinks,
            final ChunkRunner chunks,
            final ModelCalls calls,
            final TranslatePlanner planner) {
        log.debug("Wiring the Proofread stage coverage={}", settings.dial().proofreadCoverage());
        final GateFunction gate = TypographyGate.around(
                GateFunction.of(tools.documents(), document.format()),
                settings.frame().targetLanguage(),
                settings.frame().typography());
        final ProofreadStage.Windows windows = new ProofreadStage.Windows(
                new ProofreadCall(planner, calls, tools.mapper()),
                gate,
                new RoutedCalls(sinks.boundaries()),
                planner,
                words);
        final NarratorStage narrator = new NarratorStage(stores, sinks, document, settings);
        final ProofreadStage proofreadStage = new ProofreadStage(
                new ProofreadStage.Parts(stores, proofread, words, narrator), windows, settings, sinks, document);
        return new StageRunner(chunks, settings, sinks, narrator, proofreadStage);
    }
}
