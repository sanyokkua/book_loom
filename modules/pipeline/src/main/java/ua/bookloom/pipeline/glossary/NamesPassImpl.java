package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.pipeline.NamesPassStage;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.PrepSummary;
import ua.bookloom.pipeline.context.ModelWindows;
import ua.bookloom.pipeline.glossary.GlossaryModelScans.Book;
import ua.bookloom.pipeline.glossary.TermReview.Reviewed;
import ua.bookloom.pipeline.lexicon.LexiconServiceImpl;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.run.RunStores;

/**
 * The one autonomous names pass (15i.N8): the steps the Names &amp; style buttons run, in order, over a private copy of
 * both tables — scan an empty glossary, read and decide the names, read the recurring terms, then translate both and
 * lock by rule — and only then one write to the stored tables. The buttons' own services run the steps, built over the
 * copies instead of the stored repositories, so a step reads what the step before it decided and no step can reach
 * the stored tables. A Stop (the calling thread interrupted), a cancelled reply or a failed call ends the pass with
 * nothing written. The person's entries are untouched because every step already leaves them alone: a locked entry,
 * a target the person typed, a gender the person chose, a term with the person's rendering and a removal the person
 * made.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class NamesPassImpl implements NamesPass {

    private final RunStores stores;
    private final DeferralRepository deferrals;
    private final NameServices services;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CallLedger ledger;

    /**
     * A names pass whose calls are counted nowhere (tests).
     */
    public NamesPassImpl(
            final RunStores stores,
            final DeferralRepository deferrals,
            final NameServices services,
            final PromptTemplates templates,
            final ObjectMapper mapper,
            final Clock clock) {
        this(stores, deferrals, services, templates, mapper, clock, CallLedger.none());
    }

    /** The copies of both tables and the services built over them, for one project. */
    private record Stage(
            String projectId,
            StagedGlossary names,
            StagedLexicon terms,
            List<GlossaryEntry> namesBefore,
            List<LexiconEntry> termsBefore,
            GlossaryModelScans scans,
            Book book,
            GlossaryServiceImpl glossaryService,
            LexiconServiceImpl lexiconService) {}

    /** What one pass calls through, the ledger mark its calls count from, and its refused renderings' count. */
    private record Run(
            Stage stage,
            ChatModel model,
            NamesPassSteps counter,
            ModelCalls calls,
            Instant started,
            long mark,
            AtomicInteger rejected) {}

    @Override
    public Result<NamesEstimate> estimate(final String projectId, final Map<CallKind, MeasuredSpeed> stored) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(stored, "stored");
        return guarded(
                "estimate",
                projectId,
                () -> stage(projectId)
                        .flatMap(stage ->
                                stage.glossaryService().scan(projectId).map(scanned -> estimateOf(stage, stored))));
    }

    @Override
    public Result<NamesPassReport> run(
            final String projectId,
            final ChatModel model,
            @Nullable final Integer detectedContext,
            final Consumer<JobEvent> progress) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(progress, "progress");
        return guarded(
                "run",
                projectId,
                () -> stage(projectId).flatMap(stage -> {
                    final NamesPassSteps counter = new NamesPassSteps(progress);
                    final ChatModel stoppable = new StoppableModel(model);
                    final int window = ModelWindows.of(model, detectedContext);
                    log.debug("Names pass window={} detected={}", window, detectedContext);
                    final ModelCalls calls = stage.scans().calls(stoppable, counter, stage.book(), window);
                    final Run run = new Run(
                            stage, stoppable, counter, calls, clock.instant(), ledger.mark(), new AtomicInteger());
                    final Result<NamesPassReport> result = execute(run);
                    PrepSummary.log("names", projectId, ledger.since(run.mark()));
                    return result;
                }));
    }

    private Result<Stage> stage(final String projectId) {
        return StagedGlossary.copyOf(stores.glossary(), projectId)
                .flatMap(names -> StagedLexicon.copyOf(stores.lexicon(), projectId)
                        .flatMap(terms -> stageOver(projectId, names, terms)));
    }

    private Result<Stage> stageOver(final String projectId, final StagedGlossary names, final StagedLexicon terms) {
        final TermReview review = new TermReview(templates, mapper, names, services.suggestTargets());
        final GlossaryModelScans scans = new GlossaryModelScans(
                stores.projects(),
                stores.openProjects(),
                services.preScan(),
                review,
                services.suggestTargets(),
                services.lexiconReading(),
                clock);
        return scans.book(projectId)
                .map(book -> new Stage(
                        projectId,
                        names,
                        terms,
                        names.entries(),
                        terms.entries(),
                        scans,
                        book,
                        new GlossaryServiceImpl(
                                names,
                                stores.segments(),
                                deferrals,
                                stores.projects(),
                                stores.openProjects(),
                                scans,
                                stores.summaries()),
                        new LexiconServiceImpl(terms, names, stores.projects(), stores.openProjects(), scans)));
    }

    // The calls of the whole pass over the open names, so an empty glossary is estimated from what its scan would hold.
    private static NamesEstimate estimateOf(final Stage stage, final Map<CallKind, MeasuredSpeed> stored) {
        final List<GlossaryEntry> open =
                stage.names().entries().stream().filter(TermReview::isOpen).toList();
        final NamesEstimate estimate = NamesPassEstimate.of(
                open,
                stage.book().story(),
                stage.book().dial(),
                stage.book().frame().analysisLanguage(),
                stored);
        log.debug("Names estimate project={} {}", stage.projectId(), estimate);
        return estimate;
    }

    private Result<NamesPassReport> execute(final Run run) {
        final Stage stage = run.stage();
        logStarted(stage);
        run.counter().planStages();
        return step(
                        run,
                        NamesPassStage.SCAN.code(),
                        () -> stage.glossaryService().scan(stage.projectId()))
                .flatMap(scanned -> {
                    run.counter()
                            .planRequests(logEstimate(stage, scanned.added().size()));
                    return step(
                            run,
                            NamesPassStage.NAMES.code(),
                            () -> stage.scans().decide(stage.projectId(), stage.book(), run.calls()));
                })
                .flatMap(reviewed -> step(run, NamesPassStage.NAMES_KNOWLEDGE.code(), () -> knowledge(run))
                        .map(known -> reviewed))
                .flatMap(reviewed -> readTerms(run)
                        .flatMap(read -> translate(run, reviewed))
                        .flatMap(translated -> step(
                                run,
                                NamesPassStage.CHECK.code(),
                                () -> Result.ok(SuggestionCheck.of(
                                        stage.projectId(),
                                        stage.book(),
                                        stage.names().entries()))))
                        .flatMap(checked -> finish(run, reviewed)));
    }

    private static void logStarted(final Stage stage) {
        log.info(
                "Names pass started project={} dial={} policy={} glossary={} terms={}",
                stage.projectId(),
                stage.book().dial(),
                stage.book().names(),
                stage.namesBefore().size(),
                stage.termsBefore().size());
    }

    private static int logEstimate(final Stage stage, final int scanned) {
        final NamesEstimate estimate = estimateOf(stage, Map.of());
        log.info(
                "Names pass project={} scanned={} plan: about {} calls, {}-{} min",
                stage.projectId(),
                scanned,
                estimate.calls(),
                estimate.minutesLow(),
                estimate.minutesHigh());
        return estimate.calls();
    }

    // The bare names nobody settled, asked after the readings so the book's and the rules' values are in; the
    // answers go to the staged copy as likely values (design D5).
    private Result<Integer> knowledge(final Run run) {
        final Stage stage = run.stage();
        return new NameKnowledgeCalls(templates, mapper)
                .applied(stage.names().entries(), stage.book().frame(), run.calls())
                .flatMap(applied -> stage.names().putAll(stage.projectId(), applied))
                .map(List::size);
    }

    // The recurring terms: the held ones without a rendering are read first, so a term the model drops is not asked
    // again as a new candidate, then the book's frequent words are read.
    private Result<Boolean> readTerms(final Run run) {
        final Stage stage = run.stage();
        final String projectId = stage.projectId();
        return step(
                        run,
                        NamesPassStage.TERMS_SCAN.code(),
                        () -> stage.lexiconService().scan(projectId))
                .flatMap(scanned -> step(
                        run,
                        NamesPassStage.TERMS_REVIEW.code(),
                        () -> stage.lexiconService().review(projectId, run.model(), run.counter())))
                .flatMap(reviewed -> step(
                        run,
                        NamesPassStage.TERMS_READ.code(),
                        () -> stage.lexiconService().scanWithModel(projectId, run.model(), run.counter())))
                .map(read -> {
                    log.info(
                            "Names pass project={} terms now {} (was {})",
                            projectId,
                            stage.terms().entries().size(),
                            stage.termsBefore().size());
                    return true;
                });
    }

    private Result<Boolean> translate(final Run run, final Reviewed reviewed) {
        final Stage stage = run.stage();
        final String projectId = stage.projectId();
        return step(
                        run,
                        NamesPassStage.NAMES_TRANSLATE.code(),
                        () -> NamesTranslation.run(
                                stage.names(),
                                stage.scans(),
                                projectId,
                                stage.book(),
                                reviewed,
                                run.calls(),
                                run.rejected()::addAndGet))
                .flatMap(locked -> step(
                        run,
                        NamesPassStage.TERMS_TRANSLATE.code(),
                        () -> stage.lexiconService().suggest(projectId, run.model(), run.counter())))
                .map(suggested -> true);
    }

    // The only write: what the pass changed in the copies, applied to the stored tables in one go.
    private Result<NamesPassReport> finish(final Run run, final Reviewed reviewed) {
        return step(run, NamesPassStage.WRITE.code(), () -> write(run, reviewed));
    }

    private Result<NamesPassReport> write(final Run run, final Reviewed reviewed) {
        final Stage stage = run.stage();
        final Map<String, String> reasons = reasonsOf(reviewed.report().changes());
        final EntryChanges<GlossaryEntry> names = EntryChanges.between(
                stage.namesBefore(), stage.names().entries(), GlossaryEntry::id, entry -> reasons.get(entry.id()));
        final EntryChanges<LexiconEntry> terms = EntryChanges.between(
                stage.termsBefore(), stage.terms().entries(), entry -> LexiconEntry.keyOf(entry.term()), entry -> null);
        return store(stage.projectId(), names, terms).map(stored -> {
            final Duration elapsed = Duration.between(run.started(), clock.instant());
            log.info(
                    "Names pass finished project={} names added={} removed={} changed={} terms added={} removed={} changed={} calls={} elapsed={}",
                    stage.projectId(),
                    names.added().size(),
                    names.removed().size(),
                    names.changed().size(),
                    terms.added().size(),
                    terms.removed().size(),
                    terms.changed().size(),
                    run.counter().calls(),
                    elapsed);
            NamesPassCounts.write(stage.names().entries(), run.rejected().get());
            return new NamesPassReport(
                    names,
                    terms,
                    run.counter().calls(),
                    elapsed,
                    NamesPassCounts.untranslated(
                            stage.projectId(), stage.names().entries()));
        });
    }

    private static Map<String, String> reasonsOf(final EntryChanges<GlossaryEntry> changes) {
        final Map<String, String> reasons = new HashMap<>();
        changes.removed().stream()
                .filter(removal -> removal.reason() != null)
                .forEach(removal -> reasons.put(removal.entry().id(), removal.reason()));
        changes.changed().stream()
                .filter(change -> change.reason() != null)
                .forEach(change -> reasons.put(change.before().id(), change.reason()));
        return reasons;
    }

    private Result<Boolean> store(
            final String projectId, final EntryChanges<GlossaryEntry> names, final EntryChanges<LexiconEntry> terms) {
        return new NamesPassStore(
                        stores.glossary(),
                        stores.lexicon(),
                        stores.segments(),
                        deferrals,
                        stores.openProjects(),
                        TargetText.targetLanguageOf(stores.projects(), projectId))
                .store(projectId, names, terms);
    }

    // Stop interrupts the calling thread: a step does not start once it has, and a call in a step is refused.
    private static <T> Result<T> step(final Run run, final String name, final Supplier<Result<T>> body) {
        if (Thread.currentThread().isInterrupted()) {
            log.info("Names pass stopped before step {}: nothing is changed", name);
            return Result.err(StoppableModel.stopped());
        }
        log.debug("Names pass step {}", name);
        final Result<T> result = run.counter().stage(name, body);
        if (result.isErr()) {
            log.warn(
                    "Names pass step {} ended with {}: nothing is changed",
                    name,
                    Objects.requireNonNull(result.error(), "error").code());
        }
        return result;
    }

    private static <T> Result<T> guarded(final String action, final String projectId, final Supplier<Result<T>> body) {
        log.debug("Names pass {} project={}", action, projectId);
        try {
            return body.get();
        } catch (Exception cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Names pass failed", "The names could not be prepared.", null, cause);
            log.error(
                    "Unexpected names pass failure action={} project={} code={}",
                    action,
                    projectId,
                    error.code(),
                    cause);
            return Result.err(error);
        }
    }
}
