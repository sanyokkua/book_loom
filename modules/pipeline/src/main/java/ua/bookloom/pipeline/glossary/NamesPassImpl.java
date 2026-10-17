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
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.glossary.GlossaryModelScans.Book;
import ua.bookloom.pipeline.glossary.TermReview.Reviewed;
import ua.bookloom.pipeline.lexicon.LexiconServiceImpl;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

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

    private final GlossaryRepository glossary;
    private final LexiconRepository lexicon;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;
    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final PreScan preScan;
    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final SuggestTargets suggestTargets;
    private final LexiconReading lexiconReading;
    private final Clock clock;

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

    /** What one pass sends its calls through. */
    private record Run(Stage stage, ChatModel model, CallCounter counter, ModelCalls calls, Instant started) {}

    /** Forwards every event and counts the calls: a call sent again after a timeout is still one call. */
    private static final class CallCounter implements Consumer<JobEvent> {

        private final Consumer<JobEvent> receiver;
        private final AtomicInteger calls = new AtomicInteger();

        CallCounter(final Consumer<JobEvent> receiver) {
            this.receiver = receiver;
        }

        @Override
        public void accept(final JobEvent event) {
            if (event instanceof ModelCallStarted started && started.attempt() == 1) {
                calls.incrementAndGet();
            }
            receiver.accept(event);
        }

        int calls() {
            return calls.get();
        }
    }

    @Override
    public Result<NamesEstimate> estimate(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return guarded(
                "estimate",
                projectId,
                () -> stage(projectId)
                        .flatMap(
                                stage -> stage.glossaryService().scan(projectId).map(scanned -> estimateOf(stage))));
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
                    final CallCounter counter = new CallCounter(progress);
                    final ChatModel stoppable = new StoppableModel(model);
                    final int window = ContextBudget.windowFor(detectedContext, null);
                    final ModelCalls calls = stage.scans().calls(stoppable, counter, stage.book(), window);
                    return execute(new Run(stage, stoppable, counter, calls, clock.instant()));
                }));
    }

    private Result<Stage> stage(final String projectId) {
        return StagedGlossary.copyOf(glossary, projectId)
                .flatMap(names -> StagedLexicon.copyOf(lexicon, projectId).flatMap(terms -> {
                    final TermReview review = new TermReview(templates, mapper, names, suggestTargets);
                    final GlossaryModelScans scans = new GlossaryModelScans(
                            projects, openProjects, preScan, review, suggestTargets, lexiconReading, clock);
                    return scans.book(projectId)
                            .map(book -> new Stage(
                                    projectId,
                                    names,
                                    terms,
                                    names.entries(),
                                    terms.entries(),
                                    scans,
                                    book,
                                    new GlossaryServiceImpl(names, segments, deferrals, projects, openProjects, scans),
                                    new LexiconServiceImpl(terms, names, projects, openProjects, scans)));
                }));
    }

    // The calls of the whole pass over the open names, so an empty glossary is estimated from what its scan would hold.
    private static NamesEstimate estimateOf(final Stage stage) {
        final List<GlossaryEntry> open =
                stage.names().entries().stream().filter(TermReview::isOpen).toList();
        final NamesEstimate estimate = NamesPassEstimate.of(
                open,
                stage.book().story(),
                stage.book().dial(),
                stage.book().frame().analysisLanguage());
        log.debug(
                "Names estimate project={} calls={} minutes={}-{}",
                stage.projectId(),
                estimate.calls(),
                estimate.minutesLow(),
                estimate.minutesHigh());
        return estimate;
    }

    private Result<NamesPassReport> execute(final Run run) {
        final Stage stage = run.stage();
        log.info(
                "Names pass started project={} dial={} policy={} glossary={} terms={}",
                stage.projectId(),
                stage.book().dial(),
                stage.book().names(),
                stage.namesBefore().size(),
                stage.termsBefore().size());
        return step("scan", () -> stage.glossaryService().scan(stage.projectId()))
                .flatMap(scanned -> {
                    logEstimate(stage, scanned.added().size());
                    return step("names", () -> stage.scans().decide(stage.projectId(), stage.book(), run.calls()));
                })
                .flatMap(reviewed -> readTerms(run)
                        .flatMap(read -> translate(run, reviewed))
                        .flatMap(translated -> finish(run, reviewed)));
    }

    private static void logEstimate(final Stage stage, final int scanned) {
        final NamesEstimate estimate = estimateOf(stage);
        log.info(
                "Names pass project={} scanned={} plan: about {} calls, {}-{} min",
                stage.projectId(),
                scanned,
                estimate.calls(),
                estimate.minutesLow(),
                estimate.minutesHigh());
    }

    // The recurring terms: the held ones without a rendering are read first, so a term the model drops is not asked
    // again as a new candidate, then the book's frequent words are read.
    private Result<Boolean> readTerms(final Run run) {
        final Stage stage = run.stage();
        final String projectId = stage.projectId();
        return step("terms-scan", () -> stage.lexiconService().scan(projectId))
                .flatMap(scanned -> step(
                        "terms-review", () -> stage.lexiconService().review(projectId, run.model(), run.counter())))
                .flatMap(reviewed -> step(
                        "terms-read",
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
                        "names-translate",
                        () -> NamesTranslation.run(
                                stage.names(), stage.scans(), projectId, stage.book(), reviewed, run.calls()))
                .flatMap(locked -> step(
                        "terms-translate", () -> stage.lexiconService().suggest(projectId, run.model(), run.counter())))
                .map(suggested -> true);
    }

    // The only write: what the pass changed in the copies, applied to the stored tables in one go.
    private Result<NamesPassReport> finish(final Run run, final Reviewed reviewed) {
        final Stage stage = run.stage();
        if (Thread.currentThread().isInterrupted()) {
            log.info("Names pass project={} stopped before writing: nothing is changed", stage.projectId());
            return Result.err(StoppableModel.stopped());
        }
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
            return new NamesPassReport(names, terms, run.counter().calls(), elapsed, untranslated(stage));
        });
    }

    // An entry still without a target after the pass is the person's to give: the report lists it for the results
    // dialog, and it is counted at WARN and listed at TRACE (15j.J10), never kept silently.
    private static List<String> untranslated(final Stage stage) {
        final List<String> open = stage.names().entries().stream()
                .filter(entry -> entry.target() == null || entry.target().isBlank())
                .map(GlossaryEntry::term)
                .toList();
        if (!open.isEmpty()) {
            log.warn(
                    "Names pass project={} left {} name(s) without a target for the person",
                    stage.projectId(),
                    open.size());
            log.trace("Names pass project={} names without a target: {}", stage.projectId(), open);
        }
        return open;
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
        return new NamesPassStore(glossary, lexicon, segments, deferrals).store(projectId, names, terms);
    }

    // Stop interrupts the calling thread: a step does not start once it has, and a call in a step is refused.
    private static <T> Result<T> step(final String name, final Supplier<Result<T>> body) {
        if (Thread.currentThread().isInterrupted()) {
            log.info("Names pass stopped before step {}: nothing is changed", name);
            return Result.err(StoppableModel.stopped());
        }
        log.debug("Names pass step {}", name);
        final Result<T> result = body.get();
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
        } catch (Throwable cause) {
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
