package ua.bookloom.pipeline.glossary;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.project.OpenProjects;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.RequestDefaultsSource;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * The glossary's two model actions, the name scan and the review, sharing what both need: the open book, a brief
 * with a target language and a name policy for the suggested targets, and the run's model-call seam, so each call is announced, timed and attempt-counted exactly
 * as a run's calls are. A cancelled call (the calling thread interrupted) ends the action with nothing changed.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class GlossaryModelScans {

    private final ProjectRepository projects;
    private final OpenProjects openProjects;
    private final PreScan preScan;
    private final TermReview termReview;
    private final SuggestTargets suggestTargets;
    private final LexiconReading lexiconReading;
    private final Clock clock;
    private final RequestDefaultsSource requestDefaultsSource;

    /** Scans whose requests use the defaults the environment alone gives (the command line, tests). */
    public GlossaryModelScans(
            final ProjectRepository projects,
            final OpenProjects openProjects,
            final PreScan preScan,
            final TermReview termReview,
            final SuggestTargets suggestTargets,
            final LexiconReading lexiconReading,
            final Clock clock) {
        this(
                projects,
                openProjects,
                preScan,
                termReview,
                suggestTargets,
                lexiconReading,
                clock,
                RequestDefaultsSource.environmentOnly());
    }

    /** What a model action of the glossary runs on. */
    record Book(BookSlice story, CallFrame frame, NamePolicy names, QualityDial dial) {}

    Result<EntryChanges<GlossaryEntry>> prescan(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> isGatedOut(projectId, book)
                        ? Result.ok(EntryChanges.none())
                        : preScan.scan(
                                projectId, book.story(), book.frame(), book.names(), calls(model, progress, book)));
    }

    private static boolean isGatedOut(final String projectId, final Book book) {
        final int proposals = FrequencyScan.candidates(
                        book.story().index(),
                        book.story().ordinals(),
                        FrequencyScan.PROPOSAL_MIN_COUNT,
                        book.frame().analysisLanguage())
                .size();
        final boolean isWanted = PreScanGate.isWanted(book.dial(), proposals);
        if (!isWanted) {
            log.info(
                    "Model pre-scan project={} not run: dial {} and the code already proposes {} names (the model scan runs at Max or under {})",
                    projectId,
                    book.dial(),
                    proposals,
                    PreScanGate.MIN_PROPOSALS);
        }
        return !isWanted;
    }

    Result<GlossaryReviewReport> review(
            final String projectId, final ChatModel model, final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> termReview.review(
                        projectId,
                        book.story(),
                        book.frame(),
                        book.names(),
                        book.dial(),
                        calls(model, progress, book)));
    }

    /**
     * Asks the model for a rendering of each entry by the call the glossary's suggestions use; nothing is written.
     *
     * @param projectId the non-null project whose book gives each term its example sentence
     * @param entries the non-null terms to render, as glossary entries of type term
     * @param model the non-null model to call
     * @param progress the non-null receiver of each call's start and finish
     * @return the entries with the suggested targets written in, or the first failed call's error
     */
    public Result<List<GlossaryEntry>> suggestOnto(
            final String projectId,
            final List<GlossaryEntry> entries,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> suggestTargets.suggestOnto(
                        entries, book.story(), book.frame(), book.names(), calls(model, progress, book)));
    }

    /**
     * Reads the recurring-term candidates in context at the project's dial (15i.N7); nothing is written.
     *
     * @param projectId the non-null project whose book gives each term its windows
     * @param terms the non-null candidate terms
     * @param model the non-null model to call
     * @param progress the non-null receiver of each call's start and finish
     * @return a decision for every term asked, by lexicon key, or the first failed call's error
     */
    public Result<Map<String, LexiconReading.Decision>> readTerms(
            final String projectId,
            final List<String> terms,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        return book(projectId)
                .flatMap(book -> lexiconReading.read(
                        terms, book.story(), book.dial(), book.frame(), calls(model, progress, book)));
    }

    /**
     * Reads the candidates of a lexicon model scan at the project's dial ({@link LexiconReading#scan}).
     *
     * @param projectId the project; never null
     * @param terms the candidate words; never null
     * @param model the model to ask; never null
     * @param progress where each call is announced; never null
     * @return a decision for every candidate, or the book's or the first failed call's error
     */
    public Result<Map<String, LexiconReading.Decision>> scanTerms(
            final String projectId,
            final List<String> terms,
            final ChatModel model,
            final Consumer<JobEvent> progress) {
        log.debug("Lexicon model scan reading project={} candidates={}", projectId, terms.size());
        return book(projectId)
                .flatMap(book -> lexiconReading.scan(
                        terms, book.story(), book.dial(), book.frame(), calls(model, progress, book)));
    }

    private ModelCalls calls(final ChatModel model, final Consumer<JobEvent> progress, final Book book) {
        return calls(model, progress, book, ContextBudget.DEFAULT_WINDOW);
    }

    ModelCalls calls(final ChatModel model, final Consumer<JobEvent> progress, final Book book, final int window) {
        return new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                progress,
                clock,
                book.frame().targetLanguage(),
                window);
    }

    /** The names pass's names step: the review's verdicts committed, with no rendering asked yet (15i.N8). */
    Result<TermReview.Reviewed> decide(final String projectId, final Book book, final ModelCalls calls) {
        return termReview.decide(projectId, book.story(), book.frame(), book.names(), book.dial(), calls);
    }

    /** The names pass's translate step: a rendering for each entry, knowing the held glossary and the readings. */
    Result<List<SuggestionReplies.Suggestion>> suggestFor(
            final List<GlossaryEntry> entries, final TargetContext context, final Book book, final ModelCalls calls) {
        return suggestTargets.suggest(entries, book.story(), book.frame(), book.names(), calls, context);
    }

    Result<Book> book(final String projectId) {
        final Result<Optional<Project>> found = projects.find(projectId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final BookBrief brief = Objects.requireNonNull(found.data(), "found")
                .map(Project::brief)
                .orElse(null);
        final String target = brief == null ? null : brief.targetLanguage();
        if (brief == null || target == null || target.isBlank()) {
            log.warn("Glossary model action project={} refused: no target language chosen", projectId);
            return Result.err(AppError.of(
                    ErrorCode.validation, "Choose a target language", "Choose a target language before the scan."));
        }
        final Document document = openProjects.get(projectId);
        final BookIndex index = openProjects.index(projectId);
        if (document == null || index == null) {
            log.warn("Glossary model action project={} refused: no book is open", projectId);
            return Result.err(
                    AppError.of(ErrorCode.validation, "No book is open", "Open the book before scanning for names."));
        }
        final CallFrame frame = CallFrame.forBook(brief, document, index, requestDefaultsSource.current());
        log.debug(
                "Glossary model action project={} source={} analysis={} target={}",
                projectId,
                frame.sourceLanguage(),
                frame.analysisLanguage(),
                target);
        return Result.ok(new Book(BookSlice.story(index), frame, brief.names(), brief.dial()));
    }
}
