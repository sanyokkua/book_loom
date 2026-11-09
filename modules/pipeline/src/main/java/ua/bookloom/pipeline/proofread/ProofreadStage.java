package ua.bookloom.pipeline.proofread;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.run.NarratorStage;
import ua.bookloom.pipeline.run.RoutedCalls;
import ua.bookloom.pipeline.run.RunAudit;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunSettings;
import ua.bookloom.pipeline.run.RunSinks;
import ua.bookloom.pipeline.run.RunStores;

/**
 * The run's last stage, at every dial (design D2, D15): once the final audit has closed the Translate stage, the code
 * part sweeps renamed glossary targets through the decided text, infers an unstated narrator's gender, settles the
 * deferrals whose fact arrived and builds the mark list. At Fast it makes no model call and the list goes to the review
 * desk as it is; at Balanced and Max the marked and doubtful windows are then read with one minimal-edit call each,
 * and at Max every story paragraph again in a second round ({@link ProofreadWindowPass}); a window already read with the
 * fingerprint it has now is skipped. A stop asked for is honoured between these steps, and the segments decided so far
 * keep their decisions.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ProofreadStage {

    private final Parts parts;
    private final Windows windows;
    private final RunSettings settings;
    private final RunSinks sinks;
    private final Document document;

    /**
     * What the stage works with besides the run's settings.
     *
     * @param stores the run's stores, read for the brief, the records and the map
     * @param tools the code sweep and the deferral store
     * @param words the word validator the audit reads the text with
     * @param narrator the narrator count over the decided targets
     */
    public record Parts(RunStores stores, ProofreadTools tools, WordValidator words, NarratorStage narrator) {

        /** Rejects a missing part. */
        public Parts {
            Objects.requireNonNull(stores, "stores");
            Objects.requireNonNull(tools, "tools");
            Objects.requireNonNull(words, "words");
            Objects.requireNonNull(narrator, "narrator");
        }
    }

    /**
     * What the stage's windows are read with at Balanced and Max (design D15 "Call").
     *
     * @param call the proofread call bound to the run's frame and model
     * @param gate the document's placeholder gate, around the run's typography, every rewrite is restored through
     * @param routed the router that answers a call a pause, a stop or a provider error interrupted
     * @param planner the run's planner, whose model profile sizes a window and whose map a window reads
     * @param words the word validator the audit reads a rewrite with
     */
    public record Windows(
            ProofreadCall call, GateFunction gate, RoutedCalls routed, TranslatePlanner planner, WordValidator words) {

        /** Rejects a missing part. */
        public Windows {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(routed, "routed");
            Objects.requireNonNull(planner, "planner");
            Objects.requireNonNull(words, "words");
        }
    }

    /** What the code part did, for its end line, and what the windows read after it. */
    private record Done(
            TermSweep.Swept swept,
            DeferralSettler.Settled settled,
            List<Mark> marks,
            Stored stored,
            List<Deferral> open) {}

    /** What the code part reads once the narrator is settled. */
    private record Stored(MarkList.Book book, List<SegmentRecord> records) {}

    /**
     * Creates the stage of one run.
     *
     * @param parts the non-null collaborators
     * @param windows the non-null tools the windows are read with at Balanced and Max
     * @param settings the non-null settings of the run, whose dial and target language the stage reads
     * @param sinks the non-null pending decisions, events and boundaries of the run
     * @param document the non-null book being translated
     */
    public ProofreadStage(
            final Parts parts,
            final Windows windows,
            final RunSettings settings,
            final RunSinks sinks,
            final Document document) {
        this.parts = Objects.requireNonNull(parts, "parts");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.document = Objects.requireNonNull(document, "document");
    }

    /**
     * Ends the Translate stage with the audit and runs the Proofread stage.
     *
     * @param progress the non-null counts the stage-started event carries
     * @return completed once the stage ran, cancelled when a stop was asked for, or failed by a storage error
     */
    public RunEnd run(final Supplier<JobProgress> progress) {
        Objects.requireNonNull(progress, "progress");
        final String projectId = settings.projectId();
        final Result<Integer> flushed = sinks.pending().flush();
        if (flushed.isErr()) {
            return failed(Objects.requireNonNull(flushed.error(), ERROR));
        }
        RunAudit.run(parts.stores(), parts.words(), projectId);
        log.debug("Translation job stage change stage={} project={}", JobStage.PROOFREAD, projectId);
        sinks.emit().accept(new StageStarted(JobStage.PROOFREAD, progress.get()));
        log.info(
                "Proofread stage started project={} dial coverage={}",
                projectId,
                settings.dial().proofreadCoverage());
        return codePart(projectId, progress);
    }

    private RunEnd codePart(final String projectId, final Supplier<JobProgress> progress) {
        if (isStopped("the term sweep")) {
            return new RunEnd(JobState.CANCELLED, null);
        }
        final Result<TermSweep.Swept> swept = parts.tools()
                .sweep()
                .run(projectId, document, windows.gate(), settings.frame().targetLanguage());
        if (swept.isErr()) {
            return failed(Objects.requireNonNull(swept.error(), ERROR));
        }
        if (isStopped("the narrator count")) {
            return new RunEnd(JobState.CANCELLED, null);
        }
        final Optional<AppError> narrated = parts.narrator().settle();
        if (narrated.isPresent()) {
            return failed(narrated.get());
        }
        if (isStopped("the deferrals")) {
            return new RunEnd(JobState.CANCELLED, null);
        }
        final Result<Done> done = settleAndList(projectId, Objects.requireNonNull(swept.data(), "swept"));
        if (done.isErr()) {
            return failed(Objects.requireNonNull(done.error(), ERROR));
        }
        final Done listed = Objects.requireNonNull(done.data(), "done");
        ended(projectId, listed);
        return windowsRead(projectId, listed, progress);
    }

    // At Fast the pass reads no window; at Balanced and Max it reads the marked and doubtful ones.
    private RunEnd windowsRead(final String projectId, final Done listed, final Supplier<JobProgress> progress) {
        final ProofreadWindowPass.State state = new ProofreadWindowPass.State(
                listed.stored().book(), listed.stored().records(), listed.open(), listed.marks());
        return new ProofreadWindowPass(windows, settings, sinks, parts.stores())
                .run(state, progress)
                .orElseGet(() -> completed(projectId));
    }

    private static RunEnd completed(final String projectId) {
        log.info("Proofread stage completed project={}", projectId);
        return new RunEnd(JobState.COMPLETED, null);
    }

    private Result<Done> settleAndList(final String projectId, final TermSweep.Swept swept) {
        final String language = settings.frame().targetLanguage();
        return stored(projectId).flatMap(state -> {
            final Map<String, SegmentRecord> byId = new HashMap<>();
            state.records().forEach(record -> byId.put(record.segmentId(), record));
            final BookBrief brief = state.book().brief();
            return new DeferralSettler(parts.tools().deferrals())
                    .settle(projectId, byId, MarkList.byTerm(state.book().glossary()), brief.narrator(), language)
                    .flatMap(settled -> parts.tools()
                            .deferrals()
                            .open(projectId)
                            .map(open -> new Done(
                                    swept, settled, MarkList.of(state.book(), state.records(), open), state, open)));
        });
    }

    // Read after the sweep and the narrator count, so the list sees the swept text and the narrator as now stored.
    private Result<Stored> stored(final String projectId) {
        final RunStores stores = parts.stores();
        final Result<Optional<Project>> found = stores.projects().find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), ERROR));
        }
        final Optional<Project> project = Objects.requireNonNull(found.data(), "found");
        if (project.isEmpty()) {
            return Result.err(
                    AppError.of(ErrorCode.validation, "Nothing to proofread", "The project is no longer stored."));
        }
        final BookBrief brief = project.get().brief();
        return stores.glossary()
                .all(projectId)
                .flatMap(glossary -> stores.lexicon()
                        .all(projectId)
                        .flatMap(lexicon -> stores.segments()
                                .all(projectId)
                                .map(records ->
                                        new Stored(new MarkList.Book(brief, document, glossary, lexicon), records))));
    }

    private boolean isStopped(final String before) {
        final boolean stopped = sinks.boundaries().isStopRequested();
        if (stopped) {
            log.info("Proofread stage stopped before {} project={}", before, settings.projectId());
        }
        return stopped;
    }

    private void ended(final String projectId, final Done done) {
        final long marked = done.marks().stream()
                .filter(mark -> mark.tier() == Mark.Tier.MARKED)
                .count();
        log.info(
                "Proofread stage ended project={} dial coverage={} swept={} proposals={} deferralsResolved={} "
                        + "deferralsMismatched={} marked={} doubtful={}",
                projectId,
                settings.dial().proofreadCoverage(),
                done.swept().substitutions(),
                done.swept().proposals(),
                done.settled().resolved(),
                done.settled().mismatches(),
                marked,
                done.marks().size() - marked);
    }

    private static RunEnd failed(final AppError error) {
        log.debug("Proofread stage failed code={}", error.code());
        return new RunEnd(JobState.FAILED, error);
    }
}
